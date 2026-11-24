package com.paytrack.expense.config;

import com.paytrack.expense.event.PaymentCompletedEvent;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    public static final String PAYMENT_EXCHANGE = "payment.exchange";
    public static final String PAYMENT_COMPLETED_ROUTING_KEY = "payment.completed";
    public static final String EXPENSE_PAYMENT_QUEUE = "expense.payment.queue";
    public static final String EXPENSE_PAYMENT_DLQ = "expense.payment.dlq";

    @Bean
    public TopicExchange paymentExchange() {
        return new TopicExchange(PAYMENT_EXCHANGE, true, false);
    }

    @Bean
    public Queue expensePaymentQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", "");
        args.put("x-dead-letter-routing-key", EXPENSE_PAYMENT_DLQ);
        return new Queue(EXPENSE_PAYMENT_QUEUE, true, false, false, args);
    }

    @Bean
    public Queue expensePaymentDlq() {
        return new Queue(EXPENSE_PAYMENT_DLQ, true);
    }

    @Bean
    public Binding expensePaymentBinding(Queue expensePaymentQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(expensePaymentQueue).to(paymentExchange).with(PAYMENT_COMPLETED_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultClassMapper classMapper = new DefaultClassMapper();
        classMapper.setTrustedPackages("*");
        classMapper.setIdClassMapping(Map.of(
            "com.paytrack.transaction.event.PaymentCompletedEvent", PaymentCompletedEvent.class,
            "com.paytrack.expense.event.PaymentCompletedEvent", PaymentCompletedEvent.class
        ));
        converter.setClassMapper(classMapper);
        return converter;
    }
}
