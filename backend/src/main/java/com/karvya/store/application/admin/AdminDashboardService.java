package com.karvya.store.application.admin;

import com.karvya.store.application.admin.dto.AdminOrderDtos;
import com.karvya.store.application.enquiry.dto.EnquiryDtos;
import com.karvya.store.application.settings.SettingsService;
import com.karvya.store.domain.model.*;
import com.karvya.store.domain.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * What an administrator needs to see on opening the back office.
 *
 * <p>The order-value figure is always accompanied by the window it covers.
 * A bare "total order value" invites the reader to assume it means revenue,
 * which it does not - cancelled orders are excluded, but offline payment means
 * a confirmed order is still only an expectation of money.
 */
@Service
public class AdminDashboardService {

    public record LowStockItem(Long id, String sku, String name, int stockQuantity, int threshold) {
    }

    public record Dashboard(
            Map<String, Long> ordersByStatus,
            Map<String, Long> ordersByPaymentStatus,
            long ordersNeedingAttention,
            String orderValueWindow,
            BigDecimal orderValueInWindow,
            long ordersInWindow,
            String currency,
            List<AdminOrderDtos.Row> recentOrders,
            List<LowStockItem> lowStock,
            long pendingNotifications,
            long failedNotifications,
            long newEnquiries,
            List<EnquiryDtos.View> recentEnquiries
    ) {
    }

    private static final int RECENT_ORDER_COUNT = 8;
    private static final int WINDOW_DAYS = 30;

    private final CustomerOrderRepository orders;
    private final ProductRepository products;
    private final EmailNotificationRepository notifications;
    private final ContactEnquiryRepository enquiries;
    private final SettingsService settings;

    public AdminDashboardService(CustomerOrderRepository orders, ProductRepository products,
                                 EmailNotificationRepository notifications, ContactEnquiryRepository enquiries,
                                 SettingsService settings) {
        this.orders = orders;
        this.products = products;
        this.notifications = notifications;
        this.enquiries = enquiries;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public Dashboard build() {
        Instant windowStart = LocalDate.now(ZoneOffset.UTC)
                .minusDays(WINDOW_DAYS)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();

        Map<String, Long> byStatus = new java.util.LinkedHashMap<>();
        for (OrderStatus status : OrderStatus.values()) {
            byStatus.put(status.name(), orders.countByStatus(status));
        }

        Map<String, Long> byPaymentStatus = new java.util.LinkedHashMap<>();
        for (PaymentStatus status : PaymentStatus.values()) {
            byPaymentStatus.put(status.name(), orders.countByPaymentStatus(status));
        }

        // what a person actually acts on first thing in the morning
        long needingAttention = byStatus.getOrDefault(OrderStatus.NEW.name(), 0L);

        List<AdminOrderDtos.Row> recent = orders
                .findAll(PageRequest.of(0, RECENT_ORDER_COUNT,
                        Sort.by(Sort.Order.desc("placedAt"), Sort.Order.desc("id"))))
                .map(AdminOrderDtos::toRow)
                .getContent();

        int fallbackThreshold = settings.getInt(SettingsService.LOW_STOCK_THRESHOLD, 3);
        List<LowStockItem> lowStock = products.findLowStock(ProductStatus.ACTIVE).stream()
                .map(product -> new LowStockItem(
                        product.getId(), product.getSku(), product.getName(),
                        product.getStockQuantity(),
                        product.getLowStockThreshold() > 0
                                ? product.getLowStockThreshold()
                                : fallbackThreshold))
                .toList();

        return new Dashboard(
                byStatus,
                byPaymentStatus,
                needingAttention,
                "Last " + WINDOW_DAYS + " days",
                orders.totalValueSince(windowStart, OrderStatus.CANCELLED),
                orders.countPlacedSince(windowStart, OrderStatus.CANCELLED),
                settings.getString(SettingsService.CURRENCY, "INR"),
                recent,
                lowStock,
                notifications.countByStatus(NotificationStatus.PENDING),
                notifications.countByStatus(NotificationStatus.FAILED),
                enquiries.countByStatus(EnquiryStatus.NEW),
                enquiries.findTop5ByOrderByCreatedAtDesc().stream().map(EnquiryDtos.View::from).toList());
    }
}
