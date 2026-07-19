package com.gray.anime.eventing;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record DomainEventEnvelope(
        String eventId,
        String eventType,
        String aggregateType,
        String aggregateId,
        int schemaVersion,
        Instant occurredAt,
        JsonNode payload
) {
}
