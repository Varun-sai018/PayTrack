# 02. Architecture and Design

## 1. System Architecture Overview

PayTrack is decomposed into four business microservices plus infrastructure services (Eureka, API Gateway, MySQL per service, RabbitMQ, Redis for caching). Communication patterns:

- **Client → Gateway**: HTTP over public-facing Nginx gateway port 8080 (dev) / 443 (prod Nginx).
- **Gateway → Services**: Reactive Spring Cloud Gateway routes using Eureka service discovery (`lb://`). Gateway strips client-provided identity headers (`X-User-Id`, `X-User-Role`, `X-Kyc-Status`, `X-Gateway-Secret`) and re-injects verified ones.
- **Service ↔ Service**: OpenFeign clients for synchronous calls (Transaction → Auth lookup, Transaction → Wallet transfer). Eureka registration and load balancing.
- **Service ↔ Service (Async)**: RabbitMQ topic exchange `payment.exchange` publishes `PaymentCompletedEvent`; `expense.payment.queue` consumes with dead-letter queue `expense.payment.dlq`.
- **Message Producer/Consumer Isolation**: Only `wallet-service` consumes `welcome.queue`. Only `expense-service` consumes `expense.payment.queue`.

---

## 2. Component Diagram

```
 Browser (React / Vite)
      | /api/**
      v
 Nginx (prod) / Vite proxy (dev)
      | /api/**
      v
 API Gateway :8080
   - JWT verification
   - Identity header stripping / injection (X-User-Id, X-User-Role, X-Kyc-Status, X-Gateway-Secret)
   - Routing to services via Eureka lb://
   - Never routes /internal/**
      +-------------------------------+
      v                               v
Auth 8081                    Wallet 8082
paytrack_auth                paytrack_wallet
Users table                  Wallets table
KYC Requests                 Ledger Entries table
Refresh Tokens               Payment Orders table
                              System Wallets (OPERATOR, BANK_SETTLEMENT)
         |                                       |
         |  AMQP: user.registered                |
         +-----------------------> Wallet listen welcome.queue
      v
Transaction 8083                              Expense 8084
paytrack_transaction                          paytrack_expense
Transactions table                            Expenses table
Recharges table                               Category Rules table
RabbitMQ: payment.completed                   Event listener for PaymentCompletedEvent
      |                                       |
      v                                       v
   OpenFeign calls Auth (/internal/auth/users/by-upi/{upiId})
   OpenFeign calls Wallet (/internal/wallets/transfer)
```

---

## 3. Infrastructure Components

| Component | Role | Technology | Default Port |
|---|---|---|---|
| API Gateway | Request routing, JWT verification, identity header injection | Spring Cloud Gateway | 8080 |
| Eureka Server | Service discovery & registration | Spring Cloud Netflix Eureka | 8761 |
| Auth Service | User registry, auth, KYC, refresh tokens | Spring Boot, MySQL, RabbitMQ | 8081 |
| Wallet Service | Wallet balances, ledger entries, payment orders, gateway-sandbox | Spring Boot, MySQL, RabbitMQ | 8082 |
| Transaction Service | P2P transfers, QR payments, mobile recharges | Spring Boot, MySQL, RabbitMQ, OpenFeign | 8083 |
| Expense Service | Expense records, category rules, analytics | Spring Boot, MySQL, RabbitMQ | 8084 |
| Database | Per-service schema isolation | MySQL 8 | 3306 |
| Message Broker | Event pub/sub for wallet creation and payment completion | RabbitMQ 3 | 5672 |
| Caching / Session Store | Redis (provisioned but currently unused by business logic) | Redis 7 | 6379 |

---

## 4. Cross-Cutting Design Rules (Non-Negotiable)

### 4.1 Money Precision
- All amounts stored and computed using `BigDecimal`, scale 2, `RoundingMode.HALF_UP`.
- Database column type `DECIMAL(15,2)`.
- No `double` or `float` in money-critical paths.

### 4.2 Balance Safety
- Wallet balance CHECK constraint: `balance >= 0` for user-owned wallets (`owner_type='USER'`).
- System wallets (`owner_type='SYSTEM'`) may go negative (e.g. `OPERATOR` pays recharges, `BANK_SETTLEMENT` funds top-ups).

### 4.3 Double-Entry Ledger
- Every money movement is atomic: one DEBIT and one CREDIT `ledger_entries` row per `txn_ref`, equal amounts, same DB transaction.
- No endpoint creates money directly except through gateway-verified top-up, internal transfer, recharge, or recharge reversal.

### 4.4 Idempotency
- Client-supplied `Idempotency-Key` (UUID) on all money-moving requests.
- Wallet internal transfer idempotent on `txn_ref`.
- Transaction service idempotent on `(user_id, idempotency_key)` + request hash.

### 4.5 Identity & Trust
- Gateway strips client-supplied `X-User-Id`, `X-User-Role`, `X-Kyc-Status`, `X-Gateway-Secret` on every request; re-injects verified values from JWT claims + env `GATEWAY_SECRET`.
- Every business service filter rejects requests without valid `X-Gateway-Secret` (except actuator paths).
- `/internal/**` endpoints require `X-Internal-Key` equal to env `INTERNAL_API_KEY`; never routed by gateway.

### 4.6 Schema & Schema Evolution
- New Flyway migrations only (`V2__...`). Never edit an already-applied migration.
- Wallet creation remains event-driven (`user.registered`), with lazy `getOrCreateWallet(userId)` fallback.

---

## 5. Concurrency & Consistency Model

### 5.1 Pessimistic Locking
- Internal wallet transfers lock both wallet rows in ascending ID order using `PESSIMISTIC_WRITE` to prevent deadlocks.
- `@Version` column acts as a second optimistic safeguard.

### 5.2 Dual Writes Prevention
- Double-entry ledger + wallet balance update occur within a single `@Transactional` method to avoid partial commits.

### 5.3 Transactional Outbox for RabbitMQ
- After wallet transfer commit, `payment.completed` is published to RabbitMQ and `event_published` flag is set to `true`.
- A `@Scheduled` job runs every 60 seconds to re-publish COMPLETED transactions where `event_published=false`.
