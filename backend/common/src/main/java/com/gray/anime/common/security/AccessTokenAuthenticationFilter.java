package com.gray.anime.common.security;

import com.gray.anime.common.api.TraceIds;
import com.gray.anime.common.exception.BizException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class AccessTokenAuthenticationFilter extends OncePerRequestFilter {
    private final AccessTokenVerifier verifier;

    public AccessTokenAuthenticationFilter(AccessTokenVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        CurrentUser currentUser;
        try {
            currentUser = resolveCurrentUser(request.getHeader(HttpHeaders.AUTHORIZATION));
        } catch (BizException exception) {
            reject(response, HttpStatus.UNAUTHORIZED, exception.code(), exception.getMessage());
            return;
        }

        if (ApiAccessPolicy.requiresLogin(path) && currentUser == CurrentUser.ANONYMOUS) {
            reject(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Login required");
            return;
        }
        if (ApiAccessPolicy.requiresAdmin(path) && !currentUser.isAdmin()) {
            reject(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Admin role required");
            return;
        }

        request.setAttribute(CurrentUser.REQUEST_ATTRIBUTE, currentUser);
        filterChain.doFilter(request, response);
    }

    private CurrentUser resolveCurrentUser(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return CurrentUser.ANONYMOUS;
        }
        if (!authorization.startsWith("Bearer ") || authorization.length() == "Bearer ".length()) {
            throw new BizException("TOKEN_INVALID", "Invalid access token");
        }
        JwtClaims claims = verifier.verify(authorization.substring("Bearer ".length()));
        return new CurrentUser(claims.userId(), claims.roles());
    }

    private void reject(HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
        String traceId = TraceIds.current();
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(TraceIds.HEADER, traceId);
        response.getWriter().write(
                "{\"code\":\"" + code + "\",\"message\":\"" + message
                        + "\",\"data\":null,\"traceId\":\"" + traceId + "\"}"
        );
    }
}
