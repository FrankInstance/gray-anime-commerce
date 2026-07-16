package com.gray.anime.assistant.application;

import com.gray.anime.assistant.config.AssistantProperties;
import com.gray.anime.common.exception.BizException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class AssistantQuotaService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DefaultRedisScript<Long> RESERVE_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[2]) end
            if current > tonumber(ARGV[1]) then
                redis.call('DECR', KEYS[1])
                return -1
            end
            return tonumber(ARGV[1]) - current
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final AssistantProperties properties;

    public AssistantQuotaService(StringRedisTemplate redisTemplate, AssistantProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public void reserve(long userId) {
        try {
            Long remaining = redisTemplate.execute(
                    RESERVE_SCRIPT,
                    List.of("assistant:quota:" + ZonedDateTime.now(BUSINESS_ZONE).toLocalDate() + ":" + userId),
                    Integer.toString(properties.dailyLimit()),
                    Long.toString(secondsUntilTomorrow())
            );
            if (remaining == null) {
                throw unavailable();
            }
            if (remaining < 0) {
                throw new BizException(HttpStatus.TOO_MANY_REQUESTS, "ASSISTANT_DAILY_LIMIT",
                        "今天的提问次数已用完，请明天再试");
            }
        } catch (BizException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    private long secondsUntilTomorrow() {
        ZonedDateTime now = ZonedDateTime.now(BUSINESS_ZONE);
        return Math.max(60, Duration.between(now, now.plusDays(1).truncatedTo(ChronoUnit.DAYS)).getSeconds());
    }

    private BizException unavailable() {
        return new BizException(HttpStatus.SERVICE_UNAVAILABLE, "ASSISTANT_UNAVAILABLE",
                "客服暂时不可用，请稍后再试");
    }
}
