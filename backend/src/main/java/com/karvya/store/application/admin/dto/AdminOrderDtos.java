package com.karvya.store.application.admin.dto;

import com.karvya.store.application.order.dto.OrderDtos;
import com.karvya.store.domain.model.CustomerOrder;
import com.karvya.store.domain.model.OfflinePayment;
import com.karvya.store.domain.model.OrderStatus;
import com.karvya.store.domain.model.PaymentStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class AdminOrderDtos {

    private AdminOrderDtos() {
    }

    /** One row in the admin order list. */
    public record Row(
            Long id,
            String orderNumber,
            OrderStatus status,
            PaymentStatus paymentStatus,
            String customerName,
            String customerPhone,
            String customerEmail,
            boolean registeredCustomer,
            String currency,
            BigDecimal total,
            int itemCount,
            Instant placedAt
    ) {
    }

    /**
     * The full administrative view.
     *
     * <p>Unlike the customer's view this does include internal notes and the
     * recorded payments, and it lists which transitions are currently legal so
     * the interface can offer only those rather than guessing.
     */
    public record Detail(
            OrderDtos.OrderDetail order,
            String internalNotes,
            boolean registeredCustomer,
            String customerAccountEmail,
            Instant stockRestoredAt,
            List<Payment> payments,
            List<OrderStatus> allowedStatuses,
            List<PaymentStatus> allowedPaymentStatuses
    ) {
    }

    public record Payment(
            Long id,
            String methodCode,
            String reference,
            BigDecimal amount,
            LocalDate receivedOn,
            String note,
            String recordedBy,
            Instant createdAt
    ) {
        public static Payment from(OfflinePayment payment) {
            return new Payment(
                    payment.getId(), payment.getMethodCode(), payment.getReference(),
                    payment.getAmount(), payment.getReceivedOn(), payment.getNote(),
                    payment.getRecordedBy(), payment.getCreatedAt());
        }
    }

    public record StatusChange(
            @NotNull(message = "Choose a status")
            OrderStatus status,
            @Size(max = 1000)
            String note
    ) {
    }

    public record PaymentStatusChange(
            @NotNull(message = "Choose a payment status")
            PaymentStatus paymentStatus,
            @Size(max = 1000)
            String note
    ) {
    }

    public record RecordPayment(
            @NotNull(message = "Choose the method used")
            @Size(max = 48)
            String methodCode,

            @Size(max = 160)
            String reference,

            @NotNull(message = "Enter the amount received")
            @DecimalMin(value = "0.01", message = "The amount must be more than zero")
            BigDecimal amount,

            @NotNull(message = "Enter the date it was received")
            LocalDate receivedOn,

            @Size(max = 1000)
            String note,

            /**
             * Whether to move payment status to paid at the same time. Separate
             * because a partial payment is recorded without settling the order.
             */
            boolean markAsPaid
    ) {
    }

    public record InternalNote(
            @Size(max = 2000, message = "That note is too long")
            String note
    ) {
    }

    /** Filters for the admin order list. All optional. */
    public record Filter(
            String q,
            OrderStatus status,
            PaymentStatus paymentStatus,
            LocalDate placedFrom,
            LocalDate placedTo,
            int page,
            int size
    ) {
        public static final int MAX_PAGE_SIZE = 100;

        public Filter {
            q = (q == null || q.isBlank()) ? null : q.trim();
            page = Math.max(0, page);
            size = size <= 0 ? 20 : Math.min(size, MAX_PAGE_SIZE);
        }
    }

    public static Row toRow(CustomerOrder order) {
        return new Row(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getPaymentStatus(),
                order.getDeliveryName(),
                order.getDeliveryPhone(),
                order.getDeliveryEmail(),
                order.getUser() != null,
                order.getCurrency(),
                order.getTotal(),
                order.getItems().stream().mapToInt(item -> item.getQuantity()).sum(),
                order.getPlacedAt());
    }
}
