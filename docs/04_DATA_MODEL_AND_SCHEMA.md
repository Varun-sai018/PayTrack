# 04. Data Model and Schema Design

## 1. Schema Evolution Strategy
- All schema changes introduced via new Flyway migrations (`V2__...`).
- Never edit an already-applied migration (`V1__...`).
- Database per service: `paytrack_auth`, `paytrack_wallet`, `paytrack_transaction`, `paytrack_expense`.
- All monetary columns use `DECIMAL(15,2)`.
- UUIDs stored as `VARCHAR(36)`.
- Default currency changed from `USD` to `INR` in `wallets` table; existing rows updated to `INR`.

---

## 2. paytrack_auth (V2__init_auth_extensions.sql)

### 2.1 users — ALTER TABLE
| Column | Type | Constraint | Notes |
|---|---|---|---|
| `phone` | VARCHAR(15) | UNIQUE, NULLABLE | 10 digits starting 6–9; nullable for existing rows |
| `upi_id` | VARCHAR(100) | UNIQUE | Backfilled for existing users on migration |
| `kyc_status` | VARCHAR(20) | NOT NULL, DEFAULT 'NOT_SUBMITTED' | NOT_SUBMITTED / PENDING / APPROVED / REJECTED |
| `account_status` | VARCHAR(20) | NOT NULL, DEFAULT 'ACTIVE' | ACTIVE / BLOCKED |

### 2.2 kyc_requests — NEW TABLE
| Column | Type | Constraint | Notes |
|---|---|---|---|
| `id` | VARCHAR(36) | PK | UUID |
| `user_id` | VARCHAR(36) | NOT NULL | References users.user_id (logical, no FK) |
| `doc_type` | VARCHAR(20) | NOT NULL | AADHAAR / PAN / VOTER_ID |
| `doc_last4` | VARCHAR(4) | NOT NULL | Last 4 chars of docNumber only |
| `status` | VARCHAR(20) | NOT NULL, DEFAULT 'PENDING' | PENDING / APPROVED / REJECTED |
| `remarks` | VARCHAR(500) | NULLABLE | Admin review remarks |
| `reviewed_by` | VARCHAR(36) | NULLABLE | Admin user id |
| `submitted_at` | TIMESTAMP | NOT NULL | Auto-set |
| `reviewed_at` | TIMESTAMP | NULLABLE | Set on review |

Index: `(user_id, status)`

### 2.3 refresh_tokens — NEW TABLE
| Column | Type | Constraint | Notes |
|---|---|---|---|
| `id` | VARCHAR(36) | PK | UUID |
| `user_id` | VARCHAR(36) | NOT NULL | References users.user_id |
| `token_hash` | VARCHAR(255) | UNIQUE | SHA-256 of refresh token |
| `expires_at` | TIMESTAMP | NOT NULL | Created at + 7 days |
| `revoked` | BOOLEAN | NOT NULL DEFAULT FALSE | Revoked on logout |
| `created_at` | TIMESTAMP | NOT NULL | Auto-set |

### 2.4 Admin Bootstrap
At application startup: create admin user from env `ADMIN_EMAIL` and `ADMIN_PASSWORD` if no ADMIN user exists.

---

## 3. paytrack_wallet (V2__init_wallet_extensions.sql)

### 3.1 wallets — ALTER TABLE
| Column | Type | Constraint | Notes |
|---|---|---|---|
| `owner_type` | VARCHAR(10) | NOT NULL DEFAULT 'USER' | USER / SYSTEM |
| `system_name` | VARCHAR(50) | UNIQUE, NULLABLE | System wallet name (OPERATOR, BANK_SETTLEMENT) |
| `version` | BIGINT | NOT NULL DEFAULT 0 | Optimistic locking safeguard |
| `currency` | VARCHAR(3) | NOT NULL DEFAULT 'INR' | Existing rows updated to INR |

- `user_id` made NULLABLE for system wallets (keep UNIQUE constraint).
- CHECK: `owner_type = 'SYSTEM' OR balance >= 0`

### 3.2 System Wallets Seeded
| system_name | owner_type | Initial Balance | Notes |
|---|---|---|---|
| `OPERATOR` | SYSTEM | 0 | May go negative (funds recharges) |
| `BANK_SETTLEMENT` | SYSTEM | 0 | May go negative (funds top-ups via gateway) |

