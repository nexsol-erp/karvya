package com.karvya.store.web;

import com.karvya.store.domain.model.PaymentMethod;
import com.karvya.store.domain.repository.PaymentMethodRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The offline payment options offered at checkout. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Payment methods", description = "Offline payment options")
public class PaymentMethodController {

    public record PaymentMethodView(String code, String label, String instructions) {
        static PaymentMethodView from(PaymentMethod method) {
            return new PaymentMethodView(method.getCode(), method.getLabel(), method.getInstructions());
        }
    }

    private final PaymentMethodRepository paymentMethods;

    public PaymentMethodController(PaymentMethodRepository paymentMethods) {
        this.paymentMethods = paymentMethods;
    }

    /** Active methods only, so retiring one removes it from checkout at once. */
    @GetMapping("/payment-methods")
    @Operation(summary = "Offline payment methods available at checkout")
    public List<PaymentMethodView> list() {
        return paymentMethods.findByActiveTrueOrderByDisplayOrderAscLabelAsc()
                .stream()
                .map(PaymentMethodView::from)
                .toList();
    }
}
