package com.gray.anime.eventing;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

public class InboxDeduplicator {
    private final JdbcTemplate jdbc;

    public InboxDeduplicator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean claim(String consumer, String eventId) {
        try {
            jdbc.update("INSERT INTO inbox_event (consumer, event_id, processed_at) VALUES (?, ?, NOW())",
                    consumer, eventId);
            return true;
        } catch (DuplicateKeyException ignored) {
            return false;
        }
    }
}
