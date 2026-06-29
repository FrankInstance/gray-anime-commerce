package com.gray.anime.user.interfaces.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 2, max = 30) String username,
        @NotBlank @Size(min = 6, max = 64) String password
) {
}
