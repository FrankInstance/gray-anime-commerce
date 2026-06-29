package com.gray.anime.shop.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gray.anime.common.api.PageResult;
import com.gray.anime.common.exception.BizException;
import com.gray.anime.common.security.CurrentUser;
import com.gray.anime.shop.domain.CartItem;
import com.gray.anime.shop.domain.Product;
import com.gray.anime.shop.domain.Sku;
import com.gray.anime.shop.infrastructure.mapper.CartItemMapper;
import com.gray.anime.shop.infrastructure.mapper.ProductMapper;
import com.gray.anime.shop.infrastructure.mapper.SkuMapper;
import com.gray.anime.shop.interfaces.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ShopApplicationService {
    private final ProductMapper productMapper;
    private final SkuMapper skuMapper;
    private final CartItemMapper cartItemMapper;

    public ShopApplicationService(ProductMapper productMapper, SkuMapper skuMapper, CartItemMapper cartItemMapper) {
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
        this.cartItemMapper = cartItemMapper;
    }

    public PageResult<ProductView> products(long page, long size, String type, String keyword, CurrentUser user) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, "ON_SALE")
                .orderByDesc(Product::getId);
        if (type != null && !type.isBlank()) {
            wrapper.eq(Product::getProductType, type.trim().toUpperCase());
        }
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            wrapper.and(w -> w.like(Product::getTitle, value).or().like(Product::getDescription, value));
        }
        Page<Product> result = productMapper.selectPage(Page.of(page, size), wrapper);
        List<Long> productIds = result.getRecords().stream().map(Product::getId).toList();
        Map<Long, List<Sku>> skuMap = productIds.isEmpty() ? Map.of() : skuMapper.selectList(new LambdaQueryWrapper<Sku>().in(Sku::getProductId, productIds))
                .stream().collect(Collectors.groupingBy(Sku::getProductId));
        return new PageResult<>(result.getRecords().stream().map(p -> view(p, skuMap.getOrDefault(p.getId(), List.of()), user.isVip())).toList(),
                page, size, result.getTotal());
    }

    public ProductView getProduct(Long id, CurrentUser user) {
        Product product = productMapper.selectById(id);
        if (product == null || !"ON_SALE".equals(product.getStatus())) {
            throw new BizException("PRODUCT_NOT_FOUND", "Product not found");
        }
        List<Sku> skus = skuMapper.selectList(new LambdaQueryWrapper<Sku>().eq(Sku::getProductId, id));
        return view(product, skus, user.isVip());
    }

    @Transactional
    public CartItemView addCartItem(CurrentUser user, AddCartItemRequest request) {
        if (user.id() <= 0) {
            throw new BizException("UNAUTHORIZED", "Login required");
        }
        Sku sku = skuMapper.selectById(request.skuId());
        if (sku == null || !"ACTIVE".equals(sku.getStatus())) {
            throw new BizException("SKU_NOT_FOUND", "SKU not found");
        }
        CartItem existing = cartItemMapper.selectOne(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, user.id())
                .eq(CartItem::getSkuId, request.skuId()));
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            existing = new CartItem();
            existing.setUserId(user.id());
            existing.setSkuId(request.skuId());
            existing.setQuantity(request.quantity());
            existing.setCreatedAt(now);
        } else {
            existing.setQuantity(existing.getQuantity() + request.quantity());
        }
        existing.setUpdatedAt(now);
        if (existing.getId() == null) {
            cartItemMapper.insert(existing);
        } else {
            cartItemMapper.updateById(existing);
        }
        return new CartItemView(existing.getId(), existing.getSkuId(), existing.getQuantity());
    }

    @Transactional
    public ProductView adminCreateProduct(AdminProductRequest request) {
        LocalDateTime now = LocalDateTime.now();
        Product product = new Product();
        product.setTitle(request.title());
        product.setProductType(request.productType() == null ? "BOOK" : request.productType().toUpperCase());
        product.setDescription(request.description());
        product.setCoverUrl(request.coverUrl());
        product.setLimitedFlag(request.limited());
        product.setSaleStartAt(request.saleStartAt());
        product.setStatus("ON_SALE");
        product.setCreatedAt(now);
        product.setUpdatedAt(now);
        productMapper.insert(product);

        Sku sku = new Sku();
        sku.setProductId(product.getId());
        sku.setSkuName(request.skuName());
        sku.setPriceCents(request.priceCents());
        sku.setStatus("ACTIVE");
        skuMapper.insert(sku);
        return view(product, List.of(sku), false);
    }

    public PageResult<ProductView> adminProducts(long page, long size, String keyword) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>().orderByDesc(Product::getId);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Product::getTitle, keyword.trim());
        }
        Page<Product> result = productMapper.selectPage(Page.of(page, size), wrapper);
        List<Long> productIds = result.getRecords().stream().map(Product::getId).toList();
        Map<Long, List<Sku>> skuMap = productIds.isEmpty() ? Map.of() : skuMapper.selectList(new LambdaQueryWrapper<Sku>().in(Sku::getProductId, productIds))
                .stream().collect(Collectors.groupingBy(Sku::getProductId));
        return new PageResult<>(result.getRecords().stream().map(p -> view(p, skuMap.getOrDefault(p.getId(), List.of()), false)).toList(),
                page, size, result.getTotal());
    }

    private ProductView view(Product product, List<Sku> skus, boolean vip) {
        return new ProductView(product.getId(), product.getTitle(), product.getProductType(), product.getDescription(), product.getCoverUrl(),
                Boolean.TRUE.equals(product.getLimitedFlag()), product.getSaleStartAt(),
                skus.stream().map(s -> new SkuView(s.getId(), s.getSkuName(), s.getPriceCents(), vip ? Math.round(s.getPriceCents() * 0.9f) : s.getPriceCents())).toList());
    }
}
