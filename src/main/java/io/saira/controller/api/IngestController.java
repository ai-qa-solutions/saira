package io.saira.controller.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;

import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.saira.dto.IngestResponse;
import io.saira.service.ingest.IngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST endpoint для приёма OTel телеметрии в формате JSON.
 * Используется для прямой отправки данных (без Kafka).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ingest")
@RequiredArgsConstructor
public class IngestController {

    /** Сервис обработки ingestion данных. */
    private final IngestService ingestService;

    /**
     * Приём OTLP JSON данных.
     *
     * @param body JSON строка в формате ExportTraceServiceRequest
     * @return 202 Accepted с статистикой обработки
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IngestResponse> ingest(@RequestBody final String body) {
        log.debug("Received OTLP JSON ingest request, size={} bytes", body.length());
        try {
            final ExportTraceServiceRequest.Builder builder = ExportTraceServiceRequest.newBuilder();
            JsonFormat.parser().ignoringUnknownFields().merge(body, builder);
            final ExportTraceServiceRequest request = builder.build();
            final IngestResponse response = ingestService.ingest(request);
            log.info(
                    "Ingested via REST: traces={}, spans={}, filtered={}",
                    response.getTraceCount(),
                    response.getSpanCount(),
                    response.getFilteredOutCount());
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        } catch (InvalidProtocolBufferException e) {
            log.warn("Invalid OTLP JSON: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
}
