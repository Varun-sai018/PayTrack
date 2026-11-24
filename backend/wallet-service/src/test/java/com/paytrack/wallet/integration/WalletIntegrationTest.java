package com.paytrack.wallet.integration;

import com.paytrack.wallet.entity.Wallet;
import com.paytrack.wallet.repository.WalletRepository;
import com.paytrack.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "eureka.client.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class WalletIntegrationTest {

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletService walletService;

    @Test
    void walletCreation_shouldPersistWallet() {
        // Given
        String userId = UUID.randomUUID().toString();
        Wallet wallet = Wallet.builder()
                .userId(userId)
                .balance(new BigDecimal("1000.00"))
                .currency("INR")
                .build();

        // When
        Wallet saved = walletRepository.save(wallet);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getBalance()).isEqualByComparingTo("1000.00");
        assertThat(saved.getCurrency()).isEqualTo("INR");
    }
}
