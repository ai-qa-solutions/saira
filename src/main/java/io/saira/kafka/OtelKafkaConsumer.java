package io.saira.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.google.protobuf.InvalidProtocolBufferException;

import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.saira.dto.IngestResponse;
import io.saira.service.ingest.IngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Kafka consumer для приёма OTel телеметрии в формате protobuf.
 * Слушает топик fp-saira-spans, десериализует ExportTraceServiceRequest.
 * Всегда подтверждает offset (manual ack) — даже при ошибке, чтобы не блокировать очередь.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OtelKafkaConsumer {

    /** Сервис обработки ingestion данных. */
    private final IngestService ingestService;

    /**
     * Обработка сообщения из Kafka.
     * Десериализует protobuf -> вызывает IngestService -> ack.
     *
     * @param record         сообщение из Kafka (byte[])
     * @param acknowledgment подтверждение offset
     */
    @KafkaListener(topics = "${saira.kafka.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(final ConsumerRecord<String, byte[]> record, final Acknowledgment acknowledgment) {
        log.debug(
                "Received Kafka message: topic={}, partition={}, offset={}",
                record.topic(),
                record.partition(),
                record.offset());
        try {
            final ExportTraceServiceRequest request = ExportTraceServiceRequest.parseFrom(record.value());
            final IngestResponse response = ingestService.ingest(request);
            log.info(
                    "Ingested via Kafka: traces={}, spans={}, filtered={}, partition={}, offset={}",
                    response.getTraceCount(),
                    response.getSpanCount(),
                    response.getFilteredOutCount(),
                    record.partition(),
                    record.offset());
        } catch (InvalidProtocolBufferException e) {
            log.error(
                    "Failed to parse protobuf from Kafka message: partition={}, offset={}, error={}",
                    record.partition(),
                    record.offset(),
                    e.getMessage());
        } catch (Exception e) {
            log.error(
                    "Error processing Kafka message: partition={}, offset={}", record.partition(), record.offset(), e);
        } finally {
            acknowledgment.acknowledge();
        }
    }
}
