package io.saira.service.shadow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

/** Unit-тесты реестра ChatModel по провайдерам. */
class ShadowModelRegistryTest {

    private ShadowModelRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ShadowModelRegistry();
    }

    @Nested
    @DisplayName("registerModel()")
    class RegisterModel {

        @Test
        @DisplayName("should store model by providerId")
        void should_StoreModel_When_ValidParams() {
            // given
            final ChatModel model = mock(ChatModel.class);

            // when
            registry.registerModel("openrouter", model);

            // then
            assertThat(registry.hasProvider("openrouter")).isTrue();
            assertThat(registry.getModel("openrouter")).contains(model);
        }

        @Test
        @DisplayName("should overwrite existing model for same provider")
        void should_OverwriteModel_When_SameProvider() {
            // given
            final ChatModel firstModel = mock(ChatModel.class);
            final ChatModel secondModel = mock(ChatModel.class);
            registry.registerModel("openrouter", firstModel);

            // when
            registry.registerModel("openrouter", secondModel);

            // then
            assertThat(registry.getModel("openrouter")).contains(secondModel);
            assertThat(registry.getAvailableProviders()).hasSize(1);
        }

        @Test
        @DisplayName("should throw when providerId is blank")
        void should_Throw_When_ProviderIdBlank() {
            // given
            final ChatModel model = mock(ChatModel.class);

            // when & then
            assertThatThrownBy(() -> registry.registerModel("", model))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("providerId cannot be blank");

            assertThatThrownBy(() -> registry.registerModel("   ", model))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("providerId cannot be blank");

            assertThatThrownBy(() -> registry.registerModel(null, model))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("providerId cannot be blank");
        }

        @Test
        @DisplayName("should throw when model is null")
        void should_Throw_When_ModelNull() {
            // when & then
            assertThatThrownBy(() -> registry.registerModel("openrouter", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("model cannot be null");
        }
    }

    @Nested
    @DisplayName("getModel()")
    class GetModel {

        @Test
        @DisplayName("should return model when registered")
        void should_ReturnModel_When_Registered() {
            // given
            final ChatModel model = mock(ChatModel.class);
            registry.registerModel("gigachat", model);

            // when
            final Optional<ChatModel> result = registry.getModel("gigachat");

            // then
            assertThat(result).isPresent().contains(model);
        }

        @Test
        @DisplayName("should return empty when not registered")
        void should_ReturnEmpty_When_NotRegistered() {
            // when
            final Optional<ChatModel> result = registry.getModel("unknown-provider");

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should throw when providerId is blank")
        void should_Throw_When_ProviderIdBlank() {
            // when & then
            assertThatThrownBy(() -> registry.getModel(""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("providerId cannot be blank");
        }
    }

    @Nested
    @DisplayName("hasProvider()")
    class HasProvider {

        @Test
        @DisplayName("should return true when registered")
        void should_ReturnTrue_When_Registered() {
            // given
            final ChatModel model = mock(ChatModel.class);
            registry.registerModel("openrouter", model);

            // when & then
            assertThat(registry.hasProvider("openrouter")).isTrue();
        }

        @Test
        @DisplayName("should return false when not registered")
        void should_ReturnFalse_When_NotRegistered() {
            // when & then
            assertThat(registry.hasProvider("nonexistent")).isFalse();
        }

        @Test
        @DisplayName("should throw when providerId is blank")
        void should_Throw_When_ProviderIdBlank() {
            // when & then
            assertThatThrownBy(() -> registry.hasProvider(""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("providerId cannot be blank");
        }
    }

    @Nested
    @DisplayName("getAvailableProviders()")
    class GetAvailableProviders {

        @Test
        @DisplayName("should return all registered providers")
        void should_ReturnAllProviders_When_MultipleRegistered() {
            // given
            final ChatModel model1 = mock(ChatModel.class);
            final ChatModel model2 = mock(ChatModel.class);
            final ChatModel model3 = mock(ChatModel.class);
            registry.registerModel("openrouter", model1);
            registry.registerModel("gigachat", model2);
            registry.registerModel("anthropic", model3);

            // when
            final Set<String> providers = registry.getAvailableProviders();

            // then
            assertThat(providers).containsExactlyInAnyOrder("openrouter", "gigachat", "anthropic");
        }

        @Test
        @DisplayName("should return empty set when no providers")
        void should_ReturnEmptySet_When_NoProviders() {
            // when
            final Set<String> providers = registry.getAvailableProviders();

            // then
            assertThat(providers).isEmpty();
        }

        @Test
        @DisplayName("should return unmodifiable set")
        void should_ReturnUnmodifiableSet() {
            // given
            final ChatModel model = mock(ChatModel.class);
            registry.registerModel("openrouter", model);

            // when
            final Set<String> providers = registry.getAvailableProviders();

            // then
            assertThatThrownBy(() -> providers.add("hacked")).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("concurrent operations")
    class ConcurrentOperations {

        @Test
        @DisplayName("should handle concurrent register and get")
        void should_HandleConcurrentAccess() throws InterruptedException {
            // given
            final int threadCount = 20;
            final CountDownLatch startLatch = new CountDownLatch(1);
            final CountDownLatch doneLatch = new CountDownLatch(threadCount);
            final ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            // when — 20 threads registering and reading simultaneously
            for (int i = 0; i < threadCount; i++) {
                final String providerId = "provider-" + i;
                final ChatModel model = mock(ChatModel.class);
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        registry.registerModel(providerId, model);
                        registry.getModel(providerId);
                        registry.hasProvider(providerId);
                        registry.getAvailableProviders();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
            executor.shutdown();

            // then
            assertThat(completed).isTrue();
            assertThat(registry.getAvailableProviders()).hasSize(threadCount);

            for (int i = 0; i < threadCount; i++) {
                assertThat(registry.hasProvider("provider-" + i)).isTrue();
                assertThat(registry.getModel("provider-" + i)).isPresent();
            }
        }
    }
}
