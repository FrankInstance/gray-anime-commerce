package com.gray.anime.assistant.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "gray.ai.enabled", havingValue = "true")
public class AssistantVectorStoreConfiguration {
    @Bean(destroyMethod = "close")
    QdrantClient qdrantClient(QdrantProperties properties) {
        QdrantGrpcClient grpcClient = QdrantGrpcClient
                .newBuilder(properties.host(), properties.port(), properties.useTls())
                .build();
        return new QdrantClient(grpcClient);
    }

    @Bean
    VectorStore vectorStore(QdrantClient client, EmbeddingModel embeddingModel, QdrantProperties properties) {
        return QdrantVectorStore.builder(client, embeddingModel)
                .collectionName(properties.collectionName())
                .initializeSchema(true)
                .build();
    }
}
