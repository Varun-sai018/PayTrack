# CLAUDE.md: PayTrack upgrade instructions (as-built baseline)

PayTrack is a digital wallet and expense tracker built as Spring Boot microservices with a React frontend, by a team of four under the TrainingMug AI Development Framework (ADF v1.0). All money is simulated.

**The application already exists** (repository `Varun-sai018/PayTrack`, branches `main` and `develop`, about 35 commits). Your job is NOT to build from scratch. Your job is to upgrade the existing code, one task at a time, to the target scope in this file. This file replaces the earlier greenfield version.

Last updated: 22 November 2026.

---

## 0. How you must work (read first)

You are an engineering assistant, not the engineer. The student owns the design and must understand every line you write.

**Reality check.** Section 3 describes the existing system from a written report, not from reading the code. Before changing anything, verify what you rely on against the real code, and tell the user about every difference. If this file and the code disagree, the code is the truth for "what exists" and this file is the truth for "what we want"; ask when unsure.

**Session start checklist (every session, in order):**
1. Read this file fully. Skim `docs/` for anything newer.
2. Run `git status` and `git branch --show-current`. Report anything unexpected.
3. Ask the user (if not already said): which task ID from section 14, and which owner they are (A, B, C or D). Work only inside that owner's service.
4. Before backend work that changes an API or table, confirm the guide has approved the updated documents (task M-02). If not confirmed, do not change contracts or schemas; you may still do task M-01.
5. Write a short plan first: files to change, assumptions, risks, questions. Proceed unless there is a real ambiguity.

**Working agreement:**
- One task at a time; stop when its Definition of Done (section 15) is met. Never start the next task.
- Do not edit another service's code unless the task says so. If another service must change, tell the user and stop.
- Keep changes small and reversible. Prefer new Flyway migrations (`V2`, `V3`, ...) over editing existing ones. Never edit an already-applied migration.
- Every feature gets tests. Run the service build and tests before you say a task is done and report the real result. Never claim tests passed without running them. If something cannot run, say exactly what was not run.
- Ask before: adding a dependency not already in the project, changing a port, route, table, endpoint or error code beyond what the task says, upgrading Spring Boot or Spring Cloud versions, and any destructive action (deleting files or data, force-push, history rewrite, resetting databases).
- Never commit secrets. Real values live in `docker/.env` (git-ignored). Commit only `docker/.env.example` with placeholders. If you find a hardcoded secret (JWT secret, DB password, API key) in code, report it and move it to configuration as part of the relevant task.
- Git: branch `feature/<service>-<task-id>` from `develop` (example `feature/wallet-M-04`). Commit style matches the existing history: `feat(wallet): ...`, `fix(gateway): ...`, `test(...)`, `docs`, `chore`, `ci(...)`, `build(...)`. Do not merge or push to `main` or `develop`. Push the feature branch only if asked.
- Keep answers short and concrete. Explain assumptions before coding and the result after.

---

## 1. Product summary and scope

Users register, complete simulated KYC (admin approves), add money to a wallet through a dummy payment gateway, pay other users by UPI ID or QR, recharge mobiles (simulated operator), and see spending by category. Every payment carries a category chosen at payment time (default "Others", auto-suggested by keyword rules, editable later). Admins review KYC, block users and monitor transactions.

Roles: `USER`, `ADMIN`. Currency: **INR** everywhere (the current USD default must be changed).

**Out of scope (do not build):** group expense splitting, CSV import, real bank or UPI or card integration, real KYC documents, custom user-defined categories, merchant accounts, mobile app, Kafka, new infrastructure beyond what exists, Spring Boot or Spring Cloud upgrades.

---

## 2. Non-negotiable rules

1. Money is `BigDecimal`, scale 2, `RoundingMode.HALF_UP`. Never `double` or `float`. DB `DECIMAL(15,2)`.
2. A user wallet balance can never go below zero (code check plus DB CHECK).
3. Every money movement is double-entry: one DEBIT and one CREDIT ledger entry per `txn_ref`, equal amounts, in one DB transaction.
4. Money-moving APIs are idempotent: client calls use `Idempotency-Key` (UUID); the Wallet internal transfer is idempotent on `txn_ref`.
5. No user-facing endpoint may create money or change a balance directly. Balance changes happen only through: gateway-verified top-up, internal transfer, recharge, and recharge reversal.
6. Never expose JPA entities; controllers use DTOs. No business logic in controllers. Layers: controller, service, repository.
7. No hardcoded values or magic numbers; limits, secrets, URLs and timeouts come from configuration.
8. Never log or return secrets, passwords, tokens, full document numbers or card numbers; mask sensitive values.
9. A microservice trusts identity headers only when the request carries the gateway secret (section 5.1). Internal endpoints (`/internal/**`) are never routed by the gateway and require `X-Internal-Key`.
10. Database per service; no cross-schema access. Cross-service links are plain ids, not foreign keys.
11. Only one service binds to a given RabbitMQ queue (a past bug: competing consumers stole messages).

---

## 3. As-built baseline (verify against the code)

