package com.gray.anime.user.interfaces.dto;

import java.time.LocalDateTime;

public record PointsLedgerView(
        Long id,
        Integer amount,
        String reason,
        String bizKey,
        LocalDateTime createdAt
) {
}