### 3.3 ledger_entries — NEW TABLE
| Column | Type | Constraint | Notes |
|---|---|---|---|
| `id` | VARCHAR(36) | PK | UUID |
| `wallet_id` | VARCHAR(36) | NOT NULL | References wallets.user_id |
| `txn_ref` | VARCHAR(50) | NOT NULL | Transaction reference (`TXN` + yyyyMMdd + 6 digits) |
| `entry_type` | VARCHAR(10) | NOT NULL | DEBIT / CREDIT |
| `amount` | DECIMAL(15,2) | CHECK > 0 | Positive amount only |
| `balance_after` | DECIMAL(15,2) | NOT NULL | Wallet balance after this entry |
| `description` | VARCHAR(255) | NULLABLE | Human-readable description |
| `created_at` | TIMESTAMP | NOT NULL | Auto-set |

Unique: `(wallet_id, txn_ref, entry_type)`
Indexes: `(wallet_id, created_at)`, `(txn_ref)`

### 3.4 payment_orders — NEW TABLE
| Column | Type | Constraint | Notes |
|---|---|---|---|
| `order_id` | VARCHAR(36) | UNIQUE | UUID |
| `user_id` | VARCHAR(36) | NOT NULL | References wallets.user_id |
| `amount` | DECIMAL(15,2) | CHECK > 0 | |
| `currency` | VARCHAR(3) | NOT NULL DEFAULT 'INR' | |
| `status` | VARCHAR(20) | NOT NULL | CREATED / PAID / FAILED |
| `payment_id` | VARCHAR(50) | UNIQUE | Gateway payment id |
| `failure_reason` | VARCHAR(100) | NULLABLE | e.g. CARD_DECLINED, INSUFFICIENT_FUNDS |
| `created_at` | TIMESTAMP | NOT NULL | |
| `paid_at` | TIMESTAMP | NULLABLE | Set when status = PAID |

Index: `(user_id, created_at)`

### 3.5 Wallet Creation
- Event-driven on `user.registered` (existing).
- Lazy `getOrCreateWallet(userId)` fallback so a lost message never leaves a user without a wallet.

---

## 4. paytrack_transaction (V2__init_transaction_extensions.sql)

### 4.1 transactions — ALTER TABLE
| Column | Type | Constraint | Notes |
|---|---|---|---|
| `txn_ref` | VARCHAR(50) | UNIQUE | `TXN` + yyyyMMdd + 6 random digits |
| `idempotency_key` | VARCHAR(36) | NOT NULL | Client UUID |
| `request_hash` | VARCHAR(255) | NOT NULL | Hash of request body for idempotency |
| `receiver_user_id` | VARCHAR(36) | NULLABLE | Receiver of P2P/QR transfer |
| `receiver_upi_id` | VARCHAR(100) | NULLABLE | Receiver UPI ID |
| `failure_reason` | VARCHAR(255) | NULLABLE | Reason if FAILED |
| `requested_category` | VARCHAR(20) | NULLABLE | Chosen category |
| `event_published` | BOOLEAN | NOT NULL DEFAULT FALSE | Outbox flag for payment.completed |
| `type` | VARCHAR(20) | Extended | P2P / QR / RECHARGE added to existing values |
| `currency` | VARCHAR(3) | NOT NULL DEFAULT 'INR' | |

Unique: `(user_id, idempotency_key)`
Indexes: `(user_id, created_at)`, `(receiver_user_id, created_at)`, `status`, `(event_published, status)`

### 4.2 recharges — NEW TABLE
| Column | Type | Constraint | Notes |
|---|---|---|---|
| `id` | VARCHAR(36) | PK | UUID |
| `txn_id` | VARCHAR(36) | NOT NULL | References transactions.id (logical) |
| `mobile_number` | VARCHAR(15) | NOT NULL | 10 digits |
| `operator` | VARCHAR(20) | NOT NULL | AIRTEL / JIO / VI / BSNL |
| `operator_ref` | VARCHAR(50) | NOT NULL | `OP` + random digits |

---

## 5. paytrack_expense (V2__init_expense_extensions.sql)

