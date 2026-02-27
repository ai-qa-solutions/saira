package io.saira.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.saira.entity.ShadowEvaluation;

/** Репозиторий для работы с оценками shadow-результатов. */
@Repository
public interface ShadowEvaluationRepository extends JpaRepository<ShadowEvaluation, Long> {

    /** Найти оценки по ID shadow-результата. */
    List<ShadowEvaluation> findByShadowResultId(Long shadowResultId);
}
