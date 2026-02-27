package io.saira.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.saira.entity.ShadowConfig;

/** Репозиторий для работы с конфигурациями shadow-правил. */
@Repository
public interface ShadowConfigRepository extends JpaRepository<ShadowConfig, Long> {

    /** Найти конфигурации по имени сервиса и статусу. */
    List<ShadowConfig> findByServiceNameAndStatus(String serviceName, String status);

    /** Найти все конфигурации с указанным статусом. */
    List<ShadowConfig> findAllByStatus(String status);

    /** Найти конфигурации по статусу. */
    List<ShadowConfig> findByStatus(String status);
}
