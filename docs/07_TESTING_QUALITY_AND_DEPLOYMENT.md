# 07. Testing, Quality, and Deployment

## 1. Testing Strategy

### 1.1 Unit Tests
- Service-layer rules for every service (wallet locking, transfer idempotency, limit checks, category rules, recharge reversal).
- `@WebMvcTest` for controller validation and status codes.
- Mockito mocks for downstream Feign clients and RabbitTemplate.

### 1.2 Integration Tests
- Testcontainers MySQL integration tests for Wallet and Transaction services.
- AMQP test: deserialize a real `PaymentCompletedEvent` message using `DefaultClassMapper` cross-package mapping.

### 1.3 Consistency Test
- 100 parallel transfers among 4 wallets.
- Assertions:
  - Total money across all wallets unchanged.
  - No wallet balance goes below zero.
  - For every `txn_ref`, debits equal credits.
  - Repeated requests with the same idempotency key move money exactly once.

### 1.4 Coverage Target
- At least 70% line coverage on service layers.
- Run `mvn -B verify` for each module before declaring a task done.

### 1.5 Load Test (k6)
- `tests/load/` directory.
- Target: p95 under 500 ms at 100 concurrent users for user-to-user transfers.

### 1.6 API Documentation
- Swagger (springdoc-openapi) on every service.
- Postman collection and environment in `postman/` (no secrets).

---

## 2. Docker and Containerization

### 2.1 Dockerfiles
- Every service: multi-stage Dockerfile building from repo root (`-pl backend/<module> -am`), runs on JRE 17 as non-root user, `HEALTHCHECK` on `/actuator/health`.
- Frontend: Node build stage, Nginx runtime stage.

### 2.2 docker-compose.yml
Services: mysql-auth, mysql-wallet, mysql-transaction, mysql-expense, redis (only if kept), rabbitmq, eureka-service, api-gateway-service, auth-service, wallet-service, transaction-service, expense-service, nginx.
- Services register with Eureka: `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://eureka-service:8761/eureka`.
- Only nginx publishes a port externally; service ports 8081–8084 and Eureka 8761 not published.
- MySQL init script creates four schemas with one user per schema.
- `depends_on` health conditions used.

### 2.3 docker/.env.example
Placeholders only:
- MySQL passwords per schema
- `JWT_SECRET`, `GATEWAY_SECRET`, `INTERNAL_API_KEY`, `GATEWAY_KEY_SECRET`
- `ADMIN_EMAIL`, `ADMIN_PASSWORD`
- RabbitMQ credentials
- Limits, `SPRING_PROFILES_ACTIVE`
- Real values in git-ignored `docker/.env`.

---

## 3. CI/CD (GitHub Actions)

### 3.1 ci.yml (push + pull request)
- Backend: `mvn -B verify` for each module.
- Frontend: `npm ci`, lint, test, build.

### 3.2 deploy.yml (on main push)
- Build and push images to GitHub Container Registry.
- SSH to cloud VM.
- `docker compose pull && docker compose up -d`.
- Health check through the gateway.
- Secrets live in repository secrets only.

---

## 4. Deployment Target
- Docker Compose on a single cloud VM.
- Documented in `docs/10_DEPLOYMENT.md`.

---

## 5. Quality Bar Summary
| Item | Requirement |
|---|---|
| Money type | `BigDecimal`, scale 2, `HALF_UP`; DB `DECIMAL(15,2)` |
| Balance safety | Code check + DB CHECK constraint |
| Double-entry | DEBIT + CREDIT per `txn_ref`, one DB transaction |
| Idempotency | `Idempotency-Key` header; idempotent on `txn_ref` |
| Tests | Unit + controller + Testcontainers + AMQP + consistency |
| Coverage | ≥ 70% service layer lines |
| Docs | Swagger on all services, Postman collection |
