package io.saira.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import io.saira.entity.ShadowResult;

/** Репозиторий для работы с результатами shadow-вызовов. */
@Repository
public interface ShadowResultRepository extends JpaRepository<ShadowResult, Long> {

    /** Найти результаты по ID оригинального span-а. */
    List<ShadowResult> findBySourceSpanId(String sourceSpanId);

    /** Найти результаты по ID shadow-конфигурации. */
    List<ShadowResult> findByShadowConfigId(Long shadowConfigId);

    /** Подсчитать количество результатов для shadow-конфигурации. */
    long countByShadowConfigId(Long shadowConfigId);

    /** Найти результаты по списку ID span-ов. */
    List<ShadowResult> findBySourceSpanIdIn(List<String> sourceSpanIds);

    /** Найти shadow-результаты, JOIN с agent_span для получения трейсов (пагинация). */
    @Query(
            """
            SELECT sr FROM ShadowResult sr
            WHERE sr.sourceSpanId IN (
                SELECT s.spanId FROM AgentSpan s
            )
            ORDER BY sr.executedAt DESC
            """)
    Page<ShadowResult> findAllWithTracesPaginated(Pageable pageable);
}
