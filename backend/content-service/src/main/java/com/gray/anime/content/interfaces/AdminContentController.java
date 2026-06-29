package com.gray.anime.content.interfaces;

import com.gray.anime.common.api.ApiResponse;
import com.gray.anime.common.api.PageResult;
import com.gray.anime.content.application.ContentApplicationService;
import com.gray.anime.content.interfaces.dto.AdminWorkRequest;
import com.gray.anime.content.interfaces.dto.WorkCard;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/works")
public class AdminContentController {
    private final ContentApplicationService service;

    public AdminContentController(ContentApplicationService service) {
        this.service = service;
    }

    @GetMapping
    ApiResponse<PageResult<WorkCard>> works(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(service.adminWorks(page, size, keyword));
    }

    @PostMapping
    ApiResponse<WorkCard> create(@Valid @RequestBody AdminWorkRequest request) {
        return ApiResponse.ok(service.adminCreateWork(request));
    }
}
