package com.paytrack.wallet.listener;

import com.paytrack.wallet.event.UserRegisteredEvent;
import com.paytrack.wallet.service.WalletService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RabbitListener(queues = "welcome.queue")
@Slf4j
public class UserRegisteredEventListener {

    private final WalletService walletService;

    public UserRegisteredEventListener(WalletService walletService) {
        this.walletService = walletService;
    }

    @RabbitHandler
    public void handleUserRegistered(UserRegisteredEvent event) {
        log.info("Received user.registered event for userId: {}", event.getUserId());

        // Create wallet automatically for new user
        try {
            var dto = com.paytrack.wallet.dto.CreateWalletRequest.builder()
                    .userId(event.getUserId())
                    .currency("USD")
                    .build();
            walletService.createWallet(dto);
            log.info("Wallet created for new user: {}", event.getUserId());
        } catch (Exception e) {
            log.warn("Wallet may already exist for user: {}", event.getUserId(), e);
        }
    }
}