package com.paytrack.expense.listener;

import com.paytrack.expense.config.RabbitMQConfig;
import com.paytrack.expense.event.PaymentCompletedEvent;
import com.paytrack.expense.service.ExpenseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentCompletedEventListener {

    private final ExpenseService expenseService;

    @RabbitListener(queues = RabbitMQConfig.EXPENSE_PAYMENT_QUEUE)
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("Received payment.completed event: txnRef={}, userId={}, amount={}, type={}",
                event.getTxnRef(), event.getUserId(), event.getAmount(), event.getType());

        try {
            expenseService.recordPaymentExpense(event);
        } catch (Exception ex) {
            log.error("Failed to process payment.completed for txnRef {}: {}", event.getTxnRef(), ex.getMessage(), ex);
            throw ex;
        }
    }
}