| Item | As built (from the report) |
|---|---|
| Repo layout | `backend/` multi-module Maven (root and backend parent POMs), `frontend/`, root `docker-compose.yml` (MySQL, Redis, RabbitMQ only) |
| Versions | Java 17, Spring Boot 3.5.5, Spring Cloud 2025.0.0, Maven 3.9.6, MySQL 8, Flyway, Resilience4j, Auth0 `java-jwt` (HMAC256) |
| Modules and ports | `eureka-service` 8761, `api-gateway-service` **8089**, `auth-service` 8081, `wallet-service` 8082, `transaction-service` 8083, `expense-service` 8084. Frontend 3000. RabbitMQ 5672 (UI 15672). Redis 6379. MySQL 3306 |
| Java packages | `com.paytrack.eureka`, `com.paytrack.gateway`, `com.paytrack.auth`, `com.paytrack.wallet`, `com.paytrack.transaction`, `com.paytrack.expense` |
| Schemas | `paytrack_auth`, `paytrack_wallet`, `paytrack_transaction`, `paytrack_expense` (Flyway `V1__init_schema.sql` in each) |
| Ids | UUID stored as `VARCHAR(36)` (keep this; do not change to numeric) |
| Gateway | Spring Cloud Gateway (reactive), routes by Eureka `lb://` (AUTH-SERVICE, WALLET-SERVICE, TRANSACTION-SERVICE, EXPENSE-SERVICE), global `AuthenticationFilter` verifies the JWT, reads claim `userId`, adds header `X-User-Id`; CORS for `http://localhost:3000`; Resilience4j fallbacks |
| JWT | HS256, claims `sub`=email, `userId`, `role`, `iss`=paytrack-auth, expiry 24 h; BCrypt cost 10 |
| Service-side identity | `UserContextFilter` copies `X-User-Id` into `UserContextHolder` (ThreadLocal, cleared in `finally`) |
| Messaging | RabbitMQ topic exchange `notification.exchange`, queue `welcome.queue`, routing key `user.registered`; Auth publishes `UserRegisteredEvent`; Wallet consumes it (cross-package class mapping via `DefaultClassMapper.setIdClassMapping`) and creates a wallet |
| Sync calls | Transaction calls Wallet through OpenFeign (`WalletClient`) with `FeignClientInterceptor` propagating `X-User-Id`; compensating credit if the transaction row cannot be saved |
| Response shape | Every endpoint returns `ApiResponse<T>` (with timestamp and status) |
| Frontend | React 18.3, Vite 5, Tailwind 3, React Router 6, Axios; `AuthContext`, `services/api.js` (token in `localStorage`, 401 -> redirect to /login), `ProtectedRoute`, pages Login, Register, Dashboard, Wallet, Transactions, Expenses, Settings; Vite proxy `/api` -> `http://localhost:8089`; theme: background `#0A0F1A`, surface `#111827`, primary `#06D6A0`, accent `#3B82F6`, fonts Space Grotesk and JetBrains Mono |
| Existing endpoints | Auth: `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me`. Wallet: `GET /api/wallets/me`, `POST /api/wallets/credit`, `POST /api/wallets/debit`, `GET /api/wallets/{id}/balance`. Transactions: `GET /api/transactions`, `POST /api/transactions` (types DEBIT, CREDIT, TRANSFER), `GET /api/transactions/{id}`. Expenses: `GET /api/expenses`, `POST /api/expenses`, `GET /api/expenses/summary`, `DELETE /api/expenses/{id}` |
| Existing tables | `users`, `wallets`, `transactions`, `expenses` (columns as in the report; wallet currency default 'USD'; transaction status PENDING, COMPLETED, FAILED) |

**Known defects to fix (these are the priority):**
- Any logged-in user can call `POST /api/wallets/credit` or create a `CREDIT` transaction and mint money. "Add Funds" in the UI does this.
- Services trust `X-User-Id` from any caller; the service ports are directly reachable, so the header can be forged.
- The wallet keeps only a balance: no ledger, no locking, no idempotency (parallel requests can overdraw; retries can double charge).
- 24-hour token, no refresh token, no logout.
- Currency is USD; the product is INR/UPI.
- Transactions have no receiver, no category link and no limits; expenses are typed in by hand and not linked to payments.
- No automated tests, no Swagger, no Postman collection, no CI/CD, no deployment of the services (compose runs only infrastructure).
- Redis is listed in the stack but nothing in the report uses it (see M-01).

---

## 4. Target architecture

```
 Browser (React) --> Nginx (prod) / Vite proxy (dev)
                       |  /api/**
                       v
                 API Gateway :8089  (JWT check, header injection, routing)
     +---------------+--------------+---------------+
     v               v              v               v
  Auth 8081     Wallet 8082   Transaction 8083   Expense 8084      Eureka 8761
     |               |              |               |
 paytrack_auth  paytrack_wallet paytrack_transaction paytrack_expense   (MySQL)

 Transaction --Feign--> Auth     GET  /internal/auth/users/by-upi/{upiId}
 Transaction --Feign--> Wallet   POST /internal/wallets/transfer   (timeout, retry, circuit breaker)
 Auth        --AMQP---> Wallet   user.registered        (existing: wallet creation)
 Transaction --AMQP---> Expense  payment.completed      (new: expense recording)
```

