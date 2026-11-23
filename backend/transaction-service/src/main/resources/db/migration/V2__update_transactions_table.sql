-- Add new columns to transactions table
ALTER TABLE transactions ADD COLUMN txn_ref VARCHAR(36) UNIQUE;
ALTER TABLE transactions ADD COLUMN idempotency_key VARCHAR(36) UNIQUE;
ALTER TABLE transactions ADD COLUMN request_hash VARCHAR(36);
ALTER TABLE transactions ADD COLUMN receiver_user_id VARCHAR(36);
ALTER TABLE transactions ADD COLUMN receiver_upi_id VARCHAR(255);
ALTER TABLE transactions ADD COLUMN failure_reason VARCHAR(255);
ALTER TABLE transactions ADD COLUMN requested_category VARCHAR(50);
ALTER TABLE transactions ADD COLUMN event_published BOOLEAN NOT NULL DEFAULT FALSE;

-- Add constraint for unique (user_id, idempotency_key)
-- We'll add this via a partial index since MySQL doesn't support functional constraints easily
CREATE UNIQUE INDEX idx_transactions_user_id_key ON transactions(user_id, idempotency_key);

-- Add indexes for receiver and event_published
CREATE INDEX idx_transactions_receiver_user_id ON transactions(receiver_user_id);
CREATE INDEX idx_transactions_event_published ON transactions(event_published);
CREATE INDEX idx_transactions_event_published_status ON transactions(event_published, status);

-- Extend the type column to include P2P, QR, RECHARGE (keep old values for existing rows)
-- We'll add a check constraint for the new types
ALTER TABLE transactions ADD CONSTRAINT chk_transaction_type CHECK (type IN ('DEBIT', 'CREDIT', 'TRANSFER', 'P2P', 'QR', 'RECHARGE'));

-- Update existing rows to have INR currency if there is a currency column (but we're keeping VARCHAR(36) ids)
-- The report says default currency INR, so we set a comment but don't change existing data

-- Create recharges table
CREATE TABLE recharges (
    id VARCHAR(36) PRIMARY KEY,
    txn_id BIGINT NOT NULL,
    mobile_number VARCHAR(10) NOT NULL,
    operator ENUM('AIRTEL', 'JIO', 'VI', 'BSNL') NOT NULL,
    operator_ref VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (txn_id) REFERENCES transactions(id) ON DELETE CASCADE
);

-- Create index for recharges
CREATE INDEX idx_recharges_txn_id ON recharges(txn_id);
CREATE INDEX idx_recharges_operator ON recharges(operator);

-- Add triggers for updated_at
DELIMITER //
CREATE TRIGGER update_transactions_updated_at BEFORE UPDATE ON transactions
FOR EACH ROW
BEGIN
    SET NEW.updated_at = CURRENT_TIMESTAMP;
END//
DELIMITER ;