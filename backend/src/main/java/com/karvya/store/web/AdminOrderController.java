package com.karvya.store.web;

import com.karvya.store.application.admin.AdminDashboardService;
import com.karvya.store.application.admin.AdminOrderService;
import com.karvya.store.application.admin.dto.AdminOrderDtos;
import com.karvya.store.application.common.PageResponse;
import com.karvya.store.domain.model.OrderStatus;
import com.karvya.store.domain.model.PaymentStatus;
import com.karvya.store.domain.repository.OrderSpecifications;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Order management for the back office.
 *
 * <p>Authorised by {@code hasRole('ADMIN')} in the security chain rather than
 * per method, so a new endpoint added here is protected by default instead of
 * being open until somebody remembers to annotate it.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin orders", description = "Order management and the dashboard")
public class AdminOrderController {

    /** How many orders one export may contain, so a click cannot exhaust memory. */
    private static final int MAX_EXPORT_ROWS = 5000;

    private final AdminOrderService adminOrders;
    private final AdminDashboardService dashboard;

    public AdminOrderController(AdminOrderService adminOrders, AdminDashboardService dashboard) {
        this.adminOrders = adminOrders;
        this.dashboard = dashboard;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Summary counts, recent orders, low stock and notification health")
    public AdminDashboardService.Dashboard dashboard() {
        return dashboard.build();
    }

    @GetMapping("/orders")
    @Operation(summary = "Search and filter orders")
    public PageResponse<AdminOrderDtos.Row> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate placedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate placedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return adminOrders.search(new AdminOrderDtos.Filter(
                q, status, paymentStatus, placedFrom, placedTo, page, size));
    }

    @GetMapping("/orders/{orderNumber}")
    @Operation(summary = "Full order detail, including internal notes and payments")
    public AdminOrderDtos.Detail detail(@PathVariable String orderNumber) {
        return adminOrders.findDetail(orderNumber);
    }

    @PatchMapping("/orders/{orderNumber}/status")
    @Operation(summary = "Move the order along; cancelling returns its stock")
    public AdminOrderDtos.Detail updateStatus(@PathVariable String orderNumber,
                                              @Valid @RequestBody AdminOrderDtos.StatusChange request) {
        return adminOrders.updateStatus(orderNumber, request.status(), request.note(), actor());
    }

    @PatchMapping("/orders/{orderNumber}/payment-status")
    @Operation(summary = "Update payment status independently of fulfilment")
    public AdminOrderDtos.Detail updatePaymentStatus(
            @PathVariable String orderNumber,
            @Valid @RequestBody AdminOrderDtos.PaymentStatusChange request) {
        return adminOrders.updatePaymentStatus(
                orderNumber, request.paymentStatus(), request.note(), actor());
    }

    @PostMapping("/orders/{orderNumber}/payments")
    @Operation(summary = "Record an offline payment against the order")
    public AdminOrderDtos.Detail recordPayment(@PathVariable String orderNumber,
                                               @Valid @RequestBody AdminOrderDtos.RecordPayment request) {
        return adminOrders.recordPayment(orderNumber, request, actor());
    }

    @PostMapping("/orders/{orderNumber}/notes")
    @Operation(summary = "Append an internal note the customer never sees")
    public AdminOrderDtos.Detail addNote(@PathVariable String orderNumber,
                                         @Valid @RequestBody AdminOrderDtos.InternalNote request) {
        return adminOrders.addInternalNote(orderNumber, request.note(), actor());
    }

    /**
     * Exports matching orders as CSV.
     *
     * <p>Rendered by the service inside its transaction and returned whole,
     * capped at {@link #MAX_EXPORT_ROWS}. Streaming would be lighter, but a
     * StreamingResponseBody runs after the persistence context has closed and
     * the order lines would fail to load mid-download.
     */
    @GetMapping(value = "/orders/export.csv", produces = "text/csv")
    @Operation(summary = "Export orders as CSV")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate placedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate placedTo,
            @RequestParam(required = false) OrderStatus status) {

        var spec = OrderSpecifications.any();
        if (status != null) {
            spec = spec.and(OrderSpecifications.hasStatus(status));
        }
        if (placedFrom != null) {
            spec = spec.and(OrderSpecifications.placedOnOrAfter(placedFrom));
        }
        if (placedTo != null) {
            spec = spec.and(OrderSpecifications.placedOnOrBefore(placedTo));
        }

        // a byte-order mark, so Excel reads it as UTF-8 rather than mangling
        // any non-ASCII name in the file
        String csv = "﻿" + adminOrders.exportCsv(spec, MAX_EXPORT_ROWS);

        String filename = "karvya-orders-"
                + DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDate.now(ZoneOffset.UTC))
                + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    /** Who is making the change, for the audit trail. */
    private String actor() {
        return CurrentUserArgument.require().getEmail();
    }
}
