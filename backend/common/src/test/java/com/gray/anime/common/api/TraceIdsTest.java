package com.gray.anime.common.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdsTest {
    @AfterEach
    void clearTraceId() {
        TraceIds.clear();
    }

    @Test
    void acceptedTraceIdIsNormalizedAndAddedToMdc() {
        TraceIds.set("ABCDEF0123456789ABCDEF01");

        assertThat(TraceIds.current()).isEqualTo("abcdef0123456789abcdef01");
        assertThat(MDC.get(TraceIds.MDC_KEY)).isEqualTo("abcdef0123456789abcdef01");
    }

    @Test
    void unsafeTraceIdIsReplacedBeforeItCanReachLogs() {
        TraceIds.set("attacker\nforged-log-entry");

        assertThat(TraceIds.current())
                .matches("[a-f0-9]{24}")
                .doesNotContain("attacker", "\n");
    }

    @Test
    void clearRemovesThreadLocalAndMdcState() {
        TraceIds.set("abcdef0123456789abcdef01");
        TraceIds.clear();

        assertThat(MDC.get(TraceIds.MDC_KEY)).isNull();
        assertThat(TraceIds.current()).isNotEqualTo("abcdef0123456789abcdef01");
    }
}
