package com.karvya.store.web;

import com.karvya.store.application.common.PageResponse;
import com.karvya.store.application.order.OrderQueryService;
import com.karvya.store.application.order.PlaceOrderService;
import com.karvya.store.application.order.dto.CheckoutRequest;
import com.karvya.store.application.order.dto.OrderDtos;
import com.karvya.store.infrastructure.security.AppUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * Placing and reading orders.
 *
 * <p>Checkout is open to anyone: guest ordering is a first-class path, not a
 * degraded one. When the caller happens to be signed in, the order is linked
 * to their account and their saved cart is emptied.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Orders", description = "Checkout, order confirmation and order history")
public class OrderController {

    private final PlaceOrderService placeOrder;
    private final OrderQueryService orderQueries;

    public OrderController(PlaceOrderService placeOrder, OrderQueryService orderQueries) {
        this.placeOrder = placeOrder;
        this.orderQueries = orderQueries;
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Place an order")
    public OrderDtos.PlacedOrder checkout(@Valid @RequestBody CheckoutRequest request) {
        return placeOrder.place(request, currentUserIdOrNull());
    }

    /**
     * The confirmation page, for guests.
     *
     * <p>Requires the opaque token issued at checkout. The order number on its
     * own is not a credential - it travels in emails and gets read out over the
     * phone.
     */
    @GetMapping("/orders/{orderNumber}")
    @Operation(summary = "View an order using its confirmation token")
    public OrderDtos.OrderDetail confirmation(@PathVariable String orderNumber,
                                              @RequestParam(required = false) String token) {
        return orderQueries.findByNumberAndToken(orderNumber, token);
    }

    @GetMapping("/account/orders")
    @Operation(summary = "Your order history")
    public PageResponse<OrderDtos.OrderSummary> myOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return orderQueries.findOwnOrders(CurrentUserArgument.requireUserId(), page, size);
    }

    @GetMapping("/account/orders/{orderNumber}")
    @Operation(summary = "One of your own orders")
    public OrderDtos.OrderDetail myOrder(@PathVariable String orderNumber) {
        return orderQueries.findOwnOrder(orderNumber, CurrentUserArgument.requireUserId());
    }

    /**
     * The signed-in account id, or null for a guest. Read from the security
     * context rather than the request, so it cannot be supplied by the caller.
     */
    private Long currentUserIdOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            return principal.getId();
        }
        return null;
    }
}
