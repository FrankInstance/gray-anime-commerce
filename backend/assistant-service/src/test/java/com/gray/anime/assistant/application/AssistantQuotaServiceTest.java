package com.gray.anime.assistant.application;

import com.gray.anime.assistant.config.AssistantProperties;
import com.gray.anime.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class AssistantQuotaServiceTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final AssistantProperties properties = new AssistantProperties(
            true, "sk-test-key-value", "qwen-plus", false,
            30, 500, 20, 6000, 800, 30, 4, 0.65);

    @Test
    void acceptsARequestWithinTheDailyLimit() {
        when(redis.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(29L);

        assertThatCode(() -> new AssistantQuotaService(redis, properties).reserve(7L))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsARequestPastTheDailyLimit() {
        when(redis.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(-1L);

        assertThatThrownBy(() -> new AssistantQuotaService(redis, properties).reserve(7L))
                .isInstanceOf(BizException.class)
                .hasMessage("今天的提问次数已用完，请明天再试");
    }
}
