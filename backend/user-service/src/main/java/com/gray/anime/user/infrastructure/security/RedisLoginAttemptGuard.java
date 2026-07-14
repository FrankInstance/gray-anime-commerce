package com.gray.anime.user.infrastructure.security;

import com.gray.anime.common.exception.BizException;
import com.gray.anime.user.application.LoginAttemptGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Component
public class RedisLoginAttemptGuard implements LoginAttemptGuard {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisLoginAttemptGuard.class);
    private static final String KEY_PREFIX = "auth:login:";
    private static final DefaultRedisScript<Long> RECORD_FAILURE_SCRIPT = new DefaultRedisScript<>("""
            local failures = redis.call('INCR', KEYS[1])
            redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))
            if failures < tonumber(ARGV[2]) then
                return 0
            end
            local exponent = failures - tonumber(ARGV[2])
            local blockSeconds = tonumber(ARGV[3]) * (2 ^ exponent)
            if blockSeconds > tonumber(ARGV[4]) then
                blockSeconds = tonumber(ARGV[4])
            end
            blockSeconds = math.floor(blockSeconds)
            redis.call('SET', KEYS[2], '1', 'EX', blockSeconds)
            return blockSeconds
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final LoginThrottleProperties properties;
    private final SecretKeySpec accountKey;

    public RedisLoginAttemptGuard(StringRedisTemplate redisTemplate, LoginThrottleProperties properties) {
        properties.validate();
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.accountKey = new SecretKeySpec(
                properties.getAccountKeySecret().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
    }

    @Override
    public void verifyAllowed(String normalizedEmail) {
        try {
            Long remainingSeconds = redisTemplate.getExpire(blockedKeyForEmail(normalizedEmail), TimeUnit.SECONDS);
            if (remainingSeconds != null && remainingSeconds > 0) {
                throw throttled();
            }
        } catch (BizException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            LOGGER.warn("Login throttle check failed; relying on the gateway IP limit", exception);
        }
    }

    @Override
    public void recordFailure(String normalizedEmail) {
        String digest = accountDigest(normalizedEmail);
        try {
            Long blockSeconds = redisTemplate.execute(
                    RECORD_FAILURE_SCRIPT,
                    List.of(failureKey(digest), blockedKey(digest)),
                    Long.toString(properties.getFailureWindowSeconds()),
                    Integer.toString(properties.getMaxAttempts()),
                    Long.toString(properties.getInitialBlockSeconds()),
                    Long.toString(properties.getMaxBlockSeconds())
            );
            if (blockSeconds != null && blockSeconds > 0) {
                throw throttled();
            }
        } catch (BizException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            LOGGER.warn("Login throttle update failed; relying on the gateway IP limit", exception);
        }
    }

    @Override
    public void recordSuccess(String normalizedEmail) {
        String digest = accountDigest(normalizedEmail);
        try {
            redisTemplate.delete(List.of(failureKey(digest), blockedKey(digest)));
        } catch (DataAccessException exception) {
            LOGGER.warn("Login throttle reset failed; the successful login will continue", exception);
        }
    }

    String accountDigest(String email) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(accountKey);
            String normalized = email.trim().toLowerCase(Locale.ROOT);
            return HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HmacSHA256 is not available", exception);
        }
    }

    private String failureKey(String digest) {
        return KEY_PREFIX + "failures:" + digest;
    }

    private String blockedKeyForEmail(String normalizedEmail) {
        return blockedKey(accountDigest(normalizedEmail));
    }

    private String blockedKey(String digest) {
        return KEY_PREFIX + "blocked:" + digest;
    }

    private BizException throttled() {
        return new BizException(
                HttpStatus.TOO_MANY_REQUESTS,
                "LOGIN_RATE_LIMITED",
                "登录尝试过于频繁，请稍后再试。"
        );
    }
}
