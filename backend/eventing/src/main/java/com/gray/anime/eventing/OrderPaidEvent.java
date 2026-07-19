package com.gray.anime.eventing;

import java.time.LocalDateTime;
import java.util.List;

public record OrderPaidEvent(Long orderId, String orderNo, Long userId, String orderType,
                             Integer totalCents, Integer totalPoints, List<String> reservationNos,
                             LocalDateTime paidAt) {
}
