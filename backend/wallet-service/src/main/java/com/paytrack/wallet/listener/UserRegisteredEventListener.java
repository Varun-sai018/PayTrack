package com.paytrack.wallet.listener;

import com.paytrack.wallet.dto.CreateWalletRequest;
import com.paytrack.wallet.event.UserRegisteredEvent;
import com.paytrack.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RabbitListener(queues = "welcome.queue")
@RequiredArgsConstructor
@Slf4j
public class UserRegisteredEventListener {

    private final WalletService walletService;

    @RabbitHandler
    public void handleUserRegistered(UserRegisteredEvent event) {
        log.info("Received user.registered event for userId: {}", event.getUserId());

        // Create wallet automatically for new user with INR currency
        try {
            CreateWalletRequest dto = CreateWalletRequest.builder()
                    .userId(event.getUserId())
                    .currency("INR")
                    .build();
            walletService.createWallet(dto);
            log.info("Wallet created successfully in INR for user: {}", event.getUserId());
        } catch (Exception e) {
            log.warn("Wallet initialization handled: user={}, error={}", event.getUserId(), e.getMessage());
        }
    }
}
