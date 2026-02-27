package io.saira.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.saira.entity.AgentSpan;

/** Репозиторий для работы со span-ами агентов. */
@Repository
public interface AgentSpanRepository extends JpaRepository<AgentSpan, Long> {

    /** Проверить существование span по OpenTelemetry span ID. */
    boolean existsBySpanId(String spanId);

    /** Найти span по OpenTelemetry span ID. */
    Optional<AgentSpan> findBySpanId(String spanId);

    /** Количество span-ов по trace ID. */
    long countByTraceId(String traceId);

    /** Количество span-ов, начатых после указанного времени. */
    long countByStartedAtAfter(Instant after);

    /** Получить все span-ы трейса, отсортированные по времени начала. */
    List<AgentSpan> findByTraceIdOrderByStartedAtAsc(String traceId);

    /** Найти span-ы по списку span ID. */
    List<AgentSpan> findBySpanIdIn(List<String> spanIds);
}
