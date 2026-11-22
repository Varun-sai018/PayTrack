package com.paytrack.wallet.service;

import com.paytrack.wallet.UserContextHolder;
import com.paytrack.wallet.dto.CreateWalletRequest;
import com.paytrack.wallet.dto.UpdateBalanceRequest;
import com.paytrack.wallet.dto.WalletDto;
import com.paytrack.wallet.entity.Wallet;
import com.paytrack.wallet.exception.InsufficientBalanceException;
import com.paytrack.wallet.exception.WalletNotFoundException;
import com.paytrack.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;

    public WalletDto getWallet(String userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new WalletNotFoundException(userId));
        return convertToDto(wallet);
    }

    @Transactional
    public WalletDto createWallet(CreateWalletRequest request) {
        if (walletRepository.existsByUserId(request.getUserId())) {
            throw new IllegalArgumentException("Wallet already exists for user: " + request.getUserId());
        }
        Wallet wallet = Wallet.builder()
                .userId(request.getUserId())
                .balance(BigDecimal.ZERO)
                .currency(request.getCurrency() != null ? request.getCurrency() : "USD")
                .build();
        return convertToDto(walletRepository.save(wallet));
    }

    @Transactional
    public WalletDto credit(String userId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new WalletNotFoundException(userId));
        wallet.setBalance(wallet.getBalance().add(amount));
        return convertToDto(walletRepository.save(wallet));
    }

    @Transactional
    public WalletDto debit(String userId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new WalletNotFoundException(userId));
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance. Available: " + wallet.getBalance());
        }
        wallet.setBalance(wallet.getBalance().subtract(amount));
        return convertToDto(walletRepository.save(wallet));
    }

    public WalletDto updateBalance(UpdateBalanceRequest request, String userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new WalletNotFoundException(userId));
        wallet.setBalance(wallet.getBalance().add(request.getAmount()));
        return convertToDto(walletRepository.save(wallet));
    }

    public WalletDto getWalletFromContext() {
        String userId = UserContextHolder.getUserId();
        if (userId == null) {
            throw new IllegalStateException("User context not set. X-User-Id header is required.");
        }
        return getWallet(userId);
    }

    private WalletDto convertToDto(Wallet wallet) {
        return WalletDto.builder()
                .id(wallet.getId())
                .userId(wallet.getUserId())
                .balance(wallet.getBalance())
                .currency(wallet.getCurrency())
                .createdAt(wallet.getCreatedAt())
                .updatedAt(wallet.getUpdatedAt())
                .build();
    }
}
