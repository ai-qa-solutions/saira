package io.saira.service.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.saira.config.IngestProperties;

/** Unit-тесты двухуровневого фильтра span-ов. */
class SpanFilterTest {

    /** URL-паттерны для стандартных тестов. */
    private static final List<String> DEFAULT_URL_PATTERNS = List.of("/completions", "/embeddings", "/mcp");

    /**
     * Создаёт SpanFilter с указанными URL-паттернами.
     *
     * @param urlPatterns список URL-паттернов
     * @return настроенный SpanFilter
     */
    private SpanFilter createFilter(final List<String> urlPatterns) {
        final IngestProperties properties = new IngestProperties();
        properties.setUrlPatterns(urlPatterns);
        return new SpanFilter(properties);
    }

    /**
     * Создаёт SpanFilter со стандартными URL-паттернами.
     *
     * @return настроенный SpanFilter
     */
    private SpanFilter createDefaultFilter() {
        return createFilter(DEFAULT_URL_PATTERNS);
    }

    /**
     * Создаёт изменяемую карту атрибутов из пар ключ-значение.
     *
     * @param entries пары ключ-значение
     * @return карта атрибутов
     */
    private Map<String, String> attrs(final String... entries) {
        final Map<String, String> map = new HashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            map.put(entries[i], entries[i + 1]);
        }
        return map;
    }

    @Test
    @DisplayName("Принимает HTTP POST client span, если URL содержит /completions")
    void should_AcceptHttpPostClient_When_UrlMatchesPattern() {
        // given
        final SpanFilter filter = createDefaultFilter();
        final Map<String, String> attributes =
                attrs("span.kind", "client", "method", "POST", "http.url", "https://api.openai.com/v1/completions");

        // when
        final boolean result = filter.shouldAccept(attributes);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Принимает HTTP GET client span, если URL содержит /mcp")
    void should_AcceptHttpGetClient_When_UrlMatchesPattern() {
        // given
        final SpanFilter filter = createDefaultFilter();
        final Map<String, String> attributes =
                attrs("span.kind", "client", "method", "GET", "http.url", "https://mcp-server.example.com/mcp/tools");

        // when
        final boolean result = filter.shouldAccept(attributes);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Принимает HTTP client span, если URL содержит /embeddings")
    void should_AcceptHttpClient_When_UrlMatchesEmbeddingsPattern() {
        // given
        final SpanFilter filter = createDefaultFilter();
        final Map<String, String> attributes = attrs(
                "span.kind", "client",
                "method", "POST",
                "http.url", "https://api.openai.com/v1/embeddings");

        // when
        final boolean result = filter.shouldAccept(attributes);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Принимает любой HTTP client span, если список URL-паттернов пуст")
    void should_AcceptAllHttpClient_When_UrlPatternsEmpty() {
        // given
        final SpanFilter filter = createFilter(List.of());
        final Map<String, String> attributes = attrs(
                "span.kind", "client",
                "method", "POST",
                "http.url", "https://some-random-service.example.com/anything");

        // when
        final boolean result = filter.shouldAccept(attributes);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Отклоняет HTTP client span, если URL не содержит ни одного паттерна")
    void should_RejectHttpClient_When_UrlNotInPatterns() {
        // given
        final SpanFilter filter = createDefaultFilter();
        final Map<String, String> attributes = attrs(
                "span.kind", "client",
                "method", "POST",
                "http.url", "https://auth.example.com/oauth/token");

        // when
        final boolean result = filter.shouldAccept(attributes);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Отклоняет JDBC client span без http.url и method")
    void should_RejectJdbcClient_When_NoHttpUrlOrMethod() {
        // given
        final SpanFilter filter = createDefaultFilter();
        final Map<String, String> attributes = attrs("span.kind", "client", "jdbc.query[0]", "SELECT 1 FROM dual");

        // when
        final boolean result = filter.shouldAccept(attributes);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Отклоняет server span")
    void should_RejectServerSpan() {
        // given
        final SpanFilter filter = createDefaultFilter();
        final Map<String, String> attributes = attrs(
                "span.kind", "server",
                "method", "POST",
                "http.url", "https://api.openai.com/v1/completions");

        // when
        final boolean result = filter.shouldAccept(attributes);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Отклоняет internal span")
    void should_RejectInternalSpan() {
        // given
        final SpanFilter filter = createDefaultFilter();
        final Map<String, String> attributes = attrs("span.kind", "internal", "http.url", "https://example.com/v1/mcp");

        // when
        final boolean result = filter.shouldAccept(attributes);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Отклоняет span без атрибута span.kind")
    void should_RejectSpan_When_NoSpanKind() {
        // given
        final SpanFilter filter = createDefaultFilter();
        final Map<String, String> attributes =
                attrs("method", "POST", "http.url", "https://api.openai.com/v1/completions");

        // when
        final boolean result = filter.shouldAccept(attributes);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Отклоняет HTTP client span, если http.url=null при непустых urlPatterns")
    void should_RejectHttpClient_When_UrlIsNull() {
        // given
        final SpanFilter filter = createDefaultFilter();
        final Map<String, String> attributes = attrs("span.kind", "client", "method", "POST");

        // when
        final boolean result = filter.shouldAccept(attributes);

        // then
        assertThat(result).isFalse();
    }
}
