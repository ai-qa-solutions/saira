package io.saira.service.shadow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

/** Unit-тесты реестра ChatClient для shadow-вызовов. */
@ExtendWith(MockitoExtension.class)
class ShadowChatClientRegistryTest {

    private ShadowChatClientRegistry registry;

    @Mock
    private ChatClient chatClient1;

    @Mock
    private ChatClient chatClient2;

    @BeforeEach
    void setUp() {
        registry = new ShadowChatClientRegistry();
    }

    @Nested
    @DisplayName("registerClient()")
    class RegisterClient {

        @Test
        @DisplayName("should register client and make it available via getClient")
        void should_RegisterClient_When_ValidModelId() {
            // when
            registry.registerClient("gpt-4", chatClient1);

            // then
            final Optional<ChatClient> result = registry.getClient("gpt-4");
            assertThat(result).isPresent();
            assertThat(result.get()).isSameAs(chatClient1);
        }

        @Test
        @DisplayName("should overwrite existing client for same modelId")
        void should_OverwriteClient_When_SameModelIdRegistered() {
            // given
            registry.registerClient("gpt-4", chatClient1);

            // when
            registry.registerClient("gpt-4", chatClient2);

            // then
            final Optional<ChatClient> result = registry.getClient("gpt-4");
            assertThat(result).isPresent();
            assertThat(result.get()).isSameAs(chatClient2);
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when modelId is blank")
        void should_ThrowException_When_ModelIdBlank() {
            // when & then
            assertThatThrownBy(() -> registry.registerClient("", chatClient1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when modelId is null")
        void should_ThrowException_When_ModelIdNull() {
            // when & then
            assertThatThrownBy(() -> registry.registerClient(null, chatClient1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when client is null")
        void should_ThrowException_When_ClientNull() {
            // when & then
            assertThatThrownBy(() -> registry.registerClient("gpt-4", null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("getClient()")
    class GetClient {

        @Test
        @DisplayName("should return empty when model not registered")
        void should_ReturnEmpty_When_ModelNotRegistered() {
            // when
            final Optional<ChatClient> result = registry.getClient("unknown-model");

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return client when model is registered")
        void should_ReturnClient_When_ModelRegistered() {
            // given
            registry.registerClient("claude-3", chatClient1);

            // when
            final Optional<ChatClient> result = registry.getClient("claude-3");

            // then
            assertThat(result).isPresent();
            assertThat(result.get()).isSameAs(chatClient1);
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when modelId is blank")
        void should_ThrowException_When_ModelIdBlank() {
            // when & then
            assertThatThrownBy(() -> registry.getClient("")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("hasClient()")
    class HasClient {

        @Test
        @DisplayName("should return true when client exists")
        void should_ReturnTrue_When_ClientExists() {
            // given
            registry.registerClient("gpt-4", chatClient1);

            // when & then
            assertThat(registry.hasClient("gpt-4")).isTrue();
        }

        @Test
        @DisplayName("should return false when client does not exist")
        void should_ReturnFalse_When_ClientDoesNotExist() {
            // when & then
            assertThat(registry.hasClient("unknown")).isFalse();
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when modelId is blank")
        void should_ThrowException_When_ModelIdBlank() {
            // when & then
            assertThatThrownBy(() -> registry.hasClient("")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("unregisterClient()")
    class UnregisterClient {

        @Test
        @DisplayName("should remove registered client")
        void should_RemoveClient_When_Exists() {
            // given
            registry.registerClient("gpt-4", chatClient1);

            // when
            registry.unregisterClient("gpt-4");

            // then
            assertThat(registry.hasClient("gpt-4")).isFalse();
            assertThat(registry.getClient("gpt-4")).isEmpty();
        }

        @Test
        @DisplayName("should not throw when unregistering non-existent client")
        void should_NotThrow_When_ClientDoesNotExist() {
            // when & then (no exception expected)
            registry.unregisterClient("non-existent");
            assertThat(registry.hasClient("non-existent")).isFalse();
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when modelId is blank")
        void should_ThrowException_When_ModelIdBlank() {
            // when & then
            assertThatThrownBy(() -> registry.unregisterClient("")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("getAvailableModelIds()")
    class GetAvailableModelIds {

        @Test
        @DisplayName("should return empty set when no clients registered")
        void should_ReturnEmptySet_When_NoClientsRegistered() {
            // when
            final Set<String> modelIds = registry.getAvailableModelIds();

            // then
            assertThat(modelIds).isEmpty();
        }

        @Test
        @DisplayName("should return all registered model IDs")
        void should_ReturnAllModelIds_When_ClientsRegistered() {
            // given
            registry.registerClient("gpt-4", chatClient1);
            registry.registerClient("claude-3", chatClient2);

            // when
            final Set<String> modelIds = registry.getAvailableModelIds();

            // then
            assertThat(modelIds).containsExactlyInAnyOrder("gpt-4", "claude-3");
        }

        @Test
        @DisplayName("should not include unregistered model IDs")
        void should_NotIncludeUnregistered_When_ClientRemoved() {
            // given
            registry.registerClient("gpt-4", chatClient1);
            registry.registerClient("claude-3", chatClient2);
            registry.unregisterClient("gpt-4");

            // when
            final Set<String> modelIds = registry.getAvailableModelIds();

            // then
            assertThat(modelIds).containsExactly("claude-3");
        }
    }

    @Nested
    @DisplayName("Concurrent operations")
    class ConcurrentOperations {

        @Test
        @DisplayName("should safely handle concurrent register and unregister")
        void should_HandleConcurrentRegisterAndUnregister() throws InterruptedException {
            // given
            final int threadCount = 20;
            final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            final CountDownLatch latch = new CountDownLatch(threadCount);

            // when - concurrent register and unregister
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        final String modelId = "model-" + index;
                        registry.registerClient(modelId, chatClient1);
                        registry.hasClient(modelId);
                        registry.getClient(modelId);
                        if (index % 2 == 0) {
                            registry.unregisterClient(modelId);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // then - no exceptions, finishes within timeout
            final boolean completed = latch.await(5, TimeUnit.SECONDS);
            executor.shutdown();
            assertThat(completed).isTrue();
            assertThat(registry.getAvailableModelIds()).isNotNull();
        }
    }
}
