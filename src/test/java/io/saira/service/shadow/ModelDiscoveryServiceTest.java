package io.saira.service.shadow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import chat.giga.springai.api.chat.GigaChatApi;
import io.saira.config.ShadowProperties;
import io.saira.config.ShadowProperties.ProviderConfig;
import io.saira.dto.ModelInfo;
import io.saira.dto.ProviderInfo;

/** Unit-тесты сервиса обнаружения моделей от AI-провайдеров. */
@ExtendWith(MockitoExtension.class)
class ModelDiscoveryServiceTest {

    @Nested
    @DisplayName("listModels()")
    class ListModels {

        @Test
        @DisplayName("should return empty list when provider is disabled")
        void should_ReturnEmptyList_When_ProviderDisabled() {
            // given
            final ShadowProperties properties =
                    createProperties("openrouter", false, "https://api.openrouter.ai", "key");
            final ModelDiscoveryService service = new ModelDiscoveryService(properties, RestClient.create(), null);

            // when
            final List<ModelInfo> models = service.listModels("openrouter");

            // then
            assertThat(models).isEmpty();
        }

        @Test
        @DisplayName("should return empty list when provider not found")
        void should_ReturnEmptyList_When_ProviderNotFound() {
            // given
            final ShadowProperties properties = new ShadowProperties();
            final ModelDiscoveryService service = new ModelDiscoveryService(properties, RestClient.create(), null);

            // when
            final List<ModelInfo> models = service.listModels("unknown-provider");

            // then
            assertThat(models).isEmpty();
        }

        @Test
        @DisplayName("should return empty list for unknown provider name")
        void should_ReturnEmptyList_When_UnknownProviderName() {
            // given
            final ShadowProperties properties = createProperties("custom", true, "https://custom.api.com", "key");
            final ModelDiscoveryService service = new ModelDiscoveryService(properties, RestClient.create(), null);

            // when
            final List<ModelInfo> models = service.listModels("custom");

            // then
            assertThat(models).isEmpty();
        }

        @Test
        @DisplayName("should return empty list when OpenRouter API key is blank")
        void should_ReturnEmptyList_When_OpenRouterApiKeyBlank() {
            // given
            final ShadowProperties properties = createProperties("openrouter", true, "https://openrouter.ai/api", "");
            final ModelDiscoveryService service = new ModelDiscoveryService(properties, RestClient.create(), null);

            // when
            final List<ModelInfo> models = service.listModels("openrouter");

            // then
            assertThat(models).isEmpty();
        }

        @Test
        @DisplayName("should return empty list when GigaChatApi is null")
        void should_ReturnEmptyList_When_GigaChatApiNull() {
            // given — GigaChatApi not available (null passed via 2-arg constructor)
            final ShadowProperties properties = createProperties("gigachat", true, null, null);
            final ModelDiscoveryService service = new ModelDiscoveryService(properties, RestClient.create(), null);

            // when
            final List<ModelInfo> models = service.listModels("gigachat");

            // then
            assertThat(models).isEmpty();
        }

        @Test
        @DisplayName("should handle OpenRouter API error gracefully")
        @SuppressWarnings("unchecked")
        void should_ReturnEmptyList_When_OpenRouterApiThrows() {
            // given
            final ShadowProperties properties =
                    createProperties("openrouter", true, "https://openrouter.ai/api", "test-key");

            final RestClient restClient = mock(RestClient.class);
            final RestClient.RequestHeadersUriSpec uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
            final RestClient.RequestHeadersSpec headersSpec = mock(RestClient.RequestHeadersSpec.class);
            when(restClient.get()).thenReturn(uriSpec);
            when(uriSpec.uri(any(String.class))).thenReturn(headersSpec);
            when(headersSpec.header(any(), any())).thenReturn(headersSpec);
            when(headersSpec.retrieve()).thenThrow(new RestClientException("Connection refused"));

            final ModelDiscoveryService service = new ModelDiscoveryService(properties, restClient, null);

            // when
            final List<ModelInfo> models = service.listModels("openrouter");

            // then
            assertThat(models).isEmpty();
        }

        @Test
        @DisplayName("should handle GigaChat API error gracefully")
        void should_ReturnEmptyList_When_GigaChatApiThrows() {
            // given
            final ShadowProperties properties = createProperties("gigachat", true, null, null);

            final GigaChatApi gigaChatApi = mock(GigaChatApi.class);
            when(gigaChatApi.models()).thenThrow(new RestClientException("Timeout"));

            final ModelDiscoveryService service =
                    new ModelDiscoveryService(properties, RestClient.create(), gigaChatApi);

            // when
            final List<ModelInfo> models = service.listModels("gigachat");

            // then
            assertThat(models).isEmpty();
        }

        @Test
        @DisplayName("should return empty list when providers map is null")
        void should_ReturnEmptyList_When_ProvidersMapNull() {
            // given
            final ShadowProperties properties = new ShadowProperties();
            properties.setProviders(null);
            final ModelDiscoveryService service = new ModelDiscoveryService(properties, RestClient.create(), null);

            // when
            final List<ModelInfo> models = service.listModels("openrouter");

            // then
            assertThat(models).isEmpty();
        }
    }

    @Nested
    @DisplayName("getConnectedProviders()")
    class GetConnectedProviders {

