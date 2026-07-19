package com.gray.anime.eventing;

import java.time.LocalDateTime;

public record FulfillmentResultEvent(String orderNo, String handler, String message, LocalDateTime occurredAt) {
}
