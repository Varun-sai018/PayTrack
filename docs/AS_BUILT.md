# AS_BUILT.md — PayTrack Baseline Audit (Task M-01)

**Generated:** 2026-11-22  
**Branch:** feature/audit-M-01  
**Tag:** v0.1.0-mvp (created)  
**Auditor:** Owner A (Auth + Gateway + Eureka + shared frontend shell)

---

## 1. Repository Layout (Matches Section 3)

| Item | CLAUDE.md Section 3 | Actual Code |
|------|---------------------|-------------|
| Root | `backend/` multi-module Maven, `frontend/`, root `docker-compose.yml` | ✅ Confirmed. `backend/pom.xml` aggregates 6 modules. `frontend/` at root. `docker-compose.yml` at root. |
| Versions | Java 17, Spring Boot 3.5.5, Spring Cloud 2025.0.0, Maven 3.9.6, MySQL 8, Flyway, Resilience4j, Auth0 `java-jwt` 4.2.1 | ✅ Confirmed in `backend/pom.xml` and `backend/auth-service/pom.xml` (and all module POMs). Java 11 is the runtime in this environment but POM declares 17. |
| Modules & Ports | `eureka-service` 8761, `api-gateway-service` 8089, `auth-service` 8081, `wallet-service` 8082, `transaction-service` 8083, `expense-service` 8084, Frontend 3000, RabbitMQ 5672/15672, Redis 6379, MySQL 3306 | ⚠️ **Port Mismatch:** Gateway runs on **8080** in code (`application.yaml`), not 8089. Compose maps gateway to host port **8080**. Services use default Spring Boot ports (8081–8084) in their `application.yml`. Eureka is 8761. |
| Java Packages | `com.paytrack.eureka`, `com.paytrack.gateway`, `com.paytrack.auth`, `com.paytrack.wallet`, `com.paytrack.transaction`, `com.paytrack.expense` | ✅ Confirmed. |
| Schemas | `paytrack_auth`, `paytrack_wallet`, `paytrack_transaction`, `paytrack_expense` (Flyway `V1__init_schema.sql` each) | ⚠️ **Partial:** Only `auth-service` has `V1__init_users_table.sql`. Other services use `hibernate.ddl-auto: update` / `validate` and have **no Flyway migrations**. Wallet/Transaction/Expense tables are auto-created by Hibernate. |
| IDs | UUID stored as `VARCHAR(36)` | ⚠️ **Mismatch:** Entities use `@GeneratedValue(strategy = GenerationType.IDENTITY)` → `Long` primary key (`id`), plus a separate `userId` / `user_id` column as `VARCHAR(255)` holding the UUID string. The "id" is numeric; the business ID is the UUID string. |
| Gateway | Spring Cloud Gateway (reactive), routes by Eureka `lb://`, global `AuthenticationFilter` verifies JWT, reads `userId`, adds `X-User-Id`; CORS for `http://localhost:3000`; Resilience4j fallbacks | ✅ Mostly confirmed. Gateway runs on port 8080 (not 8089). `AuthenticationFilter` adds `X-User-Id`, `X-User-Email`, `X-User-Roles`. **Missing:** No stripping of client-supplied `X-User-Id`/`X-User-Roles`/`X-Kyc-Status`/`X-Gateway-Secret` before adding its own. No `X-Gateway-Secret` header added. |
| JWT | HS256, claims `sub`=email, `userId`, `role`, `iss`=paytrack-auth, expiry 24h; BCrypt cost 10 | ✅ Confirmed in `JwtUtil.java`. Uses `java-jwt` (Auth0). `issuer` in config is `ecommerce-app` (auth) / `paytrack-app` (wallet/transaction/expense) — **inconsistent**. Access token expiry hardcoded to 86400000 (24h). No `kycStatus` claim. No `jti`. |
| Service-side identity | `UserContextFilter` copies `X-User-Id` into `UserContextHolder` (ThreadLocal, cleared in `finally`) | ✅ Confirmed. Each service has its own copy of `UserContextHolder` and `UserContextFilter`. **Critical gap:** No validation of `X-Gateway-Secret` — services trust `X-User-Id` from any caller. Ports 8081–8084 are published in Compose (directly reachable). |
| Messaging | RabbitMQ topic exchange `notification.exchange`, queue `welcome.queue`, routing key `user.registered`; Auth publishes `UserRegisteredEvent`; Wallet consumes it (cross-package mapping via `DefaultClassMapper`) | ✅ Confirmed. Wallet uses `DefaultClassMapper` with `idClassMapping` for `com.paytrack.auth.event.UserRegisteredEvent`. |
| Sync calls | Transaction → Wallet via OpenFeign (`WalletClient`) with `FeignClientInterceptor` propagating `X-User-Id`; compensating credit if transaction row cannot be saved | ✅ Confirmed. `WalletClient` calls `/api/wallets/credit` and `/api/wallets/debit`. `TransactionService` has compensating logic (lines 68–92). **Issue:** Compensation uses same Feign client (not idempotent, no `txn_ref`). |
| Response shape | Every endpoint returns `ApiResponse<T>` (timestamp, status) | ✅ Confirmed. Each service has its own `ApiResponse` class (duplicated). Fields: `success`, `data`, `message`, `timestamp`. **Missing:** `errorCode` field. |
| Frontend | React 18.3, Vite 5, Tailwind 3, React Router 6, Axios; `AuthContext`, `services/api.js`, `ProtectedRoute`, pages Login, Register, Dashboard, Wallet, Transactions, Expenses, Settings; Vite proxy `/api` → `http://localhost:8089`; theme: bg `#0A0F1A`, surface `#111827`, primary `#06D6A0`, accent `#3B82F6`, fonts Space Grotesk / JetBrains Mono | ⚠️ **Partial:** React 18.2, Vite 5.0.8, Tailwind 3.4.0, Router 6.20.0, Axios 1.6.2. `AuthContext`, `api.js`, `ProtectedRoute` exist. Pages: Login, Register, Dashboard, Wallet, Transactions, Expenses, Settings. **Vite proxy targets `http://localhost:8080`** (not 8089). Theme colors match. Fonts not explicitly verified. |
| Existing Endpoints | Auth: register, login, me. Wallet: me, credit, debit, balance. Transactions: list, create (DEBIT/CREDIT/TRANSFER), get. Expenses: list, create, summary, delete. | ✅ Confirmed. Wallet has extra `POST /api/wallets` (create), `PUT /api/wallets/balance`. Transactions has `PUT /api/transactions/{id}/status`, `GET /api/transactions/wallet/{walletId}`. |
| Existing Tables | `users`, `wallets`, `transactions`, `expenses` (wallet currency default USD; txn status PENDING/COMPLETED/FAILED) | ✅ Confirmed. `users` table has `id` (BIGINT), `user_id` (VARCHAR), email, password, full_name, role, created_at, updated_at, active. `wallets` has `id` (BIGINT), `user_id` (VARCHAR), `balance`, `currency` (default USD), created_at, updated_at. `transactions` has `id` (BIGINT), `user_id`, `transaction_id` (UUID), `wallet_id`, description, amount, type, timestamp, status. `expenses` has `id` (BIGINT), `user_id`, `expense_id` (UUID), `wallet_id`, category, description, amount, status, timestamp. |

