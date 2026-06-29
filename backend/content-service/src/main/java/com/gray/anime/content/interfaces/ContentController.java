package com.gray.anime.content.interfaces;

import com.gray.anime.common.api.ApiResponse;
import com.gray.anime.common.api.PageResult;
import com.gray.anime.common.security.CurrentUser;
import com.gray.anime.content.application.ContentApplicationService;
import com.gray.anime.content.interfaces.dto.ChapterView;
import com.gray.anime.content.interfaces.dto.ReaderResponse;
import com.gray.anime.content.interfaces.dto.WorkCard;
import com.gray.anime.content.interfaces.dto.WorkDetail;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ContentController {
    private final ContentApplicationService service;

    public ContentController(ContentApplicationService service) {
        this.service = service;
    }

    @GetMapping("/works")
    ApiResponse<PageResult<WorkCard>> works(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(service.listWorks(page, size, type, category, keyword));
    }

    @GetMapping("/works/{id}")
    ApiResponse<WorkDetail> work(@PathVariable Long id) {
        return ApiResponse.ok(service.getWork(id));
    }

    @GetMapping("/works/{id}/chapters")
    ApiResponse<List<ChapterView>> chapters(@PathVariable Long id) {
        return ApiResponse.ok(service.chapters(id));
    }

    @GetMapping("/chapters/{id}/reader")
    ApiResponse<ReaderResponse> reader(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.ok(service.reader(id, CurrentUser.from(request)));
    }
}
