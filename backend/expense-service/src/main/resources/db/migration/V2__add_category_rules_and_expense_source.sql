-- Add source and category_source columns to expenses table
ALTER TABLE expenses ADD COLUMN source ENUM('PAYMENT', 'MANUAL') NOT NULL DEFAULT 'MANUAL';
ALTER TABLE expenses ADD COLUMN category_source ENUM('USER', 'RULE', 'DEFAULT', 'MANUAL') NOT NULL DEFAULT 'MANUAL';
ALTER TABLE expenses ADD COLUMN txn_ref VARCHAR(36) UNIQUE;

-- Create index for txn_ref
CREATE INDEX idx_expenses_txn_ref ON expenses(txn_ref);

-- Create category_rules table
CREATE TABLE category_rules (
    id VARCHAR(36) PRIMARY KEY,
    keyword VARCHAR(255) NOT NULL,
    category ENUM('FOOD', 'TRANSPORT', 'HOUSING', 'ENTERTAINMENT', 'UTILITIES', 'HEALTHCARE', 'SHOPPING', 'EDUCATION', 'OTHER') NOT NULL,
    match_on ENUM('NOTE', 'TXN_TYPE') NOT NULL DEFAULT 'NOTE',
    priority INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for category_rules
CREATE INDEX idx_category_rules_category ON category_rules(category);
CREATE INDEX idx_category_rules_priority ON category_rules(priority DESC);

-- Seed category rules
INSERT INTO category_rules (id, keyword, category, match_on, priority) VALUES
(UUID(), 'lunch', 'FOOD', 'NOTE', 10),
(UUID(), 'dinner', 'FOOD', 'NOTE', 10),
(UUID(), 'breakfast', 'FOOD', 'NOTE', 10),
(UUID(), 'food', 'FOOD', 'NOTE', 10),
(UUID(), 'pizza', 'FOOD', 'NOTE', 10),
(UUID(), 'swiggy', 'FOOD', 'NOTE', 10),
(UUID(), 'zomato', 'FOOD', 'NOTE', 10),
(UUID(), 'cafe', 'FOOD', 'NOTE', 10),
(UUID(), 'uber', 'TRANSPORT', 'NOTE', 10),
(UUID(), 'ola', 'TRANSPORT', 'NOTE', 10),
(UUID(), 'bus', 'TRANSPORT', 'NOTE', 10),
(UUID(), 'train', 'TRANSPORT', 'NOTE', 10),
(UUID(), 'petrol', 'TRANSPORT', 'NOTE', 10),
(UUID(), 'fuel', 'TRANSPORT', 'NOTE', 10),
(UUID(), 'metro', 'TRANSPORT', 'NOTE', 10),
(UUID(), 'amazon', 'SHOPPING', 'NOTE', 10),
(UUID(), 'flipkart', 'SHOPPING', 'NOTE', 10),
(UUID(), 'shopping', 'SHOPPING', 'NOTE', 10),
(UUID(), 'clothes', 'SHOPPING', 'NOTE', 10),
(UUID(), 'electricity', 'UTILITIES', 'NOTE', 10),
(UUID(), 'water', 'UTILITIES', 'NOTE', 10),
(UUID(), 'bill', 'UTILITIES', 'NOTE', 10),
(UUID(), 'recharge', 'UTILITIES', 'NOTE', 10),
(UUID(), 'rent', 'UTILITIES', 'NOTE', 10),
(UUID(), 'movie', 'ENTERTAINMENT', 'NOTE', 10),
(UUID(), 'netflix', 'ENTERTAINMENT', 'NOTE', 10),
(UUID(), 'game', 'ENTERTAINMENT', 'NOTE', 10),
(UUID(), 'doctor', 'HEALTHCARE', 'NOTE', 10),
(UUID(), 'medicine', 'HEALTHCARE', 'NOTE', 10),
(UUID(), 'pharmacy', 'HEALTHCARE', 'NOTE', 10),
(UUID(), 'fees', 'EDUCATION', 'NOTE', 10),
(UUID(), 'course', 'EDUCATION', 'NOTE', 10),
(UUID(), 'books', 'EDUCATION', 'NOTE', 10),
(UUID(), 'RECHARGE', 'UTILITIES', 'TXN_TYPE', 5);