---

## 2. Known Defects (Verified)

| Defect | Status | Evidence |
|--------|--------|----------|
| Any logged-in user can call `POST /api/wallets/credit` or create `CREDIT` transaction and mint money | ✅ **Confirmed** | `WalletController` exposes `/credit` and `/debit` with `permitAll()`. `TransactionController.createTransaction()` accepts `CREDIT` type and calls `walletClient.credit()`. No auth check on wallet mutation. |
| Services trust `X-User-Id` from any caller; ports directly reachable | ✅ **Confirmed** | `UserContextFilter` reads `X-User-Id` without validating `X-Gateway-Secret`. Compose publishes ports 8081–8084. |
| Wallet keeps only balance: no ledger, no locking, no idempotency | ✅ **Confirmed** | `Wallet` entity has only `balance`. No `ledger_entries` table. `credit()`/`debit()` are simple `@Transactional` read-modify-write — race conditions possible. No `idempotency_key` or `txn_ref`. |
| 24-hour token, no refresh token, no logout | ⚠️ **Partial** | JWT access token is 24h. `JwtUtil` generates refresh token but **it is never stored or validated**. `AuthController` has no `/refresh` or `/logout`. Frontend `AuthContext.logout()` only clears localStorage. |
| Currency is USD; product is INR/UPI | ✅ **Confirmed** | `Wallet.currency` defaults to "USD". Frontend `formatCurrency` uses 'USD'. No UPI logic exists. |
| Transactions have no receiver, no category link, no limits | ✅ **Confirmed** | `Transaction` entity has no `receiver_user_id`, `category`, `txn_ref`, `idempotency_key`, limits. |
| Expenses typed in by hand, not linked to payments | ✅ **Confirmed** | `Expense` entity has no `txn_ref`, `source`. Created via `POST /api/expenses` manually. |
| No automated tests, no Swagger, no Postman, no CI/CD, compose runs only infra | ✅ **Confirmed** | Only 2 test classes exist (`ApiGatewayServiceApplicationTests`, `AuthServiceApplicationTests`) — both empty context-load tests. `springdoc-openapi` dependency present but no Swagger UI config verified. No GitHub Actions. Compose includes services but they haven't been built as images. |
| Redis listed but unused | ✅ **Confirmed** | Only `auth-service` has `RedisConfig` and `spring.data.redis` config. **No code injects or uses `RedisTemplate`**. Other services don't configure Redis. |

