package com.gray.anime.ingestion.interfaces;

import com.gray.anime.common.api.ApiResponse;
import com.gray.anime.ingestion.application.IngestionApplicationService;
import com.gray.anime.ingestion.interfaces.dto.ImportTaskView;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"local", "test"})
@RequestMapping("/api/v1/admin/import-tasks")
public class DemoIngestionController {
    private final IngestionApplicationService service;

    public DemoIngestionController(IngestionApplicationService service) {
        this.service = service;
    }

    @PostMapping("/demo")
    ApiResponse<ImportTaskView> demo() {
        return ApiResponse.ok(service.importDemo());
    }
}
