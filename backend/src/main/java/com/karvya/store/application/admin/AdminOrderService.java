package com.karvya.store.application.admin;

import com.karvya.store.application.admin.dto.AdminOrderDtos;
import com.karvya.store.application.common.PageResponse;
import com.karvya.store.application.order.OrderViewMapper;
import com.karvya.store.domain.ConflictException;
import com.karvya.store.domain.NotFoundException;
import com.karvya.store.domain.model.*;
import com.karvya.store.domain.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Order management for administrators.
 *
 * <p>Every mutation loads the order under a write lock. That is what makes the
 * dangerous operation - cancelling, which returns stock - safe against two
 * administrators clicking at the same moment, or one of them double-clicking.
 */
@Service
public class AdminOrderService {

    private static final Logger log = LoggerFactory.getLogger(AdminOrderService.class);

    private final CustomerOrderRepository orders;
    private final ProductRepository products;
    private final OfflinePaymentRepository payments;
    private final PaymentMethodRepository paymentMethods;
    private final OrderViewMapper viewMapper;

    public AdminOrderService(CustomerOrderRepository orders, ProductRepository products,
                             OfflinePaymentRepository payments, PaymentMethodRepository paymentMethods,
                             OrderViewMapper viewMapper) {
        this.orders = orders;
        this.products = products;
        this.payments = payments;
        this.paymentMethods = paymentMethods;
        this.viewMapper = viewMapper;
    }

    // ---- reading ----------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<AdminOrderDtos.Row> search(AdminOrderDtos.Filter filter) {
        Specification<CustomerOrder> spec = OrderSpecifications.any();

        if (filter.status() != null) {
            spec = spec.and(OrderSpecifications.hasStatus(filter.status()));
        }
        if (filter.paymentStatus() != null) {
            spec = spec.and(OrderSpecifications.hasPaymentStatus(filter.paymentStatus()));
        }
        if (filter.q() != null) {
            spec = spec.and(OrderSpecifications.matches(filter.q()));
        }
        if (filter.placedFrom() != null) {
            spec = spec.and(OrderSpecifications.placedOnOrAfter(filter.placedFrom()));
        }
        if (filter.placedTo() != null) {
            spec = spec.and(OrderSpecifications.placedOnOrBefore(filter.placedTo()));
        }

        var pageable = PageRequest.of(filter.page(), filter.size(),
                Sort.by(Sort.Order.desc("placedAt"), Sort.Order.desc("id")));

