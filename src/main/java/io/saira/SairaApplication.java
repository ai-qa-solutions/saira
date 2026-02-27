package io.saira;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;

import io.saira.config.IngestProperties;
import io.saira.config.ShadowProperties;

/** Точка входа приложения SAIRA. */
@SpringBootApplication(
        exclude = {
            org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration.class,
            org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration.class,
            org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration.class,
            org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration.class,
            org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration.class,
            org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration.class
        })
@EnableCaching
@EnableConfigurationProperties({IngestProperties.class, ShadowProperties.class})
public class SairaApplication {

    public static void main(final String[] args) {
        SpringApplication.run(SairaApplication.class, args);
    }
}
