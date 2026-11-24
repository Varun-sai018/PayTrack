# 01. Project Charter and Scope

## 1. Executive Summary & Product Vision
**PayTrack** is a enterprise-grade digital wallet and expense tracking platform built as Spring Boot microservices with a React frontend under the TrainingMug AI Development Framework (ADF v1.0). The platform provides a full financial lifecycle experience in simulated INR currency, including:
- Simulated user KYC verification workflow with document verification and administrative review.
- Double-entry ledger accounting guaranteeing financial integrity and real-time balance tracking.
- Top-up wallet funding via a dummy payment gateway with signature verification and sandbox checkout.
- Instant peer-to-peer (P2P) transfers using unique UPI IDs and QR codes.
- Mobile recharges with simulated operator stubs and automatic balance reversals.
- Automated payment expense categorization using keyword rule matching, manual expense logging, and monthly spending analytics.

All money values in PayTrack are strictly simulated for training and demonstration purposes.

---

## 2. Business Objectives & Success Criteria
1. **Financial Precision**: Zero currency calculation drift and strict prevention of negative wallet balances or un-factored debits/credits.
2. **Security & Isolation**: Complete isolation of microservice internal endpoints, header validation at API Gateway, and short-lived JWT authentication.
3. **High Concurrency & Consistency**: Maintain p95 response time under 500ms for P2P transfers under 100 concurrent users while guaranteeing double-entry balance consistency.
4. **Resilience**: Zero message loss for payment events via asynchronous dead-letter queues and transactional outbox pattern.

---

## 3. User Roles & Access Control
- **USER**: Standard registered user who completes simulated KYC to unlock wallet features (top-up, P2P send/receive, QR pay, mobile recharge, transaction statement, expense analytics, manual expense management).
- **ADMIN**: Administrative user responsible for reviewing KYC requests, managing user account statuses (ACTIVE / BLOCKED), and monitoring platform-wide transaction summaries and volumes.

---

## 4. Product Scope Definition

### 4.1 In-Scope Features
| Domain | Capabilities |
|---|---|
| **Identity & Access** | Registration (Email, Phone, Name), Login, 15-minute JWT Access Tokens, 7-day Refresh Tokens, Logout, Role-aware routing. |
| **KYC Management** | User submission of document type (AADHAAR, PAN, VOTER_ID) & number; Admin review dashboard for Approval/Rejection with remarks. |
| **Wallet & Ledger** | User wallets & System wallets (`OPERATOR`, `BANK_SETTLEMENT`); Double-entry `ledger_entries` (DEBIT and CREDIT per `txn_ref`); Wallet statements; Lock-based atomic balance updates. |
| **Add Money / Gateway** | Payment order creation, sandbox checkout modal (UPI & Card instruments), HMAC-SHA256 signature verification, settlement counter-entry to `BANK_SETTLEMENT`. |
| **P2P & QR Payments** | UPI ID lookup, QR payload generation/parsing, idempotent transfer execution (`Idempotency-Key`), per-transaction & daily limit checks. |
| **Mobile Recharge** | Simulated operator integration (`DummyRechargeOperator`), failure detection, automatic balance reversal from `OPERATOR` system wallet. |
| **Expense & Analytics** | Auto-recording sender expenses on `payment.completed` event; Priority keyword rule engine; Manual expense logging; User category edits; Monthly summary & 6-month trend analytics. |
| **Administration** | User block/unblock, KYC decisioning dashboard, transaction monitoring and summary reports. |

### 4.2 Out-of-Scope Features (Non-Goals)
- Real banking, real UPI network (NPCI), or real credit card processing integrations.
- Real KYC identity document verification or OCR scanner.
- Group expense splitting (e.g., Splitwise-like features).
- CSV / Excel import and export.
- Custom user-defined category creation (categories are locked to the standard set).
- Merchant account support or multi-currency processing (INR only).
- Mobile native application (React web frontend only).
- Kafka messaging or new infrastructure beyond MySQL, RabbitMQ, Redis, and Nginx.

---

## 5. System Constraints & Conventions
- **Currency**: `INR` everywhere (symbol: ₹). Displayed using Indian grouping format (`en-IN`).
- **Numeric Standard**: All financial amounts use `BigDecimal` with scale 2 and `RoundingMode.HALF_UP`. Database representation is `DECIMAL(15,2)`.
- **Primary Identifiers**: External IDs (User ID, Order ID, Event ID, Transaction Ref) are UUID strings (`VARCHAR(36)`). Database primary keys use auto-incrementing `BIGINT`.
- **Database Isolation**: Database per microservice (`paytrack_auth`, `paytrack_wallet`, `paytrack_transaction`, `paytrack_expense`). No cross-database foreign key constraints.
- **Service Stack**: Java 17, Spring Boot 3.5.5, Spring Cloud 2025.0.0, React 18, Vite 5, Tailwind CSS 3, MySQL 8, RabbitMQ 3.