Gateway routes (keep existing, add): `/api/auth/**`, `/api/wallets/**`, `/api/gateway-sandbox/**` (new, to WALLET-SERVICE), `/api/transactions/**`, `/api/expenses/**`. The gateway never routes any path containing `/internal/`.

Services find each other through Eureka (`lb://` or Feign `name=`). Keep the existing Eureka configuration.

Money transfer flow (UPI ID or QR):
1. Client `POST /api/transactions/transfer` with `Idempotency-Key`.
2. Transaction service returns the stored result if `(user_id, idempotency_key)` exists with the same request hash; same key with a different body returns 409 `IDEMPOTENCY_KEY_REUSED`.
3. Validate amount; sender is not receiver; `X-Kyc-Status` is `APPROVED`.
4. Feign to Auth by-upi lookup: receiver must exist, be ACTIVE and KYC APPROVED.
5. Check per-transaction and daily limits (sum of COMPLETED transactions since start of day in `paytrack.timezone`).
6. Save PENDING with a `txn_ref`.
7. Feign to Wallet internal transfer (timeout 3 s, one retry on connect or timeout only, circuit breaker). Wallet does DEBIT and CREDIT in one DB transaction.
8. Mark COMPLETED or FAILED (`failure_reason`). Circuit open or timeout after retry: FAILED `WALLET_UNAVAILABLE`, respond 503.
9. After commit, publish `payment.completed` to RabbitMQ and set `event_published=true`. A `@Scheduled` job (every 60 s) re-publishes COMPLETED transactions with `event_published=false`.
10. Respond 201 with the transaction and chosen category.

Recharge: save PENDING, debit the user wallet to the `OPERATOR` system wallet, call the `RechargeOperator` stub; if the stub fails, reverse with an internal transfer from `OPERATOR` back to the user using `txn_ref` `<ref>-REV`, mark FAILED. The old compensating-credit logic is replaced by this and by the atomic wallet transfer.

Add money: dummy gateway flow (section 9); counter-entry goes to the `BANK_SETTLEMENT` system wallet.

---

## 5. Cross-cutting rules

### 5.1 Identity and trust (task M-03)
- Gateway, for every request: first **remove** any client-supplied `X-User-Id`, `X-User-Role`, `X-Kyc-Status`, `X-Gateway-Secret`; then, after JWT verification, set `X-User-Id` (claim `userId`), `X-User-Role` (claim `role`), `X-Kyc-Status` (claim `kycStatus`) and `X-Gateway-Secret` (env `GATEWAY_SECRET`).
- Every business service has a filter that rejects (401) any non-actuator request lacking the correct `X-Gateway-Secret`, then fills `UserContextHolder` (user id, role, kyc status).
- Admin endpoints check `X-User-Role == ADMIN` in the service (and the gateway rejects `/admin/` paths for non-admins as a second layer).
- Docker Compose never publishes service ports (8081 to 8084, 8761); only nginx (and the gateway in dev) are reachable.
- `/internal/**` endpoints require header `X-Internal-Key` equal to env `INTERNAL_API_KEY`; the Feign interceptor adds it. Internal endpoints skip the gateway-secret filter but must still be unreachable from outside.

### 5.2 JWT
- Access token: 15 minutes (replace the hardcoded 86400000). Claims: `sub`=email, `userId`, `role`, `kycStatus`, `iss`, `exp`, `jti`. Keep `java-jwt` HMAC256; secret from env `JWT_SECRET` (at least 32 bytes), shared by gateway and auth only.
- Refresh token: random 256-bit, returned once, stored hashed (SHA-256) in `refresh_tokens`, valid 7 days, revoked on logout. `POST /api/auth/refresh` returns a new access token (reload `kycStatus` and `role` from the database).
- A KYC approval or block reaches the token at the next refresh (up to 15 minutes); accepted and documented.

### 5.3 Responses and errors
Keep `ApiResponse<T>`. Inspect its current fields; add `errorCode` (nullable) if missing without breaking existing clients. Errors return the same wrapper with `success=false`, `message`, `errorCode`, `data=null` (field errors in a `details` list for validation). Status codes: 200, 201, 204, 400 validation, 401, 403, 404, 409, 422 business rule, 503 dependency down.

Error codes: `VALIDATION_ERROR`, `UNAUTHORIZED`, `FORBIDDEN`, `KYC_NOT_APPROVED`, `ACCOUNT_BLOCKED`, `NOT_FOUND`, `DUPLICATE_RESOURCE`, `IDEMPOTENCY_KEY_REUSED`, `INSUFFICIENT_BALANCE`, `LIMIT_EXCEEDED`, `INVALID_SIGNATURE`, `ORDER_NOT_FOUND`, `ORDER_NOT_PAYABLE`, `ORDER_ALREADY_PAID`, `ORDER_FAILED`, `SERVICE_UNAVAILABLE`. Each service has one `@RestControllerAdvice` mapping a small `ApiException(HttpStatus, errorCode, message)` to the wrapper.

