package com.gray.anime.user.interfaces;

import com.gray.anime.common.api.ApiResponse;
import com.gray.anime.common.api.PageResult;
import com.gray.anime.user.application.UserApplicationService;
import com.gray.anime.user.interfaces.dto.AdminUserUpdateRequest;
import com.gray.anime.user.interfaces.dto.UserProfile;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    private final UserApplicationService service;

    public AdminUserController(UserApplicationService service) {
        this.service = service;
    }

    @GetMapping
    ApiResponse<PageResult<UserProfile>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(service.adminUsers(page, size, keyword));
    }

    @PatchMapping("/{id}")
    ApiResponse<UserProfile> update(@PathVariable Long id, @RequestBody AdminUserUpdateRequest request) {
        return ApiResponse.ok(service.adminUpdateUser(id, request));
    }
}
