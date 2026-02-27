package io.saira.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import io.saira.entity.AgentTrace;

/** Репозиторий для работы с трейсами агентов. */
@Repository
public interface AgentTraceRepository extends JpaRepository<AgentTrace, Long> {

    /** Найти трейс по OpenTelemetry trace ID. */
    Optional<AgentTrace> findByTraceId(String traceId);

    /** Количество трейсов, созданных после указанного времени. */
    long countByCreatedAtAfter(Instant after);

    /** Получить все трейсы с пагинацией, отсортированные по времени начала (desc). */
    Page<AgentTrace> findAllByOrderByStartedAtDesc(Pageable pageable);

    /** Получить уникальные имена сервисов. */
    @Query("SELECT DISTINCT t.serviceName FROM AgentTrace t ORDER BY t.serviceName")
    List<String> findDistinctServiceNames();

    /** Найти трейсы по списку trace ID. */
    List<AgentTrace> findByTraceIdIn(List<String> traceIds);
}