### 5.4 Conventions and configuration
- Pagination: `page` (from 0), `size` (default 10, max 50), existing `Page<T>` inside `ApiResponse`.
- Config keys (in `application.yml`, overridable by env): `paytrack.limits.min-amount=1.00`, `paytrack.limits.max-transaction=10000`, `paytrack.limits.daily-total=25000`, `paytrack.recharge.min=10`, `paytrack.recharge.max=2000`, `paytrack.timezone=Asia/Kolkata`.
- `txn_ref`: `TXN` + `yyyyMMdd` + 6 random digits; unique; regenerate on collision. The transaction row keeps its UUID `id` as well.
- UPI ID: first and last name lower-cased, joined with a dot, plus `@paytrack` (example `asha.rao@paytrack`); on collision add 3 random digits before the `@`.
- Masked receiver name: first letter of each word plus `***` (example `R*** K***`).
- Currency formatting in the UI: Indian grouping with the rupee symbol.

---

## 6. Data changes (new Flyway migrations; never edit applied ones)

### paytrack_auth (`V2__...`)
- `users`: add `phone` (10 digits, unique, nullable for old rows), `upi_id` (unique; backfill existing users), `kyc_status` (NOT_SUBMITTED, PENDING, APPROVED, REJECTED; default NOT_SUBMITTED), `account_status` (ACTIVE, BLOCKED; default ACTIVE).
- New `kyc_requests`: id UUID, user_id, doc_type (AADHAAR, PAN, VOTER_ID), doc_last4, status (PENDING, APPROVED, REJECTED), remarks, reviewed_by, submitted_at, reviewed_at; index (user_id, status).
- New `refresh_tokens`: id UUID, user_id, token_hash (unique), expires_at, revoked, created_at.
- Admin user created at startup from env `ADMIN_EMAIL` and `ADMIN_PASSWORD` if absent.

### paytrack_wallet (`V2__...`)
- `wallets`: add `owner_type` (USER or SYSTEM, default USER), `system_name` (nullable, unique), `version` BIGINT default 0; allow `user_id` NULL for system wallets (keep unique); change default currency to `INR` and update existing rows to `INR`; CHECK `owner_type='SYSTEM' OR balance >= 0`.
- Seed system wallets `OPERATOR` and `BANK_SETTLEMENT` (UUID ids; may go negative).
- New `ledger_entries`: id VARCHAR(36), wallet_id, txn_ref, entry_type (DEBIT or CREDIT), amount DECIMAL(15,2) CHECK > 0, balance_after, description, created_at; unique (wallet_id, txn_ref, entry_type); indexes (wallet_id, created_at), (txn_ref).
- New `payment_orders` (from the gateway module, adapted: `user_id VARCHAR(36)`): order_id unique, user_id, amount CHECK > 0, currency, status (CREATED, PAID, FAILED), payment_id unique, failure_reason, created_at, paid_at; index (user_id, created_at).
- Wallet creation stays event-driven, but also make `getOrCreateWallet(userId)` lazy so a lost message never leaves a user without a wallet.

### paytrack_transaction (`V2__...`)
- `transactions`: add `txn_ref` (unique), `idempotency_key`, `request_hash`, `receiver_user_id` NULL, `receiver_upi_id` NULL, `failure_reason` NULL, `requested_category` NULL, `event_published` BOOLEAN default FALSE; unique (user_id, idempotency_key); indexes (user_id, created_at), (receiver_user_id, created_at), status, (event_published, status); extend `type` with `P2P`, `QR`, `RECHARGE` (keep old values for existing rows); keep statuses PENDING, COMPLETED, FAILED; default currency INR.
- New `recharges`: id UUID, txn_id, mobile_number, operator (AIRTEL, JIO, VI, BSNL), operator_ref.

### paytrack_expense (`V2__...`)
- Keep the fixed category set; **add `EDUCATION`**: `FOOD`, `TRANSPORT`, `HOUSING`, `ENTERTAINMENT`, `UTILITIES`, `HEALTHCARE`, `SHOPPING`, `EDUCATION`, `OTHER`. UI labels: Food, Travel, Housing, Entertainment, Bills and Recharges, Health, Shopping, Education, Others. "Default category" means `OTHER`.
- `expenses`: add `txn_ref` (unique, nullable), `source` (PAYMENT or MANUAL; existing rows MANUAL), `category_source` (USER, RULE, DEFAULT, MANUAL); default currency INR.
- New `category_rules`: id UUID, keyword, category, match_on (NOTE or TXN_TYPE), priority. Seed (NOTE contains, case-insensitive): FOOD: lunch, dinner, breakfast, food, pizza, swiggy, zomato, cafe. TRANSPORT: uber, ola, bus, train, petrol, fuel, metro. SHOPPING: amazon, flipkart, shopping, clothes. UTILITIES: electricity, water, bill, recharge, rent. ENTERTAINMENT: movie, netflix, game. HEALTHCARE: doctor, medicine, pharmacy. EDUCATION: fees, course, books. TXN_TYPE RECHARGE: UTILITIES.
- Category choice priority: user selection, then rule match (first by priority), then `OTHER`.
- Expenses are recorded for the **sender** of P2P, QR and RECHARGE only. Receiving money and adding money are not expenses.

