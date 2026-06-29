package com.gray.anime.shop.interfaces;

import com.gray.anime.common.api.ApiResponse;
import com.gray.anime.common.api.PageResult;
import com.gray.anime.shop.application.ShopApplicationService;
import com.gray.anime.shop.interfaces.dto.AdminProductRequest;
import com.gray.anime.shop.interfaces.dto.ProductView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/products")
public class AdminShopController {
    private final ShopApplicationService service;

    public AdminShopController(ShopApplicationService service) {
        this.service = service;
    }

    @GetMapping
    ApiResponse<PageResult<ProductView>> products(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.ok(service.adminProducts(page, size, keyword));
    }

    @PostMapping
    ApiResponse<ProductView> create(@Valid @RequestBody AdminProductRequest request) {
        return ApiResponse.ok(service.adminCreateProduct(request));
    }
}
