package io.saira;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Тест загрузки контекста приложения. */
@SpringBootTest
@ActiveProfiles("test")
class SairaApplicationTest {

    @Test
    void contextLoads() {
        // Проверяет, что Spring-контекст поднимается без ошибок
    }
}
