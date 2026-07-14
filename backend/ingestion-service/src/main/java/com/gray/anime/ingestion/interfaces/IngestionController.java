package com.gray.anime.ingestion.interfaces;

import com.gray.anime.common.api.ApiResponse;
import com.gray.anime.common.api.PageResult;
import com.gray.anime.ingestion.application.IngestionApplicationService;
import com.gray.anime.ingestion.interfaces.dto.BulkImportRequest;
import com.gray.anime.ingestion.interfaces.dto.ImportTaskView;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/import-tasks")
public class IngestionController {
    private final IngestionApplicationService service;

    public IngestionController(IngestionApplicationService service) {
        this.service = service;
    }

    @GetMapping
    ApiResponse<PageResult<ImportTaskView>> tasks(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size
    ) {
        return ApiResponse.ok(service.tasks(page, size));
    }

    @PostMapping("/json")
    ApiResponse<ImportTaskView> json(@RequestBody BulkImportRequest request) {
        return ApiResponse.ok(service.importBulk("JSON", "admin-json-upload", request));
    }
}
