package com.gray.anime.assistant.infrastructure.client;

import com.gray.anime.assistant.infrastructure.client.dto.ProductView;
import com.gray.anime.common.api.ApiResponse;
import com.gray.anime.common.api.PageResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "shop-service")
public interface ShopClient {
    @GetMapping("/api/v1/products")
    ApiResponse<PageResult<ProductView>> products(
            @RequestParam("page") long page,
            @RequestParam("size") long size,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization
    );
}
