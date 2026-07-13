package com.gray.anime.user.interfaces;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;

@Component
public class RefreshCookieService {
    private final String cookieName;
    private final Duration idleTtl;
    private final boolean secure;

    public RefreshCookieService(
            @Value("${security.session.refresh-cookie-name:gray_refresh}") String cookieName,
            @Value("${security.session.idle-ttl-seconds:259200}") long idleTtlSeconds,
            @Value("${security.session.cookie-secure:false}") boolean secure
    ) {
        this.cookieName = cookieName;
        this.idleTtl = Duration.ofSeconds(idleTtlSeconds);
        this.secure = secure;
    }

    public String read(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    public void write(HttpServletResponse response, String refreshToken) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(refreshToken, idleTtl).toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(maxAge)
                .build();
    }
}
