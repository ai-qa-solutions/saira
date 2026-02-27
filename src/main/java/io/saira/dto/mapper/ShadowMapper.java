package io.saira.dto.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import io.saira.dto.ShadowConfigResponse;
import io.saira.dto.ShadowResultResponse;
import io.saira.entity.ShadowConfig;
import io.saira.entity.ShadowResult;

/** MapStruct маппер для shadow-сущностей и DTO. */
@Mapper(componentModel = "spring")
public interface ShadowMapper {

    /** Преобразует ShadowConfig в ShadowConfigResponse. */
    ShadowConfigResponse toConfigResponse(ShadowConfig entity);

    /** Преобразует список ShadowConfig в список ShadowConfigResponse. */
    List<ShadowConfigResponse> toConfigResponseList(List<ShadowConfig> entities);

    /** Преобразует ShadowResult в ShadowResultResponse (без evaluation-полей). */
    @Mapping(target = "semanticSimilarity", ignore = true)
    @Mapping(target = "correctnessScore", ignore = true)
    ShadowResultResponse toResultResponse(ShadowResult entity);

    /** Преобразует список ShadowResult в список ShadowResultResponse. */
    List<ShadowResultResponse> toResultResponseList(List<ShadowResult> entities);
}
