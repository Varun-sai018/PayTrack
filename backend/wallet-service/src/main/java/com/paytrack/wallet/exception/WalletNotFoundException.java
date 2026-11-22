package com.paytrack.wallet.exception;

public class WalletNotFoundException extends ResourceNotFoundException {
    public WalletNotFoundException(String userId) {
        super("Wallet not found for user: " + userId);
    }
}