---

## 7. API changes

All responses use `ApiResponse<T>`. "KYC" = `X-Kyc-Status` must be `APPROVED` (else 403 `KYC_NOT_APPROVED`). "IK" = `Idempotency-Key` header required.

### 7.1 Auth
Keep: `POST /api/auth/register` (add required `phone`; response now also returns `refreshToken`; `GET /api/auth/me` now includes `upiId`, `kycStatus`, `role`), `POST /api/auth/login` (adds `refreshToken`; blocked user -> 403 `ACCOUNT_BLOCKED`).

| New endpoint | Auth | Request | Success | Errors |
|---|---|---|---|---|
| POST /api/auth/refresh | Public | refreshToken | new accessToken | 401 |
| POST /api/auth/logout | USER | refreshToken | 204 | 401 |
| POST /api/auth/kyc | USER | docType, docNumber | 201 PENDING | 400, 409 |
| GET /api/auth/kyc/status | USER | none | status, remarks | 401 |
| GET /api/auth/users/lookup?upiId= | USER | none | maskedName, upiId | 404 |
| GET /api/auth/admin/users | ADMIN | page, size, status | page of users | 403 |
| PUT /api/auth/admin/users/{id}/status | ADMIN | ACTIVE or BLOCKED | user | 403, 404 |
| GET /api/auth/admin/kyc | ADMIN | page, size, status | page | 403 |
| PUT /api/auth/admin/kyc/{id}/review | ADMIN | decision APPROVED or REJECTED, remarks | request | 400, 403, 404 |
| GET /internal/auth/users/by-upi/{upiId} | Internal key | none | userId, maskedName, accountStatus, kycStatus | 404 |

Public paths in the gateway: register, login, refresh (plus actuator). Validation: email valid and unique; phone 10 digits starting 6 to 9 and unique; password at least 8 characters with upper case, lower case and a digit; first and last name 1 to 60 characters; docNumber 6 to 12 alphanumeric (store only the last 4); a user with KYC PENDING or APPROVED cannot resubmit (409).

### 7.2 Wallet
Keep: `GET /api/wallets/me`. **Remove from public routing:** `POST /api/wallets/credit`, `POST /api/wallets/debit` (delete them, or move to `/internal/` guarded by the internal key if still needed), and restrict `GET /api/wallets/{id}/balance` to the caller's own wallet (or remove).

| New endpoint | Auth | Request | Success | Errors |
|---|---|---|---|---|
| GET /api/wallets/me/statement | USER | page, size, type (DEBIT/CREDIT), from, to | page of ledger entries (newest first) | 401 |
| POST /api/wallets/topup/orders | USER, KYC | amount | 201 orderId, amount, currency, keyId | 400, 403, 422 |
| POST /api/wallets/topup/verify | USER, KYC | orderId, paymentId, signature | orderId, status PAID, balance | 400, 403, 404, 409, 422 |
| POST /api/gateway-sandbox/pay | USER (non-prod only) | orderId, method UPI or CARD, instrument | status, paymentId, signature, failureReason | 400, 404, 409 |
| POST /internal/wallets/transfer | Internal key | txnRef, fromUserId or fromSystemWallet, toUserId or toSystemWallet, amount, description | txnRef, status, senderBalanceAfter | 404, 409, 422 |

Transfer rules: lock both wallet rows `PESSIMISTIC_WRITE` in ascending id order (avoids deadlocks) and keep `@Version` as a second safeguard; idempotent on `txnRef` (if entries exist, return the original result, write nothing); insufficient balance -> 422 `INSUFFICIENT_BALANCE`; frozen wallet -> 403; write DEBIT and CREDIT with `balance_after`, update both balances, all in one `@Transactional` method. The load test targets user-to-user transfers (system wallets are hot rows by design).

### 7.3 Transaction
**Remove** `POST /api/transactions` (generic DEBIT, CREDIT, TRANSFER). Keep `GET /api/transactions` (list where the caller is sender or receiver) and `GET /api/transactions/{id}` (also accept a `txnRef`).

| New endpoint | Auth | Request | Success | Errors |
|---|---|---|---|---|
| POST /api/transactions/transfer | USER, KYC, IK | receiverUpiId, amount, note, category (optional) | 201 txnRef, status, type, amount, category, balanceAfter, createdAt | 400, 403, 404, 409, 422, 503 |
| POST /api/transactions/qr/generate | USER | amount (optional) | qrPayload | 401 |
| POST /api/transactions/qr/pay | USER, KYC, IK | qrPayload, amount (if not in QR), note, category (optional) | 201 same as transfer | 400, 403, 404, 409, 422, 503 |
| POST /api/transactions/recharge | USER, KYC, IK | mobileNumber, operator, amount, category (optional) | 201 txnRef, status, operatorRef | 400, 403, 409, 422, 503 |
| GET /api/transactions/admin/all | ADMIN | page, size, status, type, from, to | page | 403 |
| GET /api/transactions/admin/summary | ADMIN | none | counts and volume per day | 403 |

