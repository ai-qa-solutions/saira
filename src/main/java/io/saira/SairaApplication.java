package io.saira;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/** Точка входа приложения SAIRA. */
@SpringBootApplication
@EnableCaching
public class SairaApplication {

    public static void main(final String[] args) {
        SpringApplication.run(SairaApplication.class, args);
    }
}