---

## 3. Hardcoded Secrets Found

| File | Secret | Value (Base64) | Decoded |
|------|--------|----------------|---------|
| `backend/auth-service/src/main/resources/application.yml` | `jwt.secret` | `a2V5LWlzLWEtc2VjcmV0LWZvci1obGQtand0LXNpZ25pbmctdHdvLWh1bmRyZWQtYml0cy1sb25nLXNlY3JldA==` | `key-is-a-secret-for-hld-jwt-signing-two-hundred-bits-long-secret` (32+ chars) |
| `backend/wallet-service/src/main/resources/application.yml` | `jwt.secret` | Same as above | Same |
| `backend/transaction-service/src/main/resources/application.yml` | `jwt.secret` | Same as above | Same |
| `backend/expense-service/src/main/resources/application.yml` | `jwt.secret` | Same as above | Same |
| `backend/api-gateway-service/src/main/resources/application.yaml` | `jwt.secret` | Same as above | Same |
| `docker-compose.yml` | MySQL root/user passwords | `rootpassword`, `paytrack123` | Plaintext |
| `docker-compose.yml` | RabbitMQ credentials | `guest` / `guest` | Default |

**All JWT secrets are identical across services and committed to git.** They must be moved to `docker/.env` (git-ignored) and referenced via `${JWT_SECRET}`.

---

## 4. Redis Usage Audit

**Finding: Redis is configured only in `auth-service` and is NOT used in any business logic.**

- `auth-service/src/main/java/com/paytrack/auth/config/RedisConfig.java` defines a `RedisTemplate<String, Object>` bean.
- `auth-service/src/main/resources/application.yml` configures `spring.data.redis.host/port`.
- **No other service** has Redis config or dependency.
- **No code** in `auth-service` (or any service) injects `RedisTemplate` or uses it for caching, sessions, rate-limiting, or OTP storage.
- The `otp.expiration-minutes: 5` config exists but no OTP implementation is present.

**Recommendation:** Remove Redis from `auth-service` (dependency, config, `RedisConfig.java`) and from `docker-compose.yml` unless a future task (e.g., refresh token blacklist, rate limiting) explicitly requires it. Current code does not use it.

---

## 5. Key Differences from CLAUDE.md Section 3 (Summary)

| Area | CLAUDE.md Says | Reality |
|------|----------------|---------|
| Gateway port | 8089 | **8080** |
| Flyway migrations | All 4 services have `V1__init_schema.sql` | **Only auth-service** has Flyway; others use `ddl-auto: update`/`validate` |
| ID type | UUID as `VARCHAR(36)` PK | **Numeric `Long` PK + UUID `user_id` column** |
| Gateway header stripping | Strips client headers, adds `X-Gateway-Secret` | **Does not strip; does not add `X-Gateway-Secret`** |
| Service gateway-secret filter | Validates `X-Gateway-Secret` | **Missing entirely** |
| JWT claims | `sub`, `userId`, `role`, `iss`, `kycStatus`, `jti` | **Missing `kycStatus`, `jti`; issuer inconsistent** |
| JWT expiry | 15 min (target) | **24h (current)** |
| Refresh token | Stored hashed, 7 days, revocable | **Generated but never stored/validated** |
| `ApiResponse` | Has `errorCode` | **Missing `errorCode`** |
| Wallet currency | USD (current), INR (target) | **USD** |
| Vite proxy | `http://localhost:8089` | **`http://localhost:8080`** |
| Compose ports | Only nginx/gateway published | **All service ports (8081–8084, 8761) published** |

---

## 6. Build Verification

