package com.gray.anime.user.interfaces.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(@Size(min = 2, max = 30) String username) {
}
