package com.gray.anime.user.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@ConfigurationProperties(prefix = "security.login-throttle")
public class LoginThrottleProperties {
    private String accountKeySecret;
    private long failureWindowSeconds;
    private int maxAttempts;
    private long initialBlockSeconds;
    private long maxBlockSeconds;

    public String getAccountKeySecret() {
        return accountKeySecret;
    }

    public void setAccountKeySecret(String accountKeySecret) {
        this.accountKeySecret = accountKeySecret;
    }

    public long getFailureWindowSeconds() {
        return failureWindowSeconds;
    }

    public void setFailureWindowSeconds(long failureWindowSeconds) {
        this.failureWindowSeconds = failureWindowSeconds;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getInitialBlockSeconds() {
        return initialBlockSeconds;
    }

    public void setInitialBlockSeconds(long initialBlockSeconds) {
        this.initialBlockSeconds = initialBlockSeconds;
    }

    public long getMaxBlockSeconds() {
        return maxBlockSeconds;
    }

    public void setMaxBlockSeconds(long maxBlockSeconds) {
        this.maxBlockSeconds = maxBlockSeconds;
    }

    void validate() {
        if (accountKeySecret == null || accountKeySecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("security.login-throttle.account-key-secret must contain at least 32 UTF-8 bytes");
        }
        if (failureWindowSeconds <= 0 || maxAttempts <= 0 || initialBlockSeconds <= 0 || maxBlockSeconds < initialBlockSeconds) {
            throw new IllegalStateException("security.login-throttle durations and attempt limits are invalid");
        }
    }
}
