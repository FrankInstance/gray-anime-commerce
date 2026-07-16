package com.gray.anime.assistant.application;

import com.gray.anime.assistant.infrastructure.client.ContentClient;
import com.gray.anime.assistant.infrastructure.client.ShopClient;
import com.gray.anime.assistant.infrastructure.client.dto.ProductView;
import com.gray.anime.assistant.infrastructure.client.dto.SkuView;
import com.gray.anime.assistant.infrastructure.client.dto.WorkCard;
import com.gray.anime.common.api.ApiResponse;
import com.gray.anime.common.api.PageResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogToolsTest {
    private final ContentClient contentClient = mock(ContentClient.class);
    private final ShopClient shopClient = mock(ShopClient.class);
    private final CatalogTools tools = new CatalogTools(contentClient, shopClient, new SimpleMeterRegistry());
    private final AssistantReferenceCollector collector = new AssistantReferenceCollector();
    private final ToolContext context = new ToolContext(Map.of(
            ToolContextKeys.AUTHORIZATION, "Bearer verified-token",
            ToolContextKeys.REFERENCES, collector));

    @Test
    void workSearchReturnsOnlyValidatedCatalogItems() {
        WorkCard work = new WorkCard(7L, "夜行电台", "NOVEL", "Gray", "都市",
                "午夜电台的故事", "/covers/7.jpg", 88);
        when(contentClient.works(1, 5, "NOVEL", null, "夜行", "Bearer verified-token"))
                .thenReturn(ApiResponse.ok(new PageResult<>(List.of(work), 1, 5, 1)));

        CatalogSearchResult result = tools.searchWorks("夜行", "NOVEL", "", context);

        assertThat(result.available()).isTrue();
        assertThat(result.items()).singleElement().satisfies(reference -> {
            assertThat(reference.kind()).isEqualTo("WORK");
            assertThat(reference.id()).isEqualTo(7L);
            assertThat(reference.title()).isEqualTo("夜行电台");
        });
        assertThat(collector.snapshot()).containsExactlyElementsOf(result.items());
        verify(contentClient).works(1, 5, "NOVEL", null, "夜行", "Bearer verified-token");
    }

    @Test
    void productSearchPreservesRealSkuPrices() {
        ProductView product = new ProductView(3L, "雨港亚克力立牌", "GOODS", "限定立牌",
                "/covers/3.jpg", true, null, List.of(new SkuView(31L, "标准款", 3900, 3510)));
        when(shopClient.products(1, 5, null, "雨港", "Bearer verified-token"))
                .thenReturn(ApiResponse.ok(new PageResult<>(List.of(product), 1, 5, 1)));

        CatalogSearchResult result = tools.searchProducts("雨港", "", context);

        assertThat(result.items()).singleElement().satisfies(reference -> {
            assertThat(reference.kind()).isEqualTo("PRODUCT");
            assertThat(reference.limited()).isTrue();
            assertThat(reference.skus()).singleElement().satisfies(sku -> {
                assertThat(sku.priceCents()).isEqualTo(3900);
                assertThat(sku.vipPriceCents()).isEqualTo(3510);
            });
        });
    }
}
