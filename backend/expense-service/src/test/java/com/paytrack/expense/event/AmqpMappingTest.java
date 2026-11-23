package com.paytrack.expense.event;

import com.paytrack.expense.event.PaymentCompletedEvent;
import com.paytrack.expense.config.RabbitMQConfig;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AmqpMappingTest {

    @Test
    void paymentCompletedEvent_shouldDeserializeCorrectly() {
        // Given: Create a PaymentCompletedEvent
        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .eventId(UUID.randomUUID())
                .txnRef("TXN20241123000001")
                .userId("user-123")
                .type("P2P")
                .amount(new BigDecimal("100.00"))
                .currency("INR")
                .note("Test payment")
                .requestedCategory("FOOD")
                .completedAt(LocalDateTime.now())
                .build();

        // When: Serialize and deserialize using the message converter
        MessageConverter converter = RabbitMQConfig.messageConverter();
        Message message = converter.toMessage(event, null);

        PaymentCompletedEvent deserialized = (PaymentCompletedEvent) converter.fromMessage(message);

        // Then: All fields should match
        assertThat(deserialized).isNotNull();
        assertThat(deserialized.getEventId()).isEqualTo(event.getEventId());
        assertThat(deserialized.getTxnRef()).isEqualTo(event.getTxnRef());
        assertThat(deserialized.getUserId()).isEqualTo(event.getUserId());
        assertThat(deserialized.getType()).isEqualTo(event.getType());
        assertThat(deserialized.getAmount()).isEqualByComparingTo(event.getAmount());
        assertThat(deserialized.getCurrency()).isEqualTo(event.getCurrency());
        assertThat(deserialized.getNote()).isEqualTo(event.getNote());
        assertThat(deserialized.getRequestedCategory()).isEqualTo(event.getRequestedCategory());
        assertThat(deserialized.getCompletedAt()).isEqualTo(event.getCompletedAt());
    }

    @Test
    void paymentCompletedEvent_shouldDeserializeWithAllTypes() {
        // Given: Test all transaction types
        String[] types = {"P2P", "QR", "RECHARGE"};

        for (String type : types) {
            PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                    .eventId(UUID.randomUUID())
                    .txnRef("TXN20241123000001")
                    .userId("user-123")
                    .type(type)
                    .amount(new BigDecimal("100.00"))
                    .currency("INR")
                    .note("Test " + type)
                    .requestedCategory("FOOD")
                    .completedAt(LocalDateTime.now())
                    .build();

            MessageConverter converter = RabbitMQConfig.messageConverter();
            Message message = converter.toMessage(event, null);

            PaymentCompletedEvent deserialized = (PaymentCompletedEvent) converter.fromMessage(message);

            assertThat(deserialized.getType()).isEqualTo(type);
        }
    }

    @Test
    void paymentCompletedEvent_shouldHandleNullFields() {
        // Given: Event with optional fields null
        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .eventId(UUID.randomUUID())
                .txnRef("TXN20241123000001")
                .userId("user-123")
                .type("RECHARGE")
                .amount(new BigDecimal("100.00"))
                .currency("INR")
                .note(null)
                .requestedCategory(null)
                .completedAt(LocalDateTime.now())
                .build();

        // When/Then: Should deserialize without error
        MessageConverter converter = RabbitMQConfig.messageConverter();
        Message message = converter.toMessage(event, null);

        PaymentCompletedEvent deserialized = (PaymentCompletedEvent) converter.fromMessage(message);

        assertThat(deserialized).isNotNull();
        assertThat(deserialized.getNote()).isNull();
        assertThat(deserialized.getRequestedCategory()).isNull();
    }
}