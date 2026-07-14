package com.gray.anime.payment.interfaces;

import com.gray.anime.common.api.ApiResponse;
import com.gray.anime.payment.application.PaymentApplicationService;
import com.gray.anime.payment.interfaces.dto.PaymentView;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile({"local", "test"})
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentApplicationService service;

    public PaymentController(PaymentApplicationService service) {
        this.service = service;
    }

    @PostMapping("/{paymentNo}/mock-confirm")
    ApiResponse<PaymentView> mockConfirm(@PathVariable String paymentNo) {
        return ApiResponse.ok(service.mockConfirm(paymentNo));
    }
}
