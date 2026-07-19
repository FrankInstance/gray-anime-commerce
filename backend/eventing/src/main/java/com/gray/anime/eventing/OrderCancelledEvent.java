package com.gray.anime.eventing;

import java.util.List;

public record OrderCancelledEvent(String orderNo, Long orderId, String reason, List<String> reservationNos) {
}
