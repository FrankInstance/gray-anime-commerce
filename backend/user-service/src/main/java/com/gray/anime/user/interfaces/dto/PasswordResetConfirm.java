package com.gray.anime.user.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirm(
        @NotBlank String token,
        @NotBlank @Size(min = 6, max = 64) String newPassword
) {
}
