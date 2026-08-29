package com.karvya.store.application.cart;

import com.karvya.store.application.cart.dto.CartDtos;
import com.karvya.store.domain.NotFoundException;
import com.karvya.store.domain.model.AppUser;
import com.karvya.store.domain.model.Cart;
import com.karvya.store.domain.model.CartItem;
import com.karvya.store.domain.model.Product;
import com.karvya.store.domain.model.ProductStatus;
import com.karvya.store.domain.repository.AppUserRepository;
import com.karvya.store.domain.repository.CartRepository;
import com.karvya.store.domain.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The signed-in customer's cart.
 *
 * <p>Stored as quantities and re-priced on every read through
 * {@link CartPricingService}, so a cart left open for a week reflects today's
 * catalogue rather than the prices of the day it was filled.
 */
@Service
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);
    private static final int MAX_QUANTITY_PER_LINE = 99;

    private final CartRepository carts;
    private final ProductRepository products;
    private final AppUserRepository users;
    private final CartPricingService pricing;

    public CartService(CartRepository carts, ProductRepository products,
                       AppUserRepository users, CartPricingService pricing) {
        this.carts = carts;
        this.products = products;
        this.users = users;
        this.pricing = pricing;
    }

    @Transactional(readOnly = true)
    public CartDtos.CartView view(Long userId) {
        return pricing.price(storedLines(userId));
    }

    /** Sets an absolute quantity. Zero removes the line. */
    @Transactional
    public CartDtos.CartView setItem(Long userId, Long productId, int quantity) {
        Cart cart = cartFor(userId);

        if (quantity <= 0) {
            cart.removeProduct(productId);
        } else {
            Product product = products.findById(productId)
                    .filter(p -> p.getStatus() == ProductStatus.ACTIVE)
                    .orElseThrow(() -> new NotFoundException("Product", String.valueOf(productId)));

            // capped here as well as at pricing time, so the stored cart never
            // holds a quantity the catalogue could not satisfy
            int capped = Math.min(Math.min(quantity, MAX_QUANTITY_PER_LINE), product.getStockQuantity());
            cart.setQuantity(product, capped);
        }

        carts.save(cart);
        return pricing.price(toLineRequests(cart));
    }

    @Transactional
    public CartDtos.CartView removeItem(Long userId, Long productId) {
        Cart cart = cartFor(userId);
        cart.removeProduct(productId);
        carts.save(cart);
        return pricing.price(toLineRequests(cart));
    }

    @Transactional
    public CartDtos.CartView clear(Long userId) {
        Cart cart = cartFor(userId);
        cart.clear();
        carts.save(cart);
        return pricing.price(List.of());
    }

    /**
     * Folds a visitor's browser cart into their account cart at sign-in.
     *
     * <p>Quantities for the same product are summed rather than overwritten -
     * someone who added two at their desk and one on their phone means to buy
     * three - and every resulting line is capped at what is actually in stock,
     * so merging can never create an unfulfillable cart.
     *
     * <p>Returns the merged cart so the caller can show what changed.
     */
    @Transactional
    public CartDtos.CartView merge(Long userId, List<CartDtos.LineRequest> guestLines) {
        Cart cart = cartFor(userId);

        if (guestLines == null || guestLines.isEmpty()) {
            return pricing.price(toLineRequests(cart));
        }

        Map<Long, Integer> combined = new LinkedHashMap<>();
        for (CartItem item : cart.getItems()) {
            combined.put(item.getProduct().getId(), item.getQuantity());
        }
        for (CartDtos.LineRequest line : guestLines) {
            if (line == null || line.productId() == null || line.quantity() <= 0) {
                continue;
            }
            combined.merge(line.productId(), line.quantity(), Integer::sum);
        }

        Map<Long, Product> byId = new LinkedHashMap<>();
        products.findByIdIn(combined.keySet()).forEach(p -> byId.put(p.getId(), p));

        // Updated in place rather than cleared and rebuilt. Hibernate flushes
        // inserts before deletes, so re-adding a product that is still pending
        // removal collides with the (cart_id, product_id) unique constraint.
        // Mutating also preserves each line's added_at, and so its position.
        for (Map.Entry<Long, Integer> entry : combined.entrySet()) {
            Product product = byId.get(entry.getKey());

            if (product == null || product.getStatus() != ProductStatus.ACTIVE
                    || product.getStockQuantity() <= 0) {
                // withdrawn or sold out since it was added: drop it silently,
                // the re-priced view reports the removal
                cart.removeProduct(entry.getKey());
                continue;
            }

            int capped = Math.min(Math.min(entry.getValue(), MAX_QUANTITY_PER_LINE),
                    product.getStockQuantity());
            cart.setQuantity(product, capped);
        }

        carts.save(cart);
        log.debug("Merged {} guest lines into the cart for account {}", guestLines.size(), userId);
        return pricing.price(toLineRequests(cart));
    }

    private Cart cartFor(Long userId) {
        return carts.findByUserId(userId).orElseGet(() -> {
            AppUser user = users.findById(userId)
                    .orElseThrow(() -> new NotFoundException("Account", String.valueOf(userId)));
            return carts.save(Cart.forUser(user));
        });
    }

    private List<CartDtos.LineRequest> storedLines(Long userId) {
        return carts.findByUserId(userId)
                .map(this::toLineRequests)
                .orElseGet(List::of);
    }

    private List<CartDtos.LineRequest> toLineRequests(Cart cart) {
        List<CartDtos.LineRequest> lines = new ArrayList<>();
        for (CartItem item : cart.getItems()) {
            lines.add(new CartDtos.LineRequest(item.getProduct().getId(), item.getQuantity()));
        }
        return lines;
    }
}