QR payload: `upi://pay?pa=<upiId>&pn=<name>&am=<amount>&cu=INR` (`am` optional). `qr/pay` validates scheme `upi`, host `pay`, `pa` present, `cu` = INR; if `am` is present the request amount must be absent or equal (else 400); then it runs the transfer flow with type QR.

Recharge: `RechargeOperator` interface with `DummyRechargeOperator` (SUCCESS with `operatorRef` like `OP` + random digits; a mobile number ending `0000` returns FAILED `OPERATOR_DECLINED`). Mobile 10 digits; amount within `paytrack.recharge.min/max`.

Rules: cannot pay yourself (400); amount between min-amount and max-transaction else 422 `LIMIT_EXCEEDED`; daily COMPLETED total must stay within daily-total else 422 `LIMIT_EXCEEDED`. `category` is one of the category enum values.

### 7.4 Expense
Keep: `GET /api/expenses` (add optional `month`, `category`), `POST /api/expenses` (manual entry, `source=MANUAL`, no wallet movement), `GET /api/expenses/summary`, `DELETE /api/expenses/{id}` (only `MANUAL` expenses; payment-linked ones cannot be deleted).

| New endpoint | Auth | Request | Success | Errors |
|---|---|---|---|---|
| PUT /api/expenses/txn/{txnRef}/category | USER (own) | category | updated expense (`category_source=USER`) | 400, 404 |
| GET /api/expenses/summary/monthly | USER | month (yyyy-MM) | total, byCategory (amount, percent) | 400 |
| GET /api/expenses/summary/trend | USER | months (default 6, max 12) | monthly totals | 400 |

Percentages are rounded to 1 decimal; the total equals the sum of category amounts.

---

## 8. Event contracts (RabbitMQ)

- Existing: exchange `notification.exchange`, queue `welcome.queue`, key `user.registered`, consumer wallet-service only. Keep. Make the wallet consumer idempotent (unique `user_id`).
- New: topic exchange `payment.exchange` (durable), queue `expense.payment.queue` (durable) bound with key `payment.completed`, plus a dead-letter queue `expense.payment.dlq`. Declare the topology in both publisher and consumer (idempotent). Only expense-service consumes it.
- `PaymentCompletedEvent` fields: `eventId` (UUID), `txnRef`, `userId`, `type` (P2P, QR, RECHARGE), `amount`, `currency`, `note`, `requestedCategory` (nullable), `completedAt`.
- Use the same cross-package mapping technique already used for `UserRegisteredEvent` (`DefaultClassMapper` with `idClassMapping`) and add a test that deserializes a real message.
- Consumer behaviour: idempotent on `txn_ref` (unique constraint; a duplicate is acknowledged and ignored); category chosen by priority in section 6; after 3 failed attempts the message goes to the DLQ; log with `txnRef`.

---

## 9. Dummy payment gateway (task M-06)

A ready-made module is in `dummy-gateway/` (README inside). **Adapt it, do not rewrite it.** If the folder is missing, ask the user for it. What it provides: sandbox gateway behind a `PaymentGateway` interface; order creation, a sandbox checkout, HMAC-SHA256 signature of `orderId|paymentId`, idempotent verification with a row lock, tests.

It was written for a different identity model, so change these when integrating:
- User ids are **UUID strings**, not `long`: change `userId` types in `PaymentOrder`, repository, service, DTOs, `WalletService.topUp(String userId, ...)` and `getBalance(String userId)`, and the migration column to `VARCHAR(36)`.
- Remove `CurrentUser` and the `@AuthenticationPrincipal Jwt` usage; read the user id and KYC status from `UserContextHolder` (gateway headers, section 5.1).
- Replace its `ApiException` and error mapping with the project's `ApiException` and `ApiResponse` wrapper (section 5.3). Return `ApiResponse<T>` from the controllers.
- Package it under `com.paytrack.wallet.gateway`; renumber the Flyway migration; keep configuration keys `paytrack.gateway.provider`, `key-id`, `key-secret=${GATEWAY_KEY_SECRET}`.
- The sandbox controller must not exist under the `prod` profile.
- `topUp` writes BANK_SETTLEMENT DEBIT and user CREDIT through the same ledger code as the internal transfer, idempotent on `txnRef` (`TOPUP-<orderId>`).

Sandbox test instruments: UPI `success@paytrack` succeeds, `failure@paytrack` fails; card `4111111111111111` succeeds, `4000000000000002` fails (`CARD_DECLINED`), `4000000000009995` fails (`INSUFFICIENT_FUNDS`); anything else is `INVALID_INSTRUMENT`. The module's logic was checked against stand-in stubs only; its controllers, Mockito test, JPA mapping and SQL were not run, so run its tests after adapting.

---

