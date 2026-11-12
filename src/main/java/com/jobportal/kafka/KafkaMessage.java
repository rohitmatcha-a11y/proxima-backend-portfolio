package com.jobportal.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KafkaMessage<T> {

    private String messageId;
    private String topic;
    private Instant timestamp;
    private String source;
    private T payload;

    public static <T> KafkaMessage<T> of(String topic, T payload) {
        return KafkaMessage.<T>builder()
                .messageId(UUID.randomUUID().toString())
                .topic(topic)
                .timestamp(Instant.now())
                .source("job-portal-backend")
                .payload(payload)
                .build();
    }
}
