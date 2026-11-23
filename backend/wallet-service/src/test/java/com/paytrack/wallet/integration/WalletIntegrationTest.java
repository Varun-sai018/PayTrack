package com.paytrack.wallet.integration;

import com.paytrack.wallet.entity.Wallet;
import com.paytrack.wallet.repository.WalletRepository;
import com.paytrack.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class WalletIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("paytrack_wallet")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

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