        @Test
        @DisplayName("should return list of enabled providers")
        void should_ReturnEnabledProviders() {
            // given
            final ShadowProperties properties = new ShadowProperties();
            final Map<String, ProviderConfig> providers = new HashMap<>();

            final ProviderConfig openrouter = new ProviderConfig();
            openrouter.setEnabled(true);
            openrouter.setBaseUrl("https://openrouter.ai/api");
            providers.put("openrouter", openrouter);

            final ProviderConfig gigachat = new ProviderConfig();
            gigachat.setEnabled(false);
            gigachat.setBaseUrl("https://gigachat.api");
            providers.put("gigachat", gigachat);

            properties.setProviders(providers);
            final ModelDiscoveryService service = new ModelDiscoveryService(properties, RestClient.create(), null);

            // when
            final List<ProviderInfo> result = service.getConnectedProviders();

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getName()).isEqualTo("openrouter");
            assertThat(result.getFirst().isEnabled()).isTrue();
        }

        @Test
        @DisplayName("should return empty list when no providers configured")
        void should_ReturnEmptyList_When_NoProviders() {
            // given
            final ShadowProperties properties = new ShadowProperties();
            properties.setProviders(null);
            final ModelDiscoveryService service = new ModelDiscoveryService(properties, RestClient.create(), null);

            // when
            final List<ProviderInfo> result = service.getConnectedProviders();

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty list when all providers disabled")
        void should_ReturnEmptyList_When_AllProvidersDisabled() {
            // given
            final ShadowProperties properties = new ShadowProperties();
            final Map<String, ProviderConfig> providers = new HashMap<>();
            final ProviderConfig config = new ProviderConfig();
            config.setEnabled(false);
            providers.put("openrouter", config);
            properties.setProviders(providers);
            final ModelDiscoveryService service = new ModelDiscoveryService(properties, RestClient.create(), null);

            // when
            final List<ProviderInfo> result = service.getConnectedProviders();

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getConnectedProviderNames()")
    class GetConnectedProviderNames {

        @Test
        @DisplayName("should return names of enabled providers")
        void should_ReturnEnabledProviderNames() {
            // given
            final ShadowProperties properties = new ShadowProperties();
            final Map<String, ProviderConfig> providers = new HashMap<>();

            final ProviderConfig enabled1 = new ProviderConfig();
            enabled1.setEnabled(true);
            providers.put("openrouter", enabled1);

            final ProviderConfig enabled2 = new ProviderConfig();
            enabled2.setEnabled(true);
            providers.put("gigachat", enabled2);

            final ProviderConfig disabled = new ProviderConfig();
            disabled.setEnabled(false);
            providers.put("disabled-provider", disabled);

            properties.setProviders(providers);
            final ModelDiscoveryService service = new ModelDiscoveryService(properties, RestClient.create(), null);

            // when
            final List<String> names = service.getConnectedProviderNames();

            // then
            assertThat(names).containsExactlyInAnyOrder("openrouter", "gigachat");
        }

        @Test
        @DisplayName("should return empty list when providers null")
        void should_ReturnEmptyList_When_ProvidersNull() {
            // given
            final ShadowProperties properties = new ShadowProperties();
            properties.setProviders(null);
            final ModelDiscoveryService service = new ModelDiscoveryService(properties, RestClient.create(), null);

            // when
            final List<String> names = service.getConnectedProviderNames();

            // then
            assertThat(names).isEmpty();
        }
    }

    @Nested
    @DisplayName("isProviderConnected()")
    class IsProviderConnected {

        @Test
        @DisplayName("should return true when provider is enabled")
        void should_ReturnTrue_When_ProviderEnabled() {
            // given
            final ShadowProperties properties = createProperties("openrouter", true, "https://api.com", "key");
            final ModelDiscoveryService service = new ModelDiscoveryService(properties, RestClient.create(), null);

            // when & then
            assertThat(service.isProviderConnected("openrouter")).isTrue();
        }

        @Test
        @DisplayName("should return false when provider is disabled")
        void should_ReturnFalse_When_ProviderDisabled() {
            // given
            final ShadowProperties properties = createProperties("openrouter", false, "https://api.com", "key");
            final ModelDiscoveryService service = new ModelDiscoveryService(properties, RestClient.create(), null);

            // when & then
            assertThat(service.isProviderConnected("openrouter")).isFalse();
        }

        @Test
        @DisplayName("should return false when provider not found")
        void should_ReturnFalse_When_ProviderNotFound() {
            // given
            final ShadowProperties properties = new ShadowProperties();
            final ModelDiscoveryService service = new ModelDiscoveryService(properties, RestClient.create(), null);

            // when & then
            assertThat(service.isProviderConnected("unknown")).isFalse();
        }
    }

    /**
     * Создаёт ShadowProperties с одним провайдером.
     *
     * @param name    имя провайдера
     * @param enabled включен ли провайдер
     * @param baseUrl базовый URL
     * @param apiKey  API ключ
     * @return конфигурация с одним провайдером
     */
    private ShadowProperties createProperties(
            final String name, final boolean enabled, final String baseUrl, final String apiKey) {
        final ShadowProperties properties = new ShadowProperties();
        final Map<String, ProviderConfig> providers = new HashMap<>();
        final ProviderConfig config = new ProviderConfig();
        config.setEnabled(enabled);
        config.setBaseUrl(baseUrl);
        config.setApiKey(apiKey);
        providers.put(name, config);
        properties.setProviders(providers);
        return properties;
    }
}
