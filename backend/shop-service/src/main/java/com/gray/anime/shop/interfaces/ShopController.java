package com.gray.anime.shop.interfaces;

import com.gray.anime.common.api.ApiResponse;
import com.gray.anime.common.api.PageResult;
import com.gray.anime.common.security.CurrentUser;
import com.gray.anime.shop.application.ShopApplicationService;
import com.gray.anime.shop.interfaces.dto.AddCartItemRequest;
import com.gray.anime.shop.interfaces.dto.CartItemView;
import com.gray.anime.shop.interfaces.dto.ProductView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ShopController {
    private final ShopApplicationService service;

    public ShopController(ShopApplicationService service) {
        this.service = service;
    }

    @GetMapping("/products")
    ApiResponse<PageResult<ProductView>> products(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request
    ) {
        return ApiResponse.ok(service.products(page, size, type, keyword, CurrentUser.from(request)));
    }

    @GetMapping("/products/{id}")
    ApiResponse<ProductView> product(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.ok(service.getProduct(id, CurrentUser.from(request)));
    }

    @PostMapping("/cart/items")
    ApiResponse<CartItemView> addCartItem(@Valid @RequestBody AddCartItemRequest body, HttpServletRequest request) {
        return ApiResponse.ok(service.addCartItem(CurrentUser.from(request), body));
    }
}
