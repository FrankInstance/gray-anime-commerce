package com.gray.anime.assistant.infrastructure.client;

import com.gray.anime.assistant.infrastructure.client.dto.BookshelfItemView;
import com.gray.anime.assistant.infrastructure.client.dto.ReadingProgressView;
import com.gray.anime.assistant.infrastructure.client.dto.WorkCard;
import com.gray.anime.common.api.ApiResponse;
import com.gray.anime.common.api.PageResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "content-service")
public interface ContentClient {
    @GetMapping("/api/v1/works")
    ApiResponse<PageResult<WorkCard>> works(
            @RequestParam("page") long page,
            @RequestParam("size") long size,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization
    );

    @GetMapping("/api/v1/reading/bookshelf")
    ApiResponse<PageResult<BookshelfItemView>> bookshelf(
            @RequestParam("page") long page,
            @RequestParam("size") long size,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization
    );

    @GetMapping("/api/v1/reading/progress")
    ApiResponse<PageResult<ReadingProgressView>> readingProgress(
            @RequestParam("page") long page,
            @RequestParam("size") long size,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization
    );
}
