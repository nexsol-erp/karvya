package com.karvya.store.application.cart;

import com.karvya.store.application.cart.dto.CartDtos;
import com.karvya.store.application.catalog.dto.ImageRef;
import com.karvya.store.application.settings.SettingsService;
import com.karvya.store.domain.model.Product;
import com.karvya.store.domain.model.ProductStatus;
import com.karvya.store.domain.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Prices a set of product ids and quantities against the live catalogue.
 *
 * <p>The single place where a cart total is computed, used by the guest
 * validation endpoint, the signed-in cart, and checkout. The browser sends
 * identifiers and quantities and nothing else - unit prices, the delivery
 * charge and the total are all derived here, so a tampered request cannot
 * change what anything costs.
 *
 * <p>Where the request cannot be honoured exactly, the cart is corrected and
 * the correction is reported as an {@link CartDtos.Adjustment} rather than
 * applied silently.
 */
@Service
public class CartPricingService {

    private static final BigDecimal DEFAULT_DELIVERY_CHARGE = BigDecimal.ZERO;
    private static final String DEFAULT_CURRENCY = "INR";

    private final ProductRepository products;
    private final SettingsService settings;

    public CartPricingService(ProductRepository products, SettingsService settings) {
        this.products = products;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public CartDtos.CartView price(List<CartDtos.LineRequest> requested) {
        List<CartDtos.LineRequest> merged = mergeDuplicates(requested);

        if (merged.isEmpty()) {
            return empty();
        }

        Map<Long, Product> byId = products
                .findByIdIn(merged.stream().map(CartDtos.LineRequest::productId).toList())
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<CartDtos.CartLine> lines = new ArrayList<>();
        List<CartDtos.Adjustment> adjustments = new ArrayList<>();

        for (CartDtos.LineRequest line : merged) {
            Product product = byId.get(line.productId());

            if (product == null || product.getStatus() != ProductStatus.ACTIVE) {
                adjustments.add(new CartDtos.Adjustment(
                        line.productId(),
                        product == null ? "This item" : product.getName(),
                        CartDtos.Adjustment.Kind.REMOVED_UNAVAILABLE,
                        (product == null ? "An item" : product.getName())
                                + " is no longer available and has been removed."));
                continue;
            }

            if (product.getStockQuantity() <= 0) {
                adjustments.add(new CartDtos.Adjustment(
                        product.getId(), product.getName(),
                        CartDtos.Adjustment.Kind.REMOVED_OUT_OF_STOCK,
                        product.getName() + " is out of stock and has been removed."));
                continue;
            }

            int quantity = Math.min(line.quantity(), product.getStockQuantity());
            if (quantity < line.quantity()) {
                adjustments.add(new CartDtos.Adjustment(
                        product.getId(), product.getName(),
                        CartDtos.Adjustment.Kind.QUANTITY_REDUCED,
                        "Only " + quantity + " of " + product.getName() + " remain, so the quantity was reduced."));
            }

            BigDecimal unitPrice = product.getPrice();
            lines.add(new CartDtos.CartLine(
                    product.getId(),
                    product.getSku(),
                    product.getSlug(),
                    product.getName(),
                    unitPrice,
                    quantity,
                    unitPrice.multiply(BigDecimal.valueOf(quantity)),
                    product.getStockQuantity(),
                    product.primaryImage().map(ImageRef::from).orElse(null)));
        }

        return assemble(lines, adjustments);
    }

    /**
     * Collapses repeated product ids into one line by summing quantities.
     * A merged guest cart, or a double submit, can otherwise present the same
     * product twice and each copy would be stock-checked independently.
     */
    private List<CartDtos.LineRequest> mergeDuplicates(List<CartDtos.LineRequest> requested) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> totals = new LinkedHashMap<>();
        for (CartDtos.LineRequest line : requested) {
            if (line == null || line.productId() == null || line.quantity() <= 0) {
                continue;
            }
            totals.merge(line.productId(), line.quantity(), Integer::sum);
        }
        return totals.entrySet().stream()
                .map(entry -> new CartDtos.LineRequest(entry.getKey(), entry.getValue()))
                .toList();
    }

    private CartDtos.CartView assemble(List<CartDtos.CartLine> lines, List<CartDtos.Adjustment> adjustments) {
        BigDecimal subtotal = lines.stream()
                .map(CartDtos.CartLine::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal charge = settings.getMoney(SettingsService.DELIVERY_CHARGE, DEFAULT_DELIVERY_CHARGE);
        Optional<BigDecimal> threshold = settings.getOptionalMoney(SettingsService.FREE_DELIVERY_THRESHOLD);

        boolean qualifiesForFree = threshold
                .map(limit -> subtotal.compareTo(limit) >= 0)
                .orElse(false);

        BigDecimal deliveryCharge = (lines.isEmpty() || qualifiesForFree) ? BigDecimal.ZERO : charge;

        // how much more would earn free delivery, or null when the rule is off
        BigDecimal toFreeDelivery = threshold
                .filter(limit -> !qualifiesForFree && !lines.isEmpty())
                .map(limit -> limit.subtract(subtotal))
                .filter(remaining -> remaining.signum() > 0)
                .orElse(null);

        int itemCount = lines.stream().mapToInt(CartDtos.CartLine::quantity).sum();

        return new CartDtos.CartView(
                lines,
                adjustments,
                itemCount,
                subtotal,
                deliveryCharge,
                subtotal.add(deliveryCharge),
                settings.getString(SettingsService.CURRENCY, DEFAULT_CURRENCY),
                threshold.orElse(null),
                toFreeDelivery);
    }

    private CartDtos.CartView empty() {
        return assemble(List.of(), List.of());
    }
}