## 10. Frontend changes (React 18, Vite, Tailwind; keep the current theme and components)

- `services/api.js`: add the refresh flow (on 401 try one refresh, retry once, else log out); send a fresh UUID `Idempotency-Key` per money action and reuse it on retry of the same action; tokens stay in `localStorage` (known trade-off, noted in docs).
- Show amounts in INR with Indian grouping everywhere; remove USD symbols and the "Withdraw" action (direct debit no longer exists).
- Role-aware navigation (USER vs ADMIN); a banner and disabled money actions while KYC is not approved.
- `Wallet.jsx` "Add Funds": replace the direct credit with Add Money: amount -> create order -> `SandboxCheckout` modal (UPI and Card tabs, lists the test instruments, shown only when `VITE_GATEWAY_MODE=sandbox`) -> verify -> updated balance.
- New pages: `Kyc`, `SendMoney`, `MyQr` (`qrcode.react`), `ScanPay` (`html5-qrcode` or paste), `Recharge`, `TransactionDetail` (editable category), `Analytics` (month picker, category doughnut, 6-month trend with Recharts), `Statement`, and admin pages `AdminDashboard`, `AdminKyc`, `AdminUsers`, `AdminTransactions`.
- `CategorySelect` shared component: preselects the auto-suggested or default category (the suggestion is computed on the client with the same seed keywords or simply shows "Others" until the server returns the chosen one; the server decides).
- The existing Expenses page keeps manual entry and shows payment-linked expenses with their category source.
- Every page: loading, error and empty states; form validation mirroring the backend; responsive layout; messages from the backend `message`.
- Env: `VITE_API_BASE_URL` (default `/api`), `VITE_GATEWAY_MODE`.

---

## 11. Docker, CI/CD and deployment

