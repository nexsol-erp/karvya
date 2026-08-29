package com.karvya.store.application.order;

import com.karvya.store.application.common.PageResponse;
import com.karvya.store.application.order.dto.OrderDtos;
import com.karvya.store.domain.NotFoundException;
import com.karvya.store.domain.model.CustomerOrder;
import com.karvya.store.domain.repository.CustomerOrderRepository;
import com.karvya.store.infrastructure.security.SecureTokens;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reading orders, for the people entitled to see them.
 *
 * <p>Two routes in, and both prove entitlement before returning anything. A
 * guest presents the opaque token they were given at checkout; a signed-in
 * customer is matched on the owning account in the query itself. The order
 * number alone is never enough - it appears in emails and gets read aloud over
 * the phone, so treating it as a credential would be a mistake.
 */
@Service
public class OrderQueryService {

    private static final int MAX_PAGE_SIZE = 50;

    private final CustomerOrderRepository orders;
    private final OrderViewMapper viewMapper;

    public OrderQueryService(CustomerOrderRepository orders, OrderViewMapper viewMapper) {
        this.orders = orders;
        this.viewMapper = viewMapper;
    }

    /**
     * The guest confirmation view.
     *
     * <p>A wrong token and an unknown order number produce the same "not
     * found", so the endpoint cannot be used to discover which order numbers
     * exist.
     */
    @Transactional(readOnly = true)
    public OrderDtos.OrderDetail findByNumberAndToken(String orderNumber, String token) {
        if (token == null || token.isBlank()) {
            throw new NotFoundException("Order", orderNumber);
        }

        CustomerOrder order = orders.findWithDetailByOrderNumber(orderNumber)
                .filter(candidate -> SecureTokens.matches(token, candidate.getAccessTokenHash()))
                .orElseThrow(() -> new NotFoundException("Order", orderNumber));

        return viewMapper.toDetail(order);
    }

    /** A signed-in customer's own order, matched on owner in the query. */
    @Transactional(readOnly = true)
    public OrderDtos.OrderDetail findOwnOrder(String orderNumber, Long userId) {
        CustomerOrder order = orders.findByOrderNumberAndUserId(orderNumber, userId)
                .orElseThrow(() -> new NotFoundException("Order", orderNumber));
        return viewMapper.toDetail(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderDtos.OrderSummary> findOwnOrders(Long userId, int page, int size) {
        int safeSize = size <= 0 ? 10 : Math.min(size, MAX_PAGE_SIZE);
        return PageResponse.from(
                orders.findByUserIdOrderByPlacedAtDesc(userId, PageRequest.of(Math.max(0, page), safeSize)),
                viewMapper::toSummary);
    }
}
