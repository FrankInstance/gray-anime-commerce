package com.gray.anime.user.application;

public interface LoginAttemptGuard {
    void verifyAllowed(String normalizedEmail);

    void recordFailure(String normalizedEmail);

    void recordSuccess(String normalizedEmail);
}
