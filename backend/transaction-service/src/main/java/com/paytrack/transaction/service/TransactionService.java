package com.paytrack.transaction.service;

import com.paytrack.transaction.client.WalletClient;
import com.paytrack.transaction.dto.TransactionDto;
import com.paytrack.transaction.entity.Transaction;
import com.paytrack.transaction.exception.BusinessException;
import com.paytrack.transaction.exception.ResourceNotFoundException;
import com.paytrack.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletClient walletClient;

    public TransactionDto createTransaction(TransactionDto transactionDto) {
        Transaction transaction = new Transaction();
        transaction.setTransactionId(UUID.randomUUID().toString());
        transaction.setUserId(transactionDto.getUserId());

        // Issue 3: walletId defaults to userId when caller omits it
        String walletId = transactionDto.getWalletId();
        if (walletId == null || walletId.isBlank()) {
            walletId = transactionDto.getUserId();
            log.warn("walletId was null/blank; defaulting to userId {} for wallet resolution", walletId);
        }
        transaction.setWalletId(walletId);

        transaction.setDescription(transactionDto.getDescription());
        transaction.setAmount(transactionDto.getAmount());
        transaction.setType(transactionDto.getType());

        String type = transactionDto.getType() != null ? transactionDto.getType().toUpperCase() : "";
        boolean walletDebited = false;
        boolean walletCredited = false;
        try {
            if ("DEBIT".equals(type) || "WITHDRAWAL".equals(type) || "PAYMENT".equals(type)) {
                log.info("Debiting wallet {} with amount {}", walletId, transactionDto.getAmount());
                walletClient.debit(transactionDto.getAmount());
                walletDebited = true;
                transaction.setStatus(transactionDto.getStatus() != null ? transactionDto.getStatus() : "COMPLETED");
            } else if ("CREDIT".equals(type) || "DEPOSIT".equals(type)) {
                log.info("Crediting wallet {} with amount {}", walletId, transactionDto.getAmount());
                walletClient.credit(transactionDto.getAmount());
                walletCredited = true;
                transaction.setStatus(transactionDto.getStatus() != null ? transactionDto.getStatus() : "COMPLETED");
            } else {
                transaction.setStatus(transactionDto.getStatus() != null ? transactionDto.getStatus() : "PENDING");
            }
        } catch (Exception ex) {
            log.error("Failed to update wallet balance via OpenFeign: {}", ex.getMessage());
            transaction.setStatus("FAILED");
            transactionRepository.save(transaction);
            throw new BusinessException("Transaction failed: " + ex.getMessage());
        }

        // Issue 4: if DB save fails after we already hit the wallet service via
        // OpenFeign, compensate by reversing the remote balance change.
        Transaction savedTransaction;
        try {
            savedTransaction = transactionRepository.save(transaction);
        } catch (Exception ex) {
            log.error("Transaction save failed after remote wallet mutation; attempting compensating {} for amount {}",
                    walletDebited ? "credit" : (walletCredited ? "debit" : "no-op"),
                    transactionDto.getAmount(), ex);
            if (walletDebited) {
                try {
                    walletClient.credit(transactionDto.getAmount());
                    log.info("Compensation credit succeeded for amount {}", transactionDto.getAmount());
                } catch (Exception compEx) {
                    log.error("Compensation credit FAILED for amount {}: {}", transactionDto.getAmount(), compEx.getMessage());
                }
            } else if (walletCredited) {
                try {
                    walletClient.debit(transactionDto.getAmount());
                    log.info("Compensation debit succeeded for amount {}", transactionDto.getAmount());
                } catch (Exception compEx) {
                    log.error("Compensation debit FAILED for amount {}: {}", transactionDto.getAmount(), compEx.getMessage());
                }
            }
            throw new BusinessException("Failed to persist transaction: " + ex.getMessage());
        }
        return convertToDto(savedTransaction);
    }

    public List<TransactionDto> getAllTransactions() {
        return transactionRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public TransactionDto getTransactionById(Long id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with ID: " + id));
        return convertToDto(transaction);
    }

    public TransactionDto updateTransactionStatus(Long id, String status) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with ID: " + id));
        transaction.setStatus(status);
        Transaction updatedTransaction = transactionRepository.save(transaction);
        return convertToDto(updatedTransaction);
    }

    public List<TransactionDto> getTransactionsByWalletId(String walletId) {
        return transactionRepository.findByWalletId(walletId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    private TransactionDto convertToDto(Transaction transaction) {
        return TransactionDto.builder()
                .id(transaction.getId())
                .userId(transaction.getUserId())
                .transactionId(transaction.getTransactionId())
                .walletId(transaction.getWalletId())
                .description(transaction.getDescription())
                .amount(transaction.getAmount())
                .type(transaction.getType())
                .timestamp(transaction.getTimestamp())
                .status(transaction.getStatus())
                .build();
    }
}
