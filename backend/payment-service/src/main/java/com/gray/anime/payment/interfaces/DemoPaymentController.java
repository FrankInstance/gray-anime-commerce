package com.gray.anime.payment.interfaces;

import com.gray.anime.common.api.ApiResponse;
import com.gray.anime.common.security.CurrentUser;
import com.gray.anime.payment.application.PaymentApplicationService;
import com.gray.anime.payment.interfaces.dto.PaymentView;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"local", "test", "demo"})
@RequestMapping("/api/v1/payments")
public class DemoPaymentController {
    private final PaymentApplicationService service;

    public DemoPaymentController(PaymentApplicationService service) {
        this.service = service;
    }

    @PostMapping("/{paymentNo}/demo-confirm")
    ApiResponse<PaymentView> confirm(@PathVariable String paymentNo, HttpServletRequest request) {
        return ApiResponse.ok(service.confirmDemo(CurrentUser.from(request), paymentNo));
    }
}
