package com.karvya.store.application.order.dto;

import com.karvya.store.domain.model.CustomerOrder;
import com.karvya.store.domain.model.OrderItem;
import com.karvya.store.domain.model.OrderStatus;
import com.karvya.store.domain.model.OrderStatusHistory;
import com.karvya.store.domain.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record Line(
            String productName,
            String productSku,
            String productSlug,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineTotal,
            String imageKey
    ) {
        public static Line from(OrderItem item) {
            return new Line(
                    item.getProductName(),
                    item.getProductSku(),
                    // null once the product is archived; the line still reads correctly
                    item.getProduct() == null ? null : item.getProduct().getSlug(),
                    item.getUnitPrice(),
                    item.getQuantity(),
                    item.getLineTotal(),
                    item.getImageKey());
        }
    }

    public record Delivery(
            String name,
            String phone,
            String email,
            String line1,
            String line2,
            String city,
            String state,
            String postalCode,
            String notes
    ) {
        public static Delivery from(CustomerOrder order) {
            return new Delivery(
                    order.getDeliveryName(), order.getDeliveryPhone(), order.getDeliveryEmail(),
                    order.getAddressLine1(), order.getAddressLine2(), order.getCity(),
                    order.getState(), order.getPostalCode(), order.getDeliveryNotes());
        }
    }

    public record TimelineEntry(
            String field,
            String from,
            String to,
            String note,
            Instant changedAt
    ) {
        public static TimelineEntry from(OrderStatusHistory entry) {
            return new TimelineEntry(entry.getField(), entry.getFromValue(),
                    entry.getToValue(), entry.getNote(), entry.getChangedAt());
        }
    }

    /**
     * The confirmation, and the customer's own view of a past order.
     *
     * <p>{@code paymentInstructions} is the administrator-configured text for
     * the chosen method, so what the customer is told to do is editable
     * without a deployment. Internal notes are never included.
     */
    public record OrderDetail(
            String orderNumber,
            OrderStatus status,
            PaymentStatus paymentStatus,
            String paymentMethodCode,
            String paymentMethodLabel,
            String paymentInstructions,
            String currency,
            BigDecimal subtotal,
            BigDecimal deliveryCharge,
            BigDecimal total,
            Delivery delivery,
            String customerComments,
            List<Line> lines,
            List<TimelineEntry> timeline,
            Instant placedAt,
            /** True when a confirmation email was queued for this order. */
            boolean confirmationEmailQueued
    ) {
    }

    /** One row in the customer's order history. */
    public record OrderSummary(
            String orderNumber,
            OrderStatus status,
            PaymentStatus paymentStatus,
            String currency,
            BigDecimal total,
            int itemCount,
            String firstItemName,
            String firstItemImageKey,
            Instant placedAt
    ) {
        public static OrderSummary from(CustomerOrder order) {
            List<OrderItem> items = order.getItems();
            OrderItem first = items.isEmpty() ? null : items.get(0);
            return new OrderSummary(
                    order.getOrderNumber(),
                    order.getStatus(),
                    order.getPaymentStatus(),
                    order.getCurrency(),
                    order.getTotal(),
                    items.stream().mapToInt(OrderItem::getQuantity).sum(),
                    first == null ? null : first.getProductName(),
                    first == null ? null : first.getImageKey(),
                    order.getPlacedAt());
        }
    }

    /**
     * What placing an order returns.
     *
     * <p>The access token is handed back exactly once, so a guest can be sent
     * to a confirmation URL that only they hold. Only its hash is stored.
     */
    public record PlacedOrder(
            String orderNumber,
            String accessToken,
            OrderDetail detail
    ) {
    }
}
