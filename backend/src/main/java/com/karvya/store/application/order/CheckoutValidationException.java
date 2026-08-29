package com.karvya.store.application.order;

import com.karvya.store.application.cart.dto.CartDtos;
import com.karvya.store.domain.DomainException;

import java.util.List;

/**
 * The cart could not be turned into an order as submitted.
 *
 * <p>Carries the specific corrections rather than a bare message, so the
 * checkout page can show exactly which piece sold out or which quantity is no
 * longer available instead of a generic failure. The customer keeps their
 * cart and their typed address.
 */
public class CheckoutValidationException extends DomainException {

    private final transient List<CartDtos.Adjustment> adjustments;

    public CheckoutValidationException(String message, List<CartDtos.Adjustment> adjustments) {
        super(message);
        this.adjustments = List.copyOf(adjustments);
    }

    public List<CartDtos.Adjustment> adjustments() {
        return adjustments;
    }

    @Override
    public String code() {
        return "checkout-cart-changed";
    }
}
