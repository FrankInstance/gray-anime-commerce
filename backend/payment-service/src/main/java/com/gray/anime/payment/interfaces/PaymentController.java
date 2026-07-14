package com.gray.anime.payment.interfaces;

import com.gray.anime.common.api.ApiResponse;
import com.gray.anime.common.security.CurrentUser;
import com.gray.anime.payment.application.PaymentApplicationService;
import com.gray.anime.payment.interfaces.dto.CheckoutSessionRequest;
import com.gray.anime.payment.interfaces.dto.CheckoutSessionView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentApplicationService service;

    public PaymentController(PaymentApplicationService service) {
        this.service = service;
    }

    @PostMapping("/checkout-session")
    ApiResponse<CheckoutSessionView> createCheckoutSession(
            @Valid @RequestBody CheckoutSessionRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(service.createCheckoutSession(CurrentUser.from(request), body));
    }
}
