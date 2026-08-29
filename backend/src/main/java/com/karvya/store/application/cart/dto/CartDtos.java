package com.karvya.store.application.cart.dto;

import com.karvya.store.application.catalog.dto.ImageRef;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public final class CartDtos {

    private CartDtos() {
    }

    /** What the browser sends: identifiers and quantities, never prices. */
    public record LineRequest(
            @NotNull(message = "A product is required")
            Long productId,

            @Min(value = 1, message = "Quantity must be at least 1")
            @Max(value = 99, message = "Quantity is too large")
            int quantity
    ) {
    }

    public record CartRequest(
            @Valid
            @Size(max = 50, message = "Too many different products in one cart")
            List<LineRequest> items
    ) {
        public List<LineRequest> items() {
            return items == null ? List.of() : items;
        }
    }

    /** A priced line, as the server computed it. */
    public record CartLine(
            Long productId,
            String sku,
            String slug,
            String name,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineTotal,
            int availableStock,
            ImageRef image
    ) {
    }

    /**
     * Something the server changed about the requested cart. Surfaced rather
     * than applied silently, so a customer is never quietly charged for
     * something different from what they chose.
     */
    public record Adjustment(
            Long productId,
            String productName,
            Kind kind,
            String message
    ) {
        public enum Kind {
            /** No longer sold, or withdrawn from the catalogue. */
            REMOVED_UNAVAILABLE,
            /** Out of stock entirely. */
            REMOVED_OUT_OF_STOCK,
            /** Fewer remain than were asked for. */
            QUANTITY_REDUCED
        }
    }

    public record CartView(
            List<CartLine> lines,
            List<Adjustment> adjustments,
            int itemCount,
            BigDecimal subtotal,
            BigDecimal deliveryCharge,
            BigDecimal total,
            String currency,
            BigDecimal freeDeliveryThreshold,
            BigDecimal amountToFreeDelivery
    ) {
        public boolean hasAdjustments() {
            return !adjustments.isEmpty();
        }
    }
}
