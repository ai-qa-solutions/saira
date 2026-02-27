package io.saira.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Конфигурация ingestion pipeline.
 *
 * <p>Example application.yml:
 *
 * <pre>{@code
 * saira:
 *   ingest:
 *     url-patterns:
 *       - /completions
 *       - /embeddings
 *       - /mcp
 * }</pre>
 */
@Data
@ConfigurationProperties(prefix = "saira.ingest")
public class IngestProperties {

    /** Список URL-паттернов для whitelist фильтрации HTTP client spans. Пустой список = принимать все. */
    private List<String> urlPatterns = new ArrayList<>();
}