### 5.1 Category Set (final)
FOOD, TRANSPORT, HOUSING, ENTERTAINMENT, UTILITIES, HEALTHCARE, SHOPPING, EDUCATION, OTHER.
UI labels: Food, Travel, Housing, Entertainment, Bills and Recharges, Health, Shopping, Education, Others.
Default category = OTHER.

### 5.2 expenses — ALTER TABLE
| Column | Type | Constraint | Notes |
|---|---|---|---|
| `txn_ref` | VARCHAR(50) | UNIQUE, NULLABLE | Links to payment transaction |
| `source` | VARCHAR(10) | NOT NULL, DEFAULT 'MANUAL' | PAYMENT / MANUAL |
| `category_source` | VARCHAR(10) | NOT NULL | USER / RULE / DEFAULT / MANUAL |
| `currency` | VARCHAR(3) | NOT NULL DEFAULT 'INR' | |

### 5.3 category_rules — NEW TABLE
| Column | Type | Constraint | Notes |
|---|---|---|---|
| `id` | VARCHAR(36) | PK | UUID |
| `keyword` | VARCHAR(100) | NOT NULL | Case-insensitive substring match |
| `category` | VARCHAR(20) | NOT NULL | One of category enum |
| `match_on` | VARCHAR(20) | NOT NULL | NOTE / TXN_TYPE |
| `priority` | INT | NOT NULL | Lower number = higher priority |

### 5.4 Seeded Category Rules
| Match On | Category | Keywords (case-insensitive contains) | Priority |
|---|---|---|---|
| NOTE | FOOD | lunch, dinner, breakfast, food, pizza, swiggy, zomato, cafe | 1 |
| NOTE | TRANSPORT | uber, ola, bus, train, petrol, fuel, metro | 1 |
| NOTE | SHOPPING | amazon, flipkart, shopping, clothes | 1 |
| NOTE | UTILITIES | electricity, water, bill, recharge, rent | 1 |
| NOTE | ENTERTAINMENT | movie, netflix, game | 1 |
| NOTE | HEALTHCARE | doctor, medicine, pharmacy | 1 |
| NOTE | EDUCATION | fees, course, books | 1 |
| TXN_TYPE | UTILITIES | RECHARGE | 1 |

**Category Choice Priority**: User selection > Rule match (first by priority) > OTHER (default).

**Expense Recording Scope**: P2P, QR, and RECHARGE sender only. Receiving money and adding money are NOT expenses.

---

## 6. Event Contracts (RabbitMQ)

### 6.1 Existing — notification.exchange
| Exchange | Queue | Routing Key | Producer | Consumer |
|---|---|---|---|---|
| notification.exchange | welcome.queue | user.registered | auth-service | wallet-service |

- Wallet consumer made idempotent on `user_id` (unique constraint).
- Cross-package class mapping via `DefaultClassMapper.setIdClassMapping` for `UserRegisteredEvent`.

### 6.2 New — payment.exchange
| Exchange | Queue | Routing Key | Producer | Consumer | DLQ |
|---|---|---|---|---|---|
| payment.exchange | expense.payment.queue | payment.completed | transaction-service | expense-service | expense.payment.dlq |

**PaymentCompletedEvent Fields**:
| Field | Type | Notes |
|---|---|---|
| `eventId` | UUID | |
| `txnRef` | String | Transaction reference |
| `userId` | String | Sender user id |
| `type` | String | P2P / QR / RECHARGE |
| `amount` | BigDecimal | Scale 2 |
| `currency` | String | INR |
| `note` | String | Transaction note |
| `requestedCategory` | String | Nullable |
| `completedAt` | Timestamp | |

- Declare topology idempotently in both publisher and consumer.
- Consumer idempotent on `txn_ref` (unique constraint; duplicate → acknowledged and ignored).
- After 3 failed attempts → DLQ.
- Log with `txnRef`.

### 6.3 Class Mapping
Same cross-package mapping technique as `UserRegisteredEvent` (`DefaultClassMapper` with `idClassMapping`).
Add AMQP test that deserializes a real message.

---

## 7. Seed Data Summary

| Table | Seed |
|---|---|
| users | Admin bootstrap from env `ADMIN_EMAIL` / `ADMIN_PASSWORD` |
| wallets | `OPERATOR` and `BANK_SETTLEMENT` system wallets |
| category_rules | 9 rules as listed in section 5.4 |
| wallets.currency | All existing rows updated to `INR` |
