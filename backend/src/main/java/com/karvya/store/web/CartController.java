package com.karvya.store.web;

import com.karvya.store.application.cart.CartPricingService;
import com.karvya.store.application.cart.CartService;
import com.karvya.store.application.cart.dto.CartDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Carts, for visitors and for signed-in customers.
 *
 * <p>Both paths return the same {@code CartView}, priced by the same service,
 * so the storefront renders one component either way and a visitor who signs
 * in mid-shop sees no change in how totals are computed.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Cart", description = "Visitor cart validation and the signed-in customer cart")
public class CartController {

    private final CartPricingService pricing;
    private final CartService carts;

    public CartController(CartPricingService pricing, CartService carts) {
        this.pricing = pricing;
        this.carts = carts;
    }

    /**
     * Re-prices a visitor's browser cart.
     *
     * <p>Open to anyone and stores nothing. The browser keeps the cart; this
     * says what it actually costs and what had to change - a product withdrawn,
     * a quantity no longer in stock - before the customer reaches checkout.
     */
    @PostMapping("/cart/validate")
    @Operation(summary = "Price a visitor cart and report any corrections")
    public CartDtos.CartView validate(@Valid @RequestBody CartDtos.CartRequest request) {
        return pricing.price(request.items());
    }

    @GetMapping("/account/cart")
    @Operation(summary = "Your saved cart")
    public CartDtos.CartView myCart() {
        return carts.view(CurrentUserArgument.requireUserId());
    }

    /** Sets an absolute quantity rather than incrementing, so a retried request is harmless. */
    @PutMapping("/account/cart/items/{productId}")
    @Operation(summary = "Set the quantity of one product in your cart")
    public CartDtos.CartView setItem(
            @PathVariable Long productId,
            @RequestParam @Min(0) @Max(99) int quantity) {
        return carts.setItem(CurrentUserArgument.requireUserId(), productId, quantity);
    }

    @DeleteMapping("/account/cart/items/{productId}")
    @Operation(summary = "Remove one product from your cart")
    public CartDtos.CartView removeItem(@PathVariable Long productId) {
        return carts.removeItem(CurrentUserArgument.requireUserId(), productId);
    }

    @DeleteMapping("/account/cart")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Empty your cart")
    public CartDtos.CartView clear() {
        return carts.clear(CurrentUserArgument.requireUserId());
    }

    /**
     * Folds the browser cart into the account cart after signing in.
     *
     * <p>Called by the storefront immediately after login. Quantities for the
     * same product are summed and then capped at available stock.
     */
    @PostMapping("/account/cart/merge")
    @Operation(summary = "Merge a visitor cart into your saved cart")
    public CartDtos.CartView merge(@Valid @RequestBody CartDtos.CartRequest request) {
        return carts.merge(CurrentUserArgument.requireUserId(), request.items());
    }
}
