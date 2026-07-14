package com.gray.anime.user.infrastructure.security;

import com.gray.anime.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class RedisLoginAttemptGuardTest {
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private RedisLoginAttemptGuard guard;

    @BeforeEach
    void setUp() {
        guard = new RedisLoginAttemptGuard(redisTemplate, validProperties());
    }

    @Test
    void accountKeyIsNormalizedAndDoesNotExposeTheEmail() {
        String digest = guard.accountDigest(" Reader@Example.com ");

        assertThat(digest).isEqualTo(guard.accountDigest("reader@example.com"));
        assertThat(digest).hasSize(64).doesNotContain("reader", "example.com", "@");
    }

    @Test
    void activeBlockReturnsAStandardTooManyRequestsError() {
        when(redisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(20L);

        assertThatThrownBy(() -> guard.verifyAllowed("reader@example.com"))
                .isInstanceOfSatisfying(BizException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("LOGIN_RATE_LIMITED");
                    assertThat(exception.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                });
    }

    @Test
    void thresholdFailureStartsTheBlock() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(),
                any(),
                any(),
                any()
        )).thenReturn(30L);

        assertThatThrownBy(() -> guard.recordFailure("reader@example.com"))
                .isInstanceOfSatisfying(BizException.class, exception ->
                        assertThat(exception.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void successfulLoginClearsFailureAndBlockKeysWithoutPlaintextAccountData() {
        String digest = guard.accountDigest("reader@example.com");
        List<String> expectedKeys = List.of(
                "auth:login:failures:" + digest,
                "auth:login:blocked:" + digest
        );

        guard.recordSuccess("reader@example.com");

        verify(redisTemplate).delete(expectedKeys);
        assertThat(expectedKeys).allSatisfy(key -> assertThat(key).doesNotContain("reader@example.com"));
    }

    @Test
    void redisFailureFallsBackToTheGatewayIpLimit() {
        String digest = guard.accountDigest("reader@example.com");
        List<String> keys = List.of(
                "auth:login:failures:" + digest,
                "auth:login:blocked:" + digest
        );
        when(redisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS)))
                .thenThrow(new RedisConnectionFailureException("offline"));
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                any(),
                any(),
                any(),
                any()
        )).thenThrow(new RedisConnectionFailureException("offline"));
        when(redisTemplate.delete(keys)).thenThrow(new RedisConnectionFailureException("offline"));

        assertThatCode(() -> guard.verifyAllowed("reader@example.com")).doesNotThrowAnyException();
        assertThatCode(() -> guard.recordFailure("reader@example.com")).doesNotThrowAnyException();
        assertThatCode(() -> guard.recordSuccess("reader@example.com")).doesNotThrowAnyException();
    }

    private LoginThrottleProperties validProperties() {
        LoginThrottleProperties properties = new LoginThrottleProperties();
        properties.setAccountKeySecret("test-login-throttle-secret-with-32-bytes");
        properties.setFailureWindowSeconds(86_400);
        properties.setMaxAttempts(5);
        properties.setInitialBlockSeconds(30);
        properties.setMaxBlockSeconds(900);
        return properties;
    }
}
