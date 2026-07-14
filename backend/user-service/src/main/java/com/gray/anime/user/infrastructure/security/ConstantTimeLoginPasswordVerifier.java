package com.gray.anime.user.infrastructure.security;

import com.gray.anime.user.application.LoginPasswordVerifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ConstantTimeLoginPasswordVerifier implements LoginPasswordVerifier {
    private final PasswordEncoder passwordEncoder;
    private final String unknownAccountHash;

    public ConstantTimeLoginPasswordVerifier(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
        this.unknownAccountHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Override
    public boolean matches(String rawPassword, String storedPasswordHash) {
        String hash = storedPasswordHash == null ? unknownAccountHash : storedPasswordHash;
        return passwordEncoder.matches(rawPassword, hash);
    }
}
