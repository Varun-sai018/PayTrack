package com.paytrack.transaction.consistency;

import com.paytrack.transaction.UserContextHolder;
import com.paytrack.transaction.client.AuthClient;
import com.paytrack.transaction.client.WalletClient;
import com.paytrack.transaction.dto.*;
import com.paytrack.transaction.entity.Transaction;
import com.paytrack.transaction.exception.BusinessException;
import com.paytrack.transaction.repository.RechargeRepository;
import com.paytrack.transaction.repository.TransactionRepository;
import com.paytrack.transaction.service.DummyRechargeOperator;
import com.paytrack.transaction.service.TransactionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParallelTransferConsistencyTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private RechargeRepository rechargeRepository;

    @Mock
    private WalletClient walletClient;

    @Mock
    private AuthClient authClient;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Spy
    private DummyRechargeOperator rechargeOperator = new DummyRechargeOperator();

    private TransactionService transactionService;

    private final int NUM_USERS = 4;
    private final int NUM_TRANSFERS = 20;
    private final BigDecimal TRANSFER_AMOUNT = new BigDecimal("50.00");

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(
                transactionRepository,
                rechargeRepository,
                walletClient,
                authClient,
                rabbitTemplate,
                rechargeOperator
        );

        ReflectionTestUtils.setField(transactionService, "minAmount", new BigDecimal("1.00"));
        ReflectionTestUtils.setField(transactionService, "maxTransactionLimit", new BigDecimal("10000.00"));
        ReflectionTestUtils.setField(transactionService, "dailyTotalLimit", new BigDecimal("50000.00"));
        ReflectionTestUtils.setField(transactionService, "configuredTimezone", "Asia/Kolkata");
        ReflectionTestUtils.setField(transactionService, "internalApiKey", "test-key");

        UserContextHolder.setUserId("user-0");
        UserContextHolder.setRole("USER");
        UserContextHolder.setKycStatus("APPROVED");
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    void parallelTransfers_shouldMaintainConsistency() throws Exception {
        List<UserLookupDto> users = new ArrayList<>();
        for (int i = 0; i < NUM_USERS; i++) {
            users.add(UserLookupDto.builder()
                    .userId("user-" + i)
                    .maskedName("U*** " + i)
                    .fullName("User " + i)
                    .upiId("user." + i + "@paytrack")
                    .accountStatus("ACTIVE")
                    .kycStatus("APPROVED")
                    .build());
        }

        when(authClient.lookupUserByUpi(anyString(), anyString())).thenAnswer(invocation -> {
            String upiId = invocation.getArgument(0);
            return users.stream()
                    .filter(u -> u.getUpiId().equals(upiId))
                    .findFirst()
                    .map(ApiResponse::success)
                    .orElse(ApiResponse.error("Not found", "NOT_FOUND"));
        });

        Map<String, Transaction> savedTransactions = new ConcurrentHashMap<>();

        when(transactionRepository.findByUserIdAndIdempotencyKey(anyString(), anyString()))
                .thenReturn(Optional.empty());

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            savedTransactions.put(t.getTxnRef(), t);
            return t;
        });

        when(walletClient.internalTransfer(anyString(), any(InternalTransferRequest.class)))
                .thenAnswer(invocation -> {
                    InternalTransferRequest req = invocation.getArgument(1);
                    return ApiResponse.success(InternalTransferResponse.builder()
                            .txnRef(req.getTxnRef())
                            .status("COMPLETED")
                            .senderBalanceAfter(new BigDecimal("950.00"))
                            .build());
                });

        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<Future<TransferResponse>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < NUM_TRANSFERS; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                UserContextHolder.setUserId("user-" + (index % NUM_USERS));
                UserContextHolder.setRole("USER");
                UserContextHolder.setKycStatus("APPROVED");

                UserLookupDto receiver = users.get((index + 1) % NUM_USERS);
                TransferRequest req = TransferRequest.builder()
                        .receiverUpiId(receiver.getUpiId())
                        .amount(TRANSFER_AMOUNT)
                        .note("Transfer " + index)
                        .build();

                TransferResponse resp = transactionService.transfer("user-" + (index % NUM_USERS), "idem-" + index, req);
                successCount.incrementAndGet();
                return resp;
            }));
        }

        for (Future<TransferResponse> f : futures) {
            f.get(5, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(NUM_TRANSFERS);
        assertThat(savedTransactions.size()).isEqualTo(NUM_TRANSFERS);
    }
}
