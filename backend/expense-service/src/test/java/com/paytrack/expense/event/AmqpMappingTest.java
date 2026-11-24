package com.paytrack.expense.event;

import com.paytrack.expense.config.RabbitMQConfig;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.support.converter.MessageConverter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AmqpMappingTest {

    private final MessageConverter converter = new RabbitMQConfig().messageConverter();

    @Test
    void paymentCompletedEvent_shouldDeserializeCorrectly() {
        // Given: Create a PaymentCompletedEvent
        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .eventId(UUID.randomUUID())
                .txnRef("TXN20261123000001")
                .userId("user-123")
                .type("P2P")
                .amount(new BigDecimal("100.00"))
                .currency("INR")
                .note("Test payment")
                .requestedCategory("FOOD")
                .completedAt(LocalDateTime.now())
                .build();

        // When: Serialize and deserialize using the message converter
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
    }

    @Test
    void paymentCompletedEvent_shouldDeserializeWithAllTypes() {
        String[] types = {"P2P", "QR", "RECHARGE"};

        for (String type : types) {
            PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                    .eventId(UUID.randomUUID())
                    .txnRef("TXN20261123000001")
                    .userId("user-123")
                    .type(type)
                    .amount(new BigDecimal("100.00"))
                    .currency("INR")
                    .note("Test " + type)
                    .requestedCategory("FOOD")
                    .completedAt(LocalDateTime.now())
                    .build();

            Message message = converter.toMessage(event, null);
            PaymentCompletedEvent deserialized = (PaymentCompletedEvent) converter.fromMessage(message);

            assertThat(deserialized.getType()).isEqualTo(type);
        }
    }

    @Test
    void paymentCompletedEvent_shouldHandleNullFields() {
        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .eventId(UUID.randomUUID())
                .txnRef("TXN20261123000001")
                .userId("user-123")
                .type("RECHARGE")
                .amount(new BigDecimal("100.00"))
                .currency("INR")
                .note(null)
                .requestedCategory(null)
                .completedAt(LocalDateTime.now())
                .build();

        Message message = converter.toMessage(event, null);
        PaymentCompletedEvent deserialized = (PaymentCompletedEvent) converter.fromMessage(message);

        assertThat(deserialized).isNotNull();
        assertThat(deserialized.getNote()).isNull();
        assertThat(deserialized.getRequestedCategory()).isNull();
    }
}
