package com.gray.anime.content.interfaces.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminWorkRequest(
        @NotBlank String title,
        @NotBlank String workType,
        String author,
        String category,
        String description,
        String coverUrl
) {
}
