package com.gray.anime.common.security;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JwtSupportTest {
    @Test
    void issuesAndVerifiesToken() {
        JwtSupport support = new JwtSupport("dev-secret-dev-secret-dev-secret-123456");
        String token = support.issue(7L, Set.of("USER", "VIP"), 60);

        JwtClaims claims = support.verify(token);

        assertThat(claims.userId()).isEqualTo(7L);
        assertThat(claims.roles()).contains("USER", "VIP");
    }
}
