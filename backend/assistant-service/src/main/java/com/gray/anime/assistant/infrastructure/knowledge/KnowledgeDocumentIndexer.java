package com.gray.anime.assistant.infrastructure.knowledge;

import com.gray.anime.assistant.config.AssistantProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "gray.ai.enabled", havingValue = "true")
public class KnowledgeDocumentIndexer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeDocumentIndexer.class);
    private static final String MANIFEST_KEY = "assistant:knowledge:manifest:v1";
    private static final int CHUNK_CHARACTERS = 1200;
    private static final int CHUNK_OVERLAP = 150;

    private final ResourcePatternResolver resources;
    private final VectorStore vectorStore;
    private final StringRedisTemplate redisTemplate;
    private final AssistantProperties properties;

    public KnowledgeDocumentIndexer(ResourcePatternResolver resources, VectorStore vectorStore,
                                    StringRedisTemplate redisTemplate, AssistantProperties properties) {
        this.resources = resources;
        this.vectorStore = vectorStore;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (!properties.available()) {
            log.info("assistant knowledge indexing skipped because AI is disabled");
            return;
        }
        Resource[] found = resources.getResources("classpath*:knowledge/*.md");
        Arrays.sort(found, Comparator.comparing(resource -> resource.getFilename() == null ? "" : resource.getFilename()));
        Map<Object, Object> previous = redisTemplate.opsForHash().entries(MANIFEST_KEY);
        Set<String> currentSources = new HashSet<>();

        for (Resource resource : found) {
            index(resource, previous, currentSources);
        }
        removeDeleted(previous, currentSources);
        log.info("assistant knowledge index ready with {} sources", currentSources.size());
    }

    private void index(Resource resource, Map<Object, Object> previous, Set<String> currentSources) throws IOException {
        String source = resource.getFilename();
        if (source == null) {
            return;
        }
        currentSources.add(source);
        String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
        String hash = sha256(content);
        ManifestEntry oldEntry = ManifestEntry.parse(previous.get(source));
        if (oldEntry != null && oldEntry.hash().equals(hash)) {
            return;
        }

        List<Document> documents = toDocuments(source, content, hash);
        if (oldEntry != null && !oldEntry.documentIds().isEmpty()) {
            vectorStore.delete(oldEntry.documentIds());
        }
        if (!documents.isEmpty()) {
            vectorStore.add(documents);
        }
        List<String> ids = documents.stream().map(Document::getId).toList();
        redisTemplate.opsForHash().put(MANIFEST_KEY, source, ManifestEntry.format(hash, ids));
    }

    private void removeDeleted(Map<Object, Object> previous, Set<String> currentSources) {
        for (Map.Entry<Object, Object> entry : previous.entrySet()) {
            String source = String.valueOf(entry.getKey());
            if (currentSources.contains(source)) {
                continue;
            }
            ManifestEntry manifest = ManifestEntry.parse(entry.getValue());
            if (manifest != null && !manifest.documentIds().isEmpty()) {
                vectorStore.delete(manifest.documentIds());
            }
            redisTemplate.opsForHash().delete(MANIFEST_KEY, source);
        }
    }

    List<Document> toDocuments(String source, String content, String hash) {
        String title = Arrays.stream(content.split("\n"))
                .filter(line -> line.startsWith("# "))
                .map(line -> line.substring(2).trim())
                .findFirst()
                .orElse(source);
        List<String> paragraphs = Arrays.stream(content.split("\n\\s*\n"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
        List<String> chunks = chunk(paragraphs);
        List<Document> documents = new ArrayList<>();
        for (int index = 0; index < chunks.size(); index++) {
            String id = UUID.nameUUIDFromBytes((source + ":" + index).getBytes(StandardCharsets.UTF_8)).toString();
            documents.add(Document.builder()
                    .id(id)
                    .text(chunks.get(index))
                    .metadata("source", source)
                    .metadata("title", title)
                    .metadata("contentHash", hash)
                    .metadata("chunk", index)
                    .build());
        }
        return documents;
    }

    private List<String> chunk(List<String> paragraphs) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (!current.isEmpty() && current.length() + paragraph.length() + 2 > CHUNK_CHARACTERS) {
                chunks.add(current.toString());
                String overlap = current.substring(Math.max(0, current.length() - CHUNK_OVERLAP));
                current = new StringBuilder(overlap).append("\n\n");
            }
            if (!current.isEmpty()) {
                current.append("\n\n");
            }
            current.append(paragraph);
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }
        return chunks;
    }

    private String sha256(String content) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record ManifestEntry(String hash, List<String> documentIds) {
        static ManifestEntry parse(Object raw) {
            if (raw == null) {
                return null;
            }
            String value = String.valueOf(raw);
            int separator = value.indexOf('|');
            if (separator < 0) {
                return null;
            }
            String ids = value.substring(separator + 1);
            return new ManifestEntry(value.substring(0, separator),
                    ids.isBlank() ? List.of() : List.of(ids.split(",")));
        }

        static String format(String hash, List<String> documentIds) {
            return hash + "|" + String.join(",", documentIds);
        }
    }
}
