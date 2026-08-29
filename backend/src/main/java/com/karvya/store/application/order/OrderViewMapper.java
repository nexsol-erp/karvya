package com.karvya.store.application.order;

import com.karvya.store.application.order.dto.OrderDtos;
import com.karvya.store.domain.model.CustomerOrder;
import com.karvya.store.domain.model.OrderItem;
import com.karvya.store.domain.model.OrderStatusHistory;
import com.karvya.store.domain.model.PaymentMethod;
import com.karvya.store.domain.repository.PaymentMethodRepository;
import org.springframework.stereotype.Component;

/**
 * Builds the customer-facing view of an order.
 *
 * <p>Kept apart from the services that write orders, because exactly one rule
 * governs it and it is easy to break by accident: nothing internal may appear
 * here. Internal notes, the access token hash and the owning account are all
 * omitted, and the payment instructions are the administrator-configured text
 * for the method actually chosen.
 */
@Component
public class OrderViewMapper {

    private final PaymentMethodRepository paymentMethods;

    public OrderViewMapper(PaymentMethodRepository paymentMethods) {
        this.paymentMethods = paymentMethods;
    }

    public OrderDtos.OrderDetail toDetail(CustomerOrder order) {
        PaymentMethod method = paymentMethods.findByCode(order.getPaymentMethodCode()).orElse(null);
        boolean emailQueued = order.getDeliveryEmail() != null && !order.getDeliveryEmail().isBlank();
        return toDetail(order, method, emailQueued);
    }

    public OrderDtos.OrderDetail toDetail(CustomerOrder order, PaymentMethod method,
                                          boolean confirmationEmailQueued) {
        return new OrderDtos.OrderDetail(
                order.getOrderNumber(),
                order.getStatus(),
                order.getPaymentStatus(),
                order.getPaymentMethodCode(),
                // the method may since have been renamed or retired; fall back
                // to the stored code so the order still reads sensibly
                method == null ? order.getPaymentMethodCode() : method.getLabel(),
                method == null ? null : method.getInstructions(),
                order.getCurrency(),
                order.getSubtotal(),
                order.getDeliveryCharge(),
                order.getTotal(),
                OrderDtos.Delivery.from(order),
                order.getCustomerComments(),
                order.getItems().stream().map(OrderDtos.Line::from).toList(),
                order.getHistory().stream().map(OrderDtos.TimelineEntry::from).toList(),
                order.getPlacedAt(),
                confirmationEmailQueued);
    }

    public OrderDtos.OrderSummary toSummary(CustomerOrder order) {
        return OrderDtos.OrderSummary.from(order);
    }

    /** Total units across every line, for the history list. */
    public int itemCount(CustomerOrder order) {
        return order.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
    }

    /** Most recent change first, for a compact timeline. */
    public OrderStatusHistory latestChange(CustomerOrder order) {
        return order.getHistory().isEmpty()
                ? null
                : order.getHistory().get(order.getHistory().size() - 1);
    }
}