- Every service gets (or confirms) a multi-stage Dockerfile that builds from the repo root with the multi-module POM (`-pl backend/<module> -am`), runs on JRE 17 as a non-root user, with a `HEALTHCHECK` on `/actuator/health`. Frontend: Node build, Nginx runtime.
- `docker/docker-compose.yml` (or extend the root one): `mysql`, `rabbitmq`, `redis` (only if M-01 keeps it), `eureka-service`, `api-gateway-service`, `auth-service`, `wallet-service`, `transaction-service`, `expense-service`, `nginx`. Services register with Eureka using `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://eureka-service:8761/eureka` and a Docker-reachable hostname or IP. Only nginx publishes a port. MySQL init script creates the four schemas with one user per schema. Use `depends_on` health conditions.
- `docker/.env.example`: MySQL passwords per schema, `JWT_SECRET`, `GATEWAY_SECRET`, `INTERNAL_API_KEY`, `GATEWAY_KEY_SECRET`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`, RabbitMQ credentials, limits, `SPRING_PROFILES_ACTIVE`. Placeholders only.
- GitHub Actions: `ci.yml` (each module `mvn -B verify`; frontend `npm ci`, lint, test, build) on push and pull request; `deploy.yml` on `main` (build and push images to GitHub Container Registry, SSH to the VM, `docker compose pull && up -d`, health check through the gateway). Secrets live in repository secrets only.
- Deployment target: Docker Compose on one cloud VM. Document it in `docs/10_DEPLOYMENT.md`.

---

## 12. Testing and quality bar

- Unit tests for every service-layer rule; `@WebMvcTest` for validation and status codes; Testcontainers MySQL integration tests for Wallet and Transaction; an AMQP test for the event mapping.
- **Consistency test:** 100 parallel transfers among 4 wallets; assert total money unchanged, no negative balance, debits equal credits per `txn_ref`, and repeated requests with the same idempotency key move money once.
- At least 70% line coverage on service layers. Run `mvn -B verify` for the module before declaring a task done.
- k6 load test for user-to-user transfers (target p95 under 500 ms at 100 concurrent users) in `tests/load/`.
- Swagger (springdoc) on every service; a Postman collection and environment in `postman/` (no secrets).

---

## 13. Git and documentation
- Conventional commits in the existing style (section 0). Small, meaningful commits. Never commit `.env`, build output or secrets.
- Keep `docs/` current: `docs/AS_BUILT.md` (written in M-01), and the ADF files 01 to 07 updated in M-02 and whenever an API, table or flow changes. Update this file's affected section in the same change and tell the user.

---

## 14. Task list

Owners: A = Auth + Gateway + Eureka + shared frontend shell, B = Wallet, C = Transaction, D = Expense + analytics + CI. Frontend tasks belong to the owner of the matching backend.

| ID | Task | Pri | Est. | Depends on | Owner |
|---|---|---|---|---|---|
| M-01 | Baseline audit: tag `v0.1.0-mvp`; run every module build; verify section 3 against the code; record differences, hardcoded secrets and Redis usage in `docs/AS_BUILT.md`; remove Redis from compose and code if unused | High | 0.5d | none | A (all review) |
| M-02 | Update ADF documents 01 to 07 to the as-built architecture plus this scope; guide approval | High | 1d | M-01 | All |
| M-03 | Security hardening (section 5.1 and 5.2): strip and re-add identity headers, gateway secret filter, internal key, close the public wallet credit/debit and generic transaction create, 15-minute JWT, refresh and logout, secrets to env | High | 2d | M-01 | A (B for wallet routes) |
| M-04 | Wallet: INR migration, system wallets, ledger entries, locking, lazy wallet creation, statement endpoint | High | 2d | M-03 | B |
| M-05 | Wallet: internal atomic transfer, idempotent on `txnRef` | High | 1.5d | M-04 | B |
| M-06 | Wallet: dummy gateway add-money (section 9) | High | 1.5d | M-04 | B |
| M-07 | Auth: phone, UPI ID, KYC submit and review, admin user management, lookup, internal by-upi, `kycStatus` claim, admin bootstrap | High | 2d | M-03 | A |
| M-08 | Transaction: transfer by UPI ID with idempotency, limits, Feign calls, circuit breaker; remove generic create | High | 2.5d | M-05, M-07 | C |
| M-09 | Transaction: QR generate and pay | Med | 1d | M-08 | C |
| M-10 | Transaction: recharge with operator stub and reversal | Med | 1d | M-08 | C |
| M-11 | Transaction: publish `payment.completed` after commit, outbox flag and retry job | High | 1d | M-08 | C |
| M-12 | Expense: category rules, consume `payment.completed`, edit category, source fields, DLQ | High | 2d | M-11 | D |
| M-13 | Expense: monthly summary and trend | Med | 1d | M-12 | D |
| M-14 | UI base: refresh flow, INR formatting, role-aware nav, KYC banner, error handling | High | 1d | M-03 | A |
| M-15 | UI: KYC page and admin pages (KYC, users, transactions) | High | 2d | M-07, M-14 | A |
| M-16 | UI: Add Money with sandbox checkout, statement, dashboard updates | High | 2d | M-06, M-14 | B |
| M-17 | UI: Send, My QR, Scan and Pay, Recharge, transaction detail with category edit | High | 3d | M-09, M-10, M-14 | C |
| M-18 | UI: Analytics page and Expenses page adaptation | Med | 1.5d | M-13, M-14 | D |
| M-19 | Automated tests: unit, controller, Testcontainers, AMQP mapping, parallel-transfer consistency test | High | 2d | M-08 | B, C |
| M-20 | Swagger on all services and the Postman collection | Med | 1d | M-17 | All |
| M-21 | Dockerfiles for all modules, full Compose, env files (section 11) | High | 1.5d | M-20 | A |
| M-22 | GitHub Actions CI/CD and cloud VM deployment with health checks | High | 1.5d | M-21 | D, A |
| M-23 | k6 load test and test report | Med | 1d | M-22 | B |
| M-24 | README, screenshots, demo video, presentation | High | 2d | M-22 | All |

Suggested pacing for four people working in parallel: days 1 to 2 = M-01 to M-03; days 3 to 6 = M-04 to M-07; days 7 to 9 = M-08 to M-13 and M-14; days 10 to 12 = M-15 to M-20; days 13 to 14 = M-21 to M-24 (about two to three weeks end to end).

**How to run one task:** restate the task and acceptance points -> verify the real code involved -> list files, migrations and assumptions -> implement (entity, DTO, repository, service, controller, validation, exception handling, Swagger) -> add tests -> run the module build -> update docs and this file if a contract changed -> commit on the feature branch -> short report (what changed, how to try it, what was and was not tested) -> stop.

---

## 15. Definition of Done (per task)
Requirement implemented; backend complete; UI complete when the task includes UI; API documented in Swagger and in this file; validation and exception handling added; unit tests written and passing; manual check described to the user; committed on the feature branch with clear messages; documentation updated. Pull request review and merge are done by the team, not by you.

---

## 16. Prompt templates the user can paste

**Start a task**
> Read CLAUDE.md. I am owner [A/B/C/D]. Do task [M-xx]. Verify the real code first, show your plan and assumptions, then implement only this task, add tests, run the build, and stop.

**Audit before changing anything**
> Read CLAUDE.md section 3. Compare it with the actual code in [module] and list every difference, hardcoded secret and unused dependency. Do not change code.

**Fix a failing build or test**
> Read CLAUDE.md. In [module], `mvn -B verify` fails with: [paste error]. Find the cause, fix only what is needed, explain it, and re-run the build.

**Review before pull request**
> Read CLAUDE.md. Review my changes on this branch against sections 2, 5 and 15. List problems by severity. Do not change code unless I ask.

**Explain code to me**
> Explain [file or class] step by step as if I must present it to my guide, including the design pattern and why it is used.

---

## 17. Open items to confirm with the user (do not guess)
- Whether Redis is really used (decided in M-01).
- Exact shape of the existing `ApiResponse` and `UserContextHolder` (read them in M-01 and follow them).
- Final project name if "PayTrack" changes (packages stay `com.paytrack`).
- Cloud VM provider and domain (affects Nginx and TLS).
- Guide's approval of the as-built architecture and the add-money change (M-02).