**Maven Wrapper Issue:** The `mvnw` script in each module is missing the `maven-wrapper.jar` (only `maven-wrapper.properties` exists). Maven is not installed in the environment. **Cannot run `mvn -B verify` or `mvn -B test` at this time.**

**Workaround:** The `target/` directories contain compiled classes, indicating the code **has been built previously** (likely in the student's local environment). The `test_results.json` at the backend root shows integration test results from a prior run against running services.

**Recommendation:** Install Maven (`apt-get install maven` or download wrapper jar) to enable builds in this environment. For now, the audit relies on static code analysis and the existing `test_results.json`.

---

## 7. Dummy Gateway Module

**Status:** `dummy-gateway/` folder **does not exist** in the repository. Per CLAUDE.md Section 9, this module must be provided by the user for Task M-06.

---

## 8. Next Steps (for Task M-02 and beyond)

1. **Guide approval** of this AS_BUILT.md (Task M-02 prerequisite).
2. Update ADF documents 01–07 to reflect as-built architecture + target scope.
3. Resolve Maven wrapper / build environment before M-03.
4. Address hardcoded secrets: create `docker/.env.example` with placeholders, move real values to `docker/.env` (git-ignored).
5. Decide on Redis: remove from compose and auth-service (recommended) or implement a use case.
6. Align gateway port: change to 8089 in code and compose, or update CLAUDE.md to 8080.
7. Add Flyway migrations to wallet/transaction/expense services (baseline `V1`, then `V2` for schema changes per Section 6).

---

## 9. File List for Reference

### Backend Core
- `backend/pom.xml` — parent POM with versions
- `backend/auth-service/` — Auth module
- `backend/wallet-service/` — Wallet module
- `backend/transaction-service/` — Transaction module
- `backend/expense-service/` — Expense module
- `backend/api-gateway-service/` — Gateway module
- `backend/eureka-service/` — Eureka module
- `docker-compose.yml` — Infrastructure + services (ports published)

### Key Source Files Reviewed
- `backend/auth-service/src/main/java/com/paytrack/auth/entity/User.java`
- `backend/auth-service/src/main/resources/db/migration/V1__init_users_table.sql`
- `backend/auth-service/src/main/java/com/paytrack/auth/util/JwtUtil.java`
- `backend/auth-service/src/main/java/com/paytrack/auth/AuthController.java`
- `backend/auth-service/src/main/java/com/paytrack/auth/AuthService.java`
- `backend/auth-service/src/main/java/com/paytrack/auth/config/RedisConfig.java`
- `backend/auth-service/src/main/resources/application.yml`
- `backend/wallet-service/src/main/java/com/paytrack/wallet/entity/Wallet.java`
- `backend/wallet-service/src/main/java/com/paytrack/wallet/service/WalletService.java`
- `backend/wallet-service/src/main/java/com/paytrack/wallet/controller/WalletController.java`
- `backend/wallet-service/src/main/java/com/paytrack/wallet/filter/UserContextFilter.java`
- `backend/wallet-service/src/main/java/com/paytrack/wallet/UserContextHolder.java`
- `backend/wallet-service/src/main/java/com/paytrack/wallet/config/RabbitMQConfig.java`
- `backend/wallet-service/src/main/java/com/paytrack/wallet/listener/UserRegisteredEventListener.java`
- `backend/wallet-service/src/main/resources/application.yml`
- `backend/transaction-service/src/main/java/com/paytrack/transaction/entity/Transaction.java`
- `backend/transaction-service/src/main/java/com/paytrack/transaction/service/TransactionService.java`
- `backend/transaction-service/src/main/java/com/paytrack/transaction/client/WalletClient.java`
- `backend/transaction-service/src/main/java/com/paytrack/transaction/controller/TransactionController.java`
- `backend/transaction-service/src/main/resources/application.yml`
- `backend/expense-service/src/main/java/com/paytrack/expense/entity/Expense.java`
- `backend/expense-service/src/main/resources/application.yml`
- `backend/api-gateway-service/src/main/java/com/paytrack/gateway/filter/JwtAuthenticationFilter.java`
- `backend/api-gateway-service/src/main/resources/application.yaml`

### Frontend
- `frontend/package.json`
- `frontend/src/services/api.js`
- `frontend/src/context/AuthContext.jsx`
- `frontend/src/pages/Dashboard.jsx`
- `frontend/src/pages/Wallet.jsx`

---

**End of AS_BUILT.md**