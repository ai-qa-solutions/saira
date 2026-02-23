package io.saira.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Конфигурация статических ресурсов для SPA. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** Кэш статики в секундах (1 час). */
    private static final int STATIC_CACHE_SECONDS = 3600;

    /** Кэш ассетов в секундах (1 год). */
    private static final int ASSETS_CACHE_SECONDS = 31536000;

    @Override
    public void addResourceHandlers(final ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCachePeriod(ASSETS_CACHE_SECONDS);

        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(STATIC_CACHE_SECONDS);
    }
}
