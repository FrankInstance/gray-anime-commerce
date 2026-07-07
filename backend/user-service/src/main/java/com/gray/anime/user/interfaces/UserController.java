package com.gray.anime.user.interfaces;

import com.gray.anime.common.api.ApiResponse;
import com.gray.anime.common.api.PageResult;
import com.gray.anime.common.security.CurrentUser;
import com.gray.anime.user.application.UserApplicationService;
import com.gray.anime.user.interfaces.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class UserController {
    private final UserApplicationService service;

    public UserController(UserApplicationService service) {
        this.service = service;
    }

    @PostMapping("/auth/register")
    ApiResponse<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(service.register(request));
    }

    @PostMapping("/auth/login")
    ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(service.login(request));
    }

    @PostMapping("/auth/password-reset/request")
    ApiResponse<PasswordResetDevResponse> passwordReset(@Valid @RequestBody PasswordResetRequest request) {
        return ApiResponse.ok(service.requestPasswordReset(request));
    }

    @PostMapping("/auth/password-reset/confirm")
    ApiResponse<Void> passwordResetConfirm(@Valid @RequestBody PasswordResetConfirm request) {
        service.confirmPasswordReset(request);
        return ApiResponse.ok(null);
    }

    @GetMapping("/users/me")
    ApiResponse<UserProfile> me(HttpServletRequest request) {
        return ApiResponse.ok(service.me(CurrentUser.from(request)));
    }

    @PatchMapping("/users/me")
    ApiResponse<UserProfile> updateMe(HttpServletRequest request, @Valid @RequestBody UpdateProfileRequest body) {
        return ApiResponse.ok(service.updateMe(CurrentUser.from(request), body));
    }

    @PostMapping("/checkins")
    ApiResponse<CheckinResponse> checkin(HttpServletRequest request) {
        return ApiResponse.ok(service.checkin(CurrentUser.from(request)));
    }

    @GetMapping("/users/me/points-ledger")
    ApiResponse<PageResult<PointsLedgerView>> pointsLedger(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(service.pointsLedger(CurrentUser.from(request), page, size));
    }
}