        return PageResponse.from(orders.findAll(spec, pageable), AdminOrderDtos::toRow);
    }

    /**
     * Renders matching orders as CSV.
     *
     * <p>Built here, inside the transaction, rather than streamed from the
     * controller. A StreamingResponseBody runs after the request returns and so
     * after the persistence context is gone, at which point touching an order's
     * lazily-loaded lines throws and the download silently truncates to its
     * header row. Holding a capped export in memory is the cheaper mistake.
     */
    @Transactional(readOnly = true)
    public String exportCsv(Specification<CustomerOrder> spec, int maxRows) {
        var pageable = PageRequest.of(0, maxRows,
                Sort.by(Sort.Order.desc("placedAt"), Sort.Order.desc("id")));

        List<CustomerOrder> matching = orders.findAll(spec, pageable).getContent();
        // touch the lines while the session is still open
        matching.forEach(order -> order.getItems().size());

        StringWriter out = new StringWriter();
        try {
            new OrderCsvExporter().write(out, matching);
        } catch (IOException e) {
            // a StringWriter cannot actually fail; this keeps the signature clean
            throw new IllegalStateException("Could not render the CSV export", e);
        }
        return out.toString();
    }

    @Transactional(readOnly = true)
    public AdminOrderDtos.Detail findDetail(String orderNumber) {
        CustomerOrder order = require(orderNumber);
        return toDetail(order);
    }

    // ---- transitions ------------------------------------------------------

    /**
     * Moves an order along, returning stock when it is cancelled.
     *
     * <p>The order is locked first, so the read of {@code stockRestoredAt} and
     * the write that claims it cannot interleave with another request doing the
     * same thing.
     */
    @Transactional
    public AdminOrderDtos.Detail updateStatus(String orderNumber, OrderStatus next,
                                              String note, String actor) {
        CustomerOrder order = orders.lockByOrderNumber(orderNumber)
                .orElseThrow(() -> new NotFoundException("Order", orderNumber));

        order.transitionTo(next, note, actor);
        order.setUpdatedBy(actor);

        if (next == OrderStatus.CANCELLED) {
            restoreStockOnce(order, actor);
        }

        return toDetail(order);
    }

    /**
     * Returns the reserved stock, at most once per order.
     *
     * <p>{@code claimStockRestoration} both checks and sets the marker while
     * the row is locked, so a second cancellation - a retry, a double click,
     * two administrators - finds it already claimed and touches nothing.
     * Without this an order cancelled twice would credit its stock twice and
     * the catalogue would start selling things that do not exist.
     */
    private void restoreStockOnce(CustomerOrder order, String actor) {
        if (!order.claimStockRestoration()) {
            log.info("Order {} was already restocked; cancellation is a no-op for stock",
                    order.getOrderNumber());
            return;
        }

        Map<Long, Integer> toReturn = order.getItems().stream()
                .filter(item -> item.getProduct() != null)
                .collect(Collectors.toMap(
                        item -> item.getProduct().getId(),
                        OrderItem::getQuantity,
                        Integer::sum));

        if (toReturn.isEmpty()) {
            return;
        }

        // same ascending-id discipline as checkout, so a cancellation and a
        // purchase touching the same products cannot deadlock each other
        List<Long> ids = new ArrayList<>(toReturn.keySet());
        Collections.sort(ids);

        products.lockAllByIdInOrder(ids).forEach(product ->
                product.restoreStock(toReturn.get(product.getId())));

        log.info("Order {} cancelled by {}; returned {} product lines to stock",
                order.getOrderNumber(), actor, toReturn.size());
    }

    @Transactional
    public AdminOrderDtos.Detail updatePaymentStatus(String orderNumber, PaymentStatus next,
                                                     String note, String actor) {
        CustomerOrder order = orders.lockByOrderNumber(orderNumber)
                .orElseThrow(() -> new NotFoundException("Order", orderNumber));

        order.transitionPaymentTo(next, note, actor);
        order.setUpdatedBy(actor);
        return toDetail(order);
    }

    // ---- payments and notes -----------------------------------------------

    /**
     * Records money received. Marking the order paid is a separate flag,
     * because a part payment is worth recording without settling the order.
     */
    @Transactional
    public AdminOrderDtos.Detail recordPayment(String orderNumber,
                                               AdminOrderDtos.RecordPayment request, String actor) {
        CustomerOrder order = orders.lockByOrderNumber(orderNumber)
                .orElseThrow(() -> new NotFoundException("Order", orderNumber));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ConflictException("order-cancelled",
                    "Payment cannot be recorded against a cancelled order.");
        }

        paymentMethods.findByCode(request.methodCode())
                .orElseThrow(() -> new ConflictException("unknown-payment-method",
                        "That payment method does not exist."));

        payments.save(OfflinePayment.record(
                order, request.methodCode(), blankToNull(request.reference()),
                request.amount(), request.receivedOn(), blankToNull(request.note()), actor));

        if (request.markAsPaid() && order.getPaymentStatus() != PaymentStatus.PAID_OFFLINE) {
            order.transitionPaymentTo(PaymentStatus.PAID_OFFLINE,
                    "Payment recorded: " + request.amount(), actor);
        }

        order.setUpdatedBy(actor);
        return toDetail(order);
    }

    @Transactional
    public AdminOrderDtos.Detail addInternalNote(String orderNumber, String note, String actor) {
        if (note == null || note.isBlank()) {
            throw new ConflictException("empty-note", "The note is empty.");
        }
        CustomerOrder order = require(orderNumber);
        order.appendInternalNote(note.trim(), actor);
        order.setUpdatedBy(actor);
        return toDetail(order);
    }

    // ---- plumbing ---------------------------------------------------------

    private CustomerOrder require(String orderNumber) {
        return orders.findWithDetailByOrderNumber(orderNumber)
                .orElseThrow(() -> new NotFoundException("Order", orderNumber));
    }

    private AdminOrderDtos.Detail toDetail(CustomerOrder order) {
        return new AdminOrderDtos.Detail(
                viewMapper.toDetail(order),
                order.getInternalNotes(),
                order.getUser() != null,
                order.getUser() == null ? null : order.getUser().getEmail(),
                order.getStockRestoredAt(),
                payments.findByOrderIdOrderByReceivedOnAscIdAsc(order.getId())
                        .stream().map(AdminOrderDtos.Payment::from).toList(),
                List.copyOf(order.getStatus().allowedNext()),
                List.copyOf(order.getPaymentStatus().allowedNext()),
                supplyFor(order));
    }

    /**
     * Who to order each line from.
     *
     * <p>A line whose product has since been deleted still appears, marked, so
     * the order reads completely - an empty row would look like a rendering
     * fault rather than a deleted product.
     */
    private List<AdminOrderDtos.Supply> supplyFor(CustomerOrder order) {
        return order.getItems().stream().map(item -> {
            Product product = item.getProduct();
            Vendor vendor = product == null ? null : product.getVendor();

            return new AdminOrderDtos.Supply(
                    item.getProductSku(),
                    item.getProductName(),
                    item.getQuantity(),
                    vendor == null ? null : vendor.getName(),
                    vendor == null ? null : vendor.getContactName(),
                    vendor == null ? null : vendor.getEmail(),
                    vendor == null ? null : vendor.getPhone(),
                    vendor == null ? null : vendor.getAddress(),
                    // the product may quote its own lead time when it differs
                    // from whatever the supplier usually says
                    product != null && product.getVendorDeliveryTime() != null
                            ? product.getVendorDeliveryTime()
                            : (vendor == null ? null : vendor.getDeliveryTime()),
                    vendor == null ? null : vendor.getConditions(),
                    product == null ? null : product.getVendorPrice(),
                    product == null);
        }).toList();
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
