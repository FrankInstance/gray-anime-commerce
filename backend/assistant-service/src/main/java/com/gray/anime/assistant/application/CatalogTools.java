package com.gray.anime.assistant.application;

import com.gray.anime.assistant.infrastructure.client.ContentClient;
import com.gray.anime.assistant.infrastructure.client.ShopClient;
import com.gray.anime.assistant.infrastructure.client.dto.BookshelfItemView;
import com.gray.anime.assistant.infrastructure.client.dto.ProductView;
import com.gray.anime.assistant.infrastructure.client.dto.ReadingProgressView;
import com.gray.anime.assistant.infrastructure.client.dto.SkuView;
import com.gray.anime.assistant.infrastructure.client.dto.WorkCard;
import com.gray.anime.assistant.interfaces.dto.AssistantReference;
import com.gray.anime.assistant.interfaces.dto.ProductSkuReference;
import com.gray.anime.common.api.ApiResponse;
import com.gray.anime.common.api.PageResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CatalogTools {
    private static final Logger log = LoggerFactory.getLogger(CatalogTools.class);
    private static final int RESULT_LIMIT = 5;
    private final ContentClient contentClient;
    private final ShopClient shopClient;
    private final MeterRegistry meterRegistry;

    public CatalogTools(ContentClient contentClient, ShopClient shopClient, MeterRegistry meterRegistry) {
        this.contentClient = contentClient;
        this.shopClient = shopClient;
        this.meterRegistry = meterRegistry;
    }

    @Tool(name = "search_works", description = "搜索站内轻小说或漫画。只有这个工具返回的作品才可以推荐或展示。")
    public CatalogSearchResult searchWorks(
            @ToolParam(description = "作品标题、作者或关键词；没有关键词时传空字符串") String keyword,
            @ToolParam(description = "作品类型，只能是 NOVEL、MANGA 或空字符串") String type,
            @ToolParam(description = "作品分类；没有分类时传空字符串") String category,
            ToolContext context
    ) {
        try {
            ApiResponse<PageResult<WorkCard>> response = contentClient.works(
                    1, RESULT_LIMIT, optional(type), optional(category), optional(keyword), authorization(context));
            List<AssistantReference> references = response.data().items().stream()
                    .map(this::toReference)
                    .toList();
            collector(context).addAll(references);
            recordTool("search_works", "success");
            return CatalogSearchResult.found(references);
        } catch (RuntimeException exception) {
            recordTool("search_works", "failure");
            log.warn("assistant tool search_works failed: {}", exception.getClass().getSimpleName());
            return CatalogSearchResult.unavailable();
        }
    }

    @Tool(name = "search_products", description = "搜索站内会员购商品。只有这个工具返回的商品、规格和价格才可以展示。")
    public CatalogSearchResult searchProducts(
            @ToolParam(description = "商品名称或关键词；没有关键词时传空字符串") String keyword,
            @ToolParam(description = "商品类型；没有指定时传空字符串") String type,
            ToolContext context
    ) {
        try {
            ApiResponse<PageResult<ProductView>> response = shopClient.products(
                    1, RESULT_LIMIT, optional(type), optional(keyword), authorization(context));
            List<AssistantReference> references = response.data().items().stream()
                    .map(this::toReference)
                    .toList();
            collector(context).addAll(references);
            recordTool("search_products", "success");
            return CatalogSearchResult.found(references);
        } catch (RuntimeException exception) {
            recordTool("search_products", "failure");
            log.warn("assistant tool search_products failed: {}", exception.getClass().getSimpleName());
            return CatalogSearchResult.unavailable();
        }
    }

    @Tool(name = "get_reading_profile", description = "读取当前用户的书架和最近阅读摘要，用于个性化推荐。不得推断订单或支付信息。")
    public ReadingProfileResult getReadingProfile(ToolContext context) {
        try {
            String authorization = authorization(context);
            List<ReadingProfileResult.ReadingProfileItem> bookshelf = contentClient.bookshelf(1, 10, authorization)
                    .data().items().stream().map(this::toProfileItem).toList();
            List<ReadingProfileResult.ReadingProfileItem> recent = contentClient.readingProgress(1, 10, authorization)
                    .data().items().stream().map(this::toProfileItem).toList();
            recordTool("get_reading_profile", "success");
            return new ReadingProfileResult(true, bookshelf, recent, "查询成功");
        } catch (RuntimeException exception) {
            recordTool("get_reading_profile", "failure");
            log.warn("assistant tool get_reading_profile failed: {}", exception.getClass().getSimpleName());
            return ReadingProfileResult.unavailable();
        }
    }

    private AssistantReference toReference(WorkCard work) {
        return AssistantReference.work(work.id(), work.title(), work.author(), work.category(),
                work.description(), work.coverUrl());
    }

    private AssistantReference toReference(ProductView product) {
        List<ProductSkuReference> skus = product.skus().stream().map(this::toReference).toList();
        return AssistantReference.product(product.id(), product.title(), product.productType(),
                product.description(), product.coverUrl(), product.limited(), skus);
    }

    private ProductSkuReference toReference(SkuView sku) {
        return new ProductSkuReference(sku.id(), sku.skuName(), sku.priceCents(), sku.vipPriceCents());
    }

    private ReadingProfileResult.ReadingProfileItem toProfileItem(BookshelfItemView item) {
        return new ReadingProfileResult.ReadingProfileItem(item.workId(), item.title(), item.workType(),
                item.author(), item.category(), item.lastChapterNo(), item.progressPercent());
    }

    private ReadingProfileResult.ReadingProfileItem toProfileItem(ReadingProgressView item) {
        return new ReadingProfileResult.ReadingProfileItem(item.workId(), item.title(), item.workType(),
                item.author(), item.category(), item.chapterNo(), item.progressPercent());
    }

    private String authorization(ToolContext context) {
        Object value = context.getContext().get(ToolContextKeys.AUTHORIZATION);
        if (!(value instanceof String authorization) || authorization.isBlank()) {
            throw new IllegalStateException("Missing assistant tool authorization");
        }
        return authorization;
    }

    private AssistantReferenceCollector collector(ToolContext context) {
        Object value = context.getContext().get(ToolContextKeys.REFERENCES);
        if (!(value instanceof AssistantReferenceCollector collector)) {
            throw new IllegalStateException("Missing assistant reference collector");
        }
        return collector;
    }

    private String optional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 80 ? trimmed.substring(0, 80) : trimmed;
    }

    private void recordTool(String tool, String result) {
        Counter.builder("gray.assistant.tool.calls")
                .tag("tool", tool)
                .tag("result", result)
                .register(meterRegistry)
                .increment();
    }
}
