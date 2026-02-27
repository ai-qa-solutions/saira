package io.saira.controller.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import io.saira.dto.TraceDetailResponse;
import io.saira.dto.TraceListItemResponse;
import io.saira.service.trace.TraceService;
import lombok.RequiredArgsConstructor;

/**
 * REST контроллер для работы с трейсами агентов.
 * Предоставляет API для получения списка и деталей трейсов.
 */
@RestController
@RequestMapping("/api/v1/traces")
@RequiredArgsConstructor
public class TraceController {

    /** Сервис для работы с трейсами. */
    private final TraceService traceService;

    /**
     * Получить список трейсов с пагинацией.
     *
     * @param page номер страницы (начиная с 0)
     * @param size размер страницы
     * @return страница с элементами списка трейсов
     */
    @GetMapping
    public Page<TraceListItemResponse> getTraces(
            @RequestParam(defaultValue = "0") final int page, @RequestParam(defaultValue = "20") final int size) {
        return traceService.getTraces(PageRequest.of(page, size));
    }

    /**
     * Получить детальную информацию о трейсе.
     *
     * @param traceId OpenTelemetry trace ID
     * @return детализированная информация о трейсе со списком спанов
     */
    @GetMapping("/{traceId}")
    public TraceDetailResponse getTraceDetail(@PathVariable final String traceId) {
        return traceService.getTraceDetail(traceId);
    }
}
