package com.gray.anime.user.interfaces;

import com.gray.anime.common.api.ApiResponse;
import com.gray.anime.common.api.PageResult;
import com.gray.anime.common.security.CurrentUser;
import com.gray.anime.user.application.UserApplicationService;
import com.gray.anime.user.application.AuthSessionResult;
import com.gray.anime.user.interfaces.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class UserController {
    private final UserApplicationService service;
    private final RefreshCookieService refreshCookies;

    public UserController(UserApplicationService service, RefreshCookieService refreshCookies) {
        this.service = service;
        this.refreshCookies = refreshCookies;
    }

    @PostMapping("/auth/register")
    ApiResponse<TokenResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        return completeAuth(service.register(request), response);
    }

    @PostMapping("/auth/login")
    ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        return completeAuth(service.login(request), response);
    }

    @PostMapping("/auth/refresh")
    ApiResponse<TokenResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        return completeAuth(service.refreshSession(refreshCookies.read(request)), response);
    }

    @PostMapping("/auth/logout")
    ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        service.logout(refreshCookies.read(request));
        refreshCookies.clear(response);
        return ApiResponse.ok(null);
    }

    @PostMapping("/auth/password-reset/request")
    ApiResponse<PasswordResetDevResponse> passwordReset(@Valid @RequestBody PasswordResetRequest request) {
        return ApiResponse.ok(service.requestPasswordReset(request));
    }

    @PostMapping("/auth/password-reset/confirm")
    ApiResponse<Void> passwordResetConfirm(
            @Valid @RequestBody PasswordResetConfirm request,
            HttpServletResponse response
    ) {
        service.confirmPasswordReset(request);
        refreshCookies.clear(response);
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

    private ApiResponse<TokenResponse> completeAuth(AuthSessionResult result, HttpServletResponse response) {
        refreshCookies.write(response, result.refreshToken());
        return ApiResponse.ok(result.tokenResponse());
    }
}
