package io.saira.service.ingest;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import io.saira.config.IngestProperties;
import lombok.extern.slf4j.Slf4j;

/**
 * Двухуровневый фильтр span-ов.
 *
 * <p>Level 1: только HTTP client spans (span.kind=client + наличие http.url/method).
 *
 * <p>Level 2: URL whitelist из saira.ingest.url-patterns (пустой список = принимать все).
 */
@Slf4j
@Service
public class SpanFilter {

    /** URL-паттерны для whitelist фильтрации. */
    private final List<String> urlPatterns;

    /**
     * Создаёт фильтр на основе конфигурации ingestion.
     *
     * @param properties конфигурация ingestion pipeline
     */
    public SpanFilter(final IngestProperties properties) {
        this.urlPatterns = properties.getUrlPatterns();
        log.info("SpanFilter initialized with {} URL patterns: {}", urlPatterns.size(), urlPatterns);
    }

    /**
     * Проверяет, должен ли span быть сохранён.
     *
     * @param attributes карта атрибутов span (из OTel tags)
     * @return true если span проходит оба уровня фильтрации
     */
    public boolean shouldAccept(final Map<String, String> attributes) {
        // Level 1: HTTP client only
        final String spanKind = attributes.getOrDefault("span.kind", "");
        if (!"client".equals(spanKind)) {
            log.trace("Rejected: span.kind={}", spanKind);
            return false;
        }

        final String httpUrl = attributes.get("http.url");
        if (httpUrl == null && !attributes.containsKey("method")) {
            return false;
        }

        // Level 2: URL pattern whitelist (empty list = accept all)
        if (urlPatterns.isEmpty()) {
            return true;
        }

        if (httpUrl == null) {
            return false;
        }

        return urlPatterns.stream().anyMatch(httpUrl::contains);
    }
}
