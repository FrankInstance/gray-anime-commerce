package com.gray.anime.gateway.filter;

import com.gray.anime.common.security.AccessTokenIssuer;
import com.gray.anime.common.security.AccessTokenVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayAuthFilterTest {
    private static final String ISSUER = "gray-auth";
    private static final String AUDIENCE = "gray-api";
    private static final String KEY_ID = "gray-access-2026-01";

    private KeyPair keyPair;
    private AccessTokenIssuer issuer;
    private GatewayAuthFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        keyPair = keyPair();
        issuer = issuer(keyPair);
        filter = new GatewayAuthFilter(new AccessTokenVerifier(
                (RSAPublicKey) keyPair.getPublic(), ISSUER, AUDIENCE, KEY_ID
        ));
    }

    @Test
    void stripsClientSuppliedIdentityHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/products")
                        .header("X-User-Id", "999")
                        .header("X-User-Roles", "SUPER_ADMIN")
        );
        AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = current -> {
            forwarded.set(current.getRequest());
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getHeaders().getFirst("X-User-Id")).isNull();
        assertThat(forwarded.get().getHeaders().getFirst("X-User-Roles")).isNull();
    }

    @Test
    void acceptsRsaTokenWithoutForwardingTrustedIdentityAsHeaders() {
        String token = issuer.issue(7L, Set.of("USER"), 60);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-User-Id", "999")
        );
        AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();

        filter.filter(exchange, current -> {
            forwarded.set(current.getRequest());
            return Mono.empty();
        }).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getHeaders().getFirst("X-User-Id")).isNull();
        assertThat(forwarded.get().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer " + token);
    }

    @Test
    void rejectsTokenSignedWithAnotherPrivateKey() throws Exception {
        String forged = issuer(keyPair()).issue(7L, Set.of("ADMIN"), 60);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + forged)
        );
        AtomicReference<Boolean> forwarded = new AtomicReference<>(false);

        filter.filter(exchange, current -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        assertThat(forwarded.get()).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void internalApiIsNotReachableThroughTheEdgeGateway() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/internal/inventory/reservations")
        );

        filter.filter(exchange, current -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
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
