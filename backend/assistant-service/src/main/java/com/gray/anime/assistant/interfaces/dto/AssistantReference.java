package com.gray.anime.assistant.interfaces.dto;

import java.util.List;

public record AssistantReference(
        String kind,
        String key,
        Long id,
        String title,
        String subtitle,
        String description,
        String coverUrl,
        boolean limited,
        List<ProductSkuReference> skus,
        String source
) {
    public static AssistantReference work(Long id, String title, String author, String category,
                                          String description, String coverUrl) {
        String subtitle = author == null || author.isBlank() ? category : author + " · " + category;
        return new AssistantReference("WORK", "WORK:" + id, id, title, subtitle, description, coverUrl,
                false, List.of(), null);
    }

    public static AssistantReference product(Long id, String title, String productType, String description,
                                             String coverUrl, boolean limited, List<ProductSkuReference> skus) {
        return new AssistantReference("PRODUCT", "PRODUCT:" + id, id, title, productType, description,
                coverUrl, limited, List.copyOf(skus), null);
    }

    public static AssistantReference faq(String title, String source) {
        return new AssistantReference("FAQ", "FAQ:" + source, null, title, null, null, null,
                false, List.of(), source);
    }
}
