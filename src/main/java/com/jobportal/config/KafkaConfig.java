package com.jobportal.config;

import com.jobportal.kafka.KafkaTopics;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    // --- Admin ---

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configs.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "30000");
        KafkaAdmin admin = new KafkaAdmin(configs);
        admin.setFatalIfBrokerNotAvailable(false);
        return admin;
    }

    // --- Topics ---

    @Bean
    public org.apache.kafka.clients.admin.NewTopic scrapeJobRequestedTopic() {
        return TopicBuilder.name(KafkaTopics.SCRAPE_JOB_REQUESTED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public org.apache.kafka.clients.admin.NewTopic scrapeJobCompletedTopic() {
        return TopicBuilder.name(KafkaTopics.SCRAPE_JOB_COMPLETED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public org.apache.kafka.clients.admin.NewTopic jobIndexingRequestedTopic() {
        return TopicBuilder.name(KafkaTopics.JOB_INDEXING_REQUESTED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public org.apache.kafka.clients.admin.NewTopic jobVisaDetectionRequestedTopic() {
        return TopicBuilder.name(KafkaTopics.JOB_VISA_DETECTION_REQUESTED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public org.apache.kafka.clients.admin.NewTopic scrapeJobFailedTopic() {
        return TopicBuilder.name(KafkaTopics.SCRAPE_JOB_FAILED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    // --- Producer ---

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // --- Consumer ---

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3);
        factory.setAutoStartup(true);
        return factory;
    }
}
