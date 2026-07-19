package com.gray.anime.eventing;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InboxDeduplicatorTest {
    @Test
    void duplicateDeliveryIsIgnored() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), eq("consumer"), eq("event-1")))
                .thenReturn(1)
                .thenThrow(new DuplicateKeyException("duplicate"));
        InboxDeduplicator inbox = new InboxDeduplicator(jdbc);

        assertThat(inbox.claim("consumer", "event-1")).isTrue();
        assertThat(inbox.claim("consumer", "event-1")).isFalse();
    }
}
