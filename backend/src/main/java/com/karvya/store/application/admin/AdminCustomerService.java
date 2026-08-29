package com.karvya.store.application.admin;

import com.karvya.store.application.common.PageResponse;
import com.karvya.store.application.identity.PasswordResetService;
import com.karvya.store.application.order.dto.OrderDtos;
import com.karvya.store.domain.ConflictException;
import com.karvya.store.domain.NotFoundException;
import com.karvya.store.domain.model.AppUser;
import com.karvya.store.domain.model.Role;
import com.karvya.store.domain.repository.AppUserRepository;
import com.karvya.store.domain.repository.CustomerOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Customer accounts, as an administrator may see them.
 *
 * <p>What is deliberately absent matters as much as what is here. There is no
 * way to read a password hash, no way to set someone's password, and no way to
 * see a reset token. An administrator who needs to help a locked-out customer
 * sends them a reset link; they never learn or choose the credential.
 */
@Service
public class AdminCustomerService {

    private static final Logger log = LoggerFactory.getLogger(AdminCustomerService.class);

    public record CustomerRow(
            Long id,
            String email,
            String fullName,
            String phone,
            boolean enabled,
            boolean locked,
            Instant lastLoginAt,
            Instant memberSince
    ) {
        static CustomerRow from(AppUser user) {
            return new CustomerRow(user.getId(), user.getEmail(), user.getFullName(),
                    user.getPhone(), user.isEnabled(), user.isLocked(),
                    user.getLastLoginAt(), user.getCreatedAt());
        }
    }

    public record CustomerDetail(
            CustomerRow customer,
            List<String> roles,
            long orderCount,
            List<OrderDtos.OrderSummary> recentOrders
    ) {
    }

    private static final int RECENT_ORDERS = 10;

    private final AppUserRepository users;
    private final CustomerOrderRepository orders;
    private final PasswordResetService passwordResets;

    public AdminCustomerService(AppUserRepository users, CustomerOrderRepository orders,
                                PasswordResetService passwordResets) {
        this.users = users;
        this.orders = orders;
        this.passwordResets = passwordResets;
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerRow> search(String q, int page, int size) {
        var pageable = PageRequest.of(Math.max(0, page), size <= 0 ? 20 : Math.min(size, 100),
                Sort.by(Sort.Order.desc("createdAt")));

        var results = (q == null || q.isBlank())
                ? users.findCustomers(Role.CUSTOMER, pageable)
                : users.searchCustomers(Role.CUSTOMER, q.trim(), pageable);

        return PageResponse.from(results, CustomerRow::from);
    }

    @Transactional(readOnly = true)
    public CustomerDetail find(Long id) {
        AppUser user = require(id);

        var recent = orders.findByUserIdOrderByPlacedAtDesc(id, PageRequest.of(0, RECENT_ORDERS));

        return new CustomerDetail(
                CustomerRow.from(user),
                user.getRoles().stream().map(Role::getCode).sorted().toList(),
                recent.getTotalElements(),
                recent.getContent().stream().map(OrderDtos.OrderSummary::from).toList());
    }

    /**
     * Disables or re-enables an account.
     *
     * <p>Disabling keeps every order intact - the history belongs to the
     * business as much as to the customer - and only stops them signing in.
     * Sessions are database-backed, so it takes effect on their next request.
     */
    @Transactional
    public CustomerRow setEnabled(Long id, boolean enabled, String actor) {
        AppUser user = require(id);

        if (user.hasRole(Role.ADMIN)) {
            throw new ConflictException("cannot-disable-administrator",
                    "Administrator accounts are managed separately.");
        }

        user.setEnabled(enabled);
        user.setUpdatedBy(actor);
        log.info("{} {} customer account {}", actor, enabled ? "enabled" : "disabled", id);
        return CustomerRow.from(user);
    }

    /**
     * Sends the customer a reset link.
     *
     * <p>Reuses the same flow the customer would start themselves, so an
     * administrator never sees the token and there is only one code path to
     * keep correct.
     */
    @Transactional
    public void sendPasswordReset(Long id, String actor) {
        AppUser user = require(id);
        if (!user.isEnabled()) {
            throw new ConflictException("account-disabled",
                    "Re-enable the account before sending a reset link.");
        }
        passwordResets.requestReset(user.getEmail(), "admin:" + actor);
        log.info("{} triggered a password reset for customer {}", actor, id);
    }

    private AppUser require(Long id) {
        return users.findById(id)
                .orElseThrow(() -> new NotFoundException("Customer", String.valueOf(id)));
    }
}
