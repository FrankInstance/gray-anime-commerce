package com.gray.anime.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AccessTokenAuthenticationFilterTest {
    private static final String ISSUER = "gray-auth";
    private static final String AUDIENCE = "gray-api";
    private static final String KEY_ID = "gray-access-2026-01";

    private AccessTokenIssuer issuer;
    private AccessTokenAuthenticationFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        KeyPair keyPair = keyPair();
        issuer = issuer(keyPair);
        filter = new AccessTokenAuthenticationFilter(new AccessTokenVerifier(
                (RSAPublicKey) keyPair.getPublic(), ISSUER, AUDIENCE, KEY_ID
        ));
    }

    @Test
    void businessServiceBuildsCurrentUserFromVerifiedToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + issuer.issue(7L, Set.of("USER", "VIP"), 60));
        request.addHeader("X-User-Id", "999");
        request.addHeader("X-User-Roles", "SUPER_ADMIN");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<CurrentUser> resolved = new AtomicReference<>();

        filter.doFilter(request, response, (currentRequest, currentResponse) ->
                resolved.set(CurrentUser.from((MockHttpServletRequest) currentRequest))
        );

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(resolved.get().id()).isEqualTo(7L);
        assertThat(resolved.get().roles()).containsExactlyInAnyOrder("USER", "VIP");
    }

    @Test
    void identityHeadersAloneNeverCreateAUser() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "999");
        request.addHeader("X-User-Roles", "SUPER_ADMIN");

        assertThat(CurrentUser.from(request)).isEqualTo(CurrentUser.ANONYMOUS);
    }

    @Test
    void regularUserCannotCallAdminControllerDirectly() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/orders");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + issuer.issue(7L, Set.of("USER"), 60));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Boolean> forwarded = new AtomicReference<>(false);

        filter.doFilter(request, response, (currentRequest, currentResponse) -> forwarded.set(true));

        assertThat(forwarded.get()).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void assistantRequiresAValidatedAccessToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/assistant/status");
        request.addHeader("X-User-Id", "999");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Boolean> forwarded = new AtomicReference<>(false);

        filter.doFilter(request, response, (currentRequest, currentResponse) -> forwarded.set(true));

        assertThat(forwarded.get()).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    private AccessTokenIssuer issuer(KeyPair pair) {
        return new AccessTokenIssuer(
                (RSAPublicKey) pair.getPublic(),
                (RSAPrivateKey) pair.getPrivate(),
                ISSUER,
                AUDIENCE,
                KEY_ID
        );
    }

    private KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
