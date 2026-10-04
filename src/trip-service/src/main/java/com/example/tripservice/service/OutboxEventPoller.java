package com.example.tripservice.service;

import com.example.tripservice.entity.OutboxEvent;
import com.example.tripservice.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class OutboxEventPoller {

    private final OutboxEventRepository outboxEventRepository;
    private final org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;

    public OutboxEventPoller(OutboxEventRepository outboxEventRepository, java.util.Optional<org.springframework.kafka.core.KafkaTemplate<String, Object>> kafkaTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate.orElse(null);
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void pollOutbox() {
        if (kafkaTemplate == null) {
            return;
        }
        List<OutboxEvent> events = outboxEventRepository.findAll();
        for (OutboxEvent event : events) {
            try {
                kafkaTemplate.send("trip-events", event.getAggregateId(), event.getPayload());
                outboxEventRepository.delete(event);
                log.debug("Published event {} to topic trip-events", event.getId());
            } catch (Exception e) {
                log.error("Failed to publish event {} to Kafka", event.getId(), e);
            }
        }
    }
}
