package com.gray.anime.user.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class ConstantTimeLoginPasswordVerifierTest {
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final ConstantTimeLoginPasswordVerifier verifier = new ConstantTimeLoginPasswordVerifier(passwordEncoder);

    @Test
    void verifiesKnownAccountsAndStillHashesUnknownAccountAttempts() {
        String storedHash = passwordEncoder.encode("correct-password");

        assertThat(verifier.matches("correct-password", storedHash)).isTrue();
        assertThat(verifier.matches("wrong-password", storedHash)).isFalse();
        assertThat(verifier.matches("wrong-password", null)).isFalse();
    }
}
