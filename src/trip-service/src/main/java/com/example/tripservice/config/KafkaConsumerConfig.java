package com.example.tripservice.config;

import com.example.tripservice.event.DeadLetterPublisher;
import com.example.tripservice.exception.ValidationException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DeserializationException;

/**
 * Bounded retry then DLQ for every @KafkaListener (docs/events.md §8):
 * retry 3 times with exponential backoff 1s, 2s, 4s (max 30s), then publish to {@code <topic>.DLQ}.
 * Spring Boot plugs this CommonErrorHandler bean into the auto-configured listener container factory.
 * A listener only has to throw: {@link ValidationException} for a malformed event (no retry),
 * any other exception for a failure that may succeed later.
 */
@Configuration
public class KafkaConsumerConfig {

    public static final int MAX_RETRIES = 3;
    private static final long INITIAL_INTERVAL_MS = 1_000;
    private static final double MULTIPLIER = 2.0;
    private static final long MAX_INTERVAL_MS = 30_000;

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(DeadLetterPublisher deadLetterPublisher) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(MAX_RETRIES);
        backOff.setInitialInterval(INITIAL_INTERVAL_MS);
        backOff.setMultiplier(MULTIPLIER);
        backOff.setMaxInterval(MAX_INTERVAL_MS);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(deadLetterPublisher, backOff);
        // A malformed event fails the same way every time: skip the retries and go to the DLQ
        errorHandler.addNotRetryableExceptions(ValidationException.class, DeserializationException.class);
        errorHandler.setRetryListeners(deadLetterPublisher);
        // Commit the offset of a record once it is in the DLQ so it is not delivered again
        errorHandler.setCommitRecovered(true);
        return errorHandler;
    }
}
