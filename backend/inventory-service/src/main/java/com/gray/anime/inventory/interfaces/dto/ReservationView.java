package com.gray.anime.inventory.interfaces.dto;

import java.time.LocalDateTime;

public record ReservationView(String reservationNo, Long skuId, int quantity, String status, LocalDateTime expiresAt) {
}
