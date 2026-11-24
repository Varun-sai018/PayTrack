# 03. API Contracts

## 1. Conventions

- **Base Path**: All business endpoints are prefixed with `/api`. The gateway routes `/api/auth/**`, `/api/wallets/**`, `/api/transactions/**`, `/api/expenses/**`, and `/api/gateway-sandbox/**` to their respective services.
- **Response Wrapper**: All endpoints return `ApiResponse<T>`:
  ```json
  {
    "success": boolean,
    "data": T | null,
    "message": string,
    "errorCode": string | null,
    "timestamp": string,
    "details": array | null
  }
  ```
- **Status Codes**: 200 OK, 201 Created, 204 No Content, 400 Validation, 401 Unauthorized, 403 Forbidden, 404 Not Found, 409 Conflict, 422 Business Rule Violation, 503 Service Unavailable.
- **Pagination**: `page` (from 0), `size` (default 10, max 50).
- **Currency**: All amounts are in `INR` (₹).
- **Headers**:
  - `Authorization: Bearer <accessToken>` — for all authenticated endpoints.
  - `Idempotency-Key: <UUID>` — required for money-moving endpoints.
  - `X-Internal-Key: <INTERNAL_API_KEY>` — for `/internal/**` endpoints (gateway-secret filter bypassed).
  - `X-Gateway-Secret: <GATEWAY_SECRET>` — injected by gateway; validated by services.

---

## 2. Auth Service (`/api/auth`)

| Endpoint | Method | Auth | Request | Success | Errors |
|---|---|---|---|---|---|
| `POST /api/auth/register` | POST | Public | email, phone, password, firstName, lastName, role | 201 UserDto + refreshToken | 400, 409 |
| `POST /api/auth/login` | POST | Public | email, password | 200 accessToken + refreshToken + expiresAt | 400, 403 (ACCOUNT_BLOCKED) |
| `POST /api/auth/refresh` | POST | Public | refreshToken | 200 new accessToken | 401 |
| `POST /api/auth/logout` | POST | USER | refreshToken | 204 | 401 |
| `GET /api/auth/me` | GET | USER | — | UserDto incl. upiId, kycStatus, role | 401 |
| `POST /api/auth/kyc` | POST | USER | docType (AADHAAR/PAN/VOTER_ID), docNumber | 201 PENDING kyc_request | 400, 409 |
| `GET /api/auth/kyc/status` | GET | USER | — | status, remarks | 401 |
| `GET /api/auth/users/lookup?upiId=` | GET | USER | upiId query param | maskedName, upiId | 404 |
| `GET /api/auth/admin/users` | GET | ADMIN | page, size, status | Page<User> | 403 |
| `PUT /api/auth/admin/users/{id}/status` | PUT | ADMIN | body: ACTIVE or BLOCKED | User | 403, 404 |
| `GET /api/auth/admin/kyc` | GET | ADMIN | page, size, status | Page<KycRequest> | 403 |
| `PUT /api/auth/admin/kyc/{id}/review` | PUT | ADMIN | decision (APPROVED/REJECTED), remarks | KycRequest | 400, 403, 404 |
| `GET /internal/auth/users/by-upi/{upiId}` | GET | Internal key | — | userId, maskedName, accountStatus, kycStatus | 404 |

**Validation Rules**:
- Email: valid format, unique.
- Phone: 10 digits starting 6–9, unique.
- Password: min 8 chars, at least one upper, one lower, one digit.
- First/last name: 1–60 characters.
- docNumber: 6–12 alphanumeric; only last 4 stored.
- KYC PENDING or APPROVED user cannot resubmit (409 `DUPLICATE_RESOURCE`).

---

## 3. Wallet Service (`/api/wallets`)

| Endpoint | Method | Auth | Request | Success | Errors |
|---|---|---|---|---|---|
| `GET /api/wallets/me` | GET | USER | — | WalletDto | 401 |
| `GET /api/wallets/me/statement` | GET | USER | page, size, type (DEBIT/CREDIT), from, to | Page<LedgerEntry> newest first | 401 |
| `POST /api/wallets/topup/orders` | POST | USER, KYC | amount | orderId, amount, currency, keyId | 400, 403, 422 |
| `POST /api/wallets/topup/verify` | POST | USER, KYC | orderId, paymentId, signature | orderId, status PAID, balance | 400, 403, 404, 409, 422 |
| `POST /api/gateway-sandbox/pay` | POST | USER (non-prod) | orderId, method (UPI/CARD), instrument | status, paymentId, signature, failureReason | 400, 404, 409 |
| `POST /internal/wallets/transfer` | POST | Internal key | txnRef, fromUserId/fromSystemWallet, toUserId/toSystemWallet, amount, description | txnRef, status, senderBalanceAfter | 404, 409, 422 |

**Removed (M-03)**: `POST /api/wallets/credit`, `POST /api/wallets/debit`, `PUT /api/wallets/balance`, `GET /api/wallets/{id}/balance` (restricted to caller's own wallet or removed).

---

## 4. Transaction Service (`/api/transactions`)

| Endpoint | Method | Auth | Request | Success | Errors |
|---|---|---|---|---|---|
| `GET /api/transactions` | GET | USER | page, size | Page<Transaction> where user is sender or receiver | 401 |
| `GET /api/transactions/{id}` | GET | USER | path id (UUID or txnRef) | Transaction | 401, 404 |
| `POST /api/transactions/transfer` | POST | USER, KYC, IK | receiverUpiId, amount, note, category (optional) | 201 txnRef, status, type (P2P), amount, category, balanceAfter, createdAt | 400, 403, 404, 409, 422, 503 |
| `POST /api/transactions/qr/generate` | POST | USER | amount (optional) | qrPayload | 401 |
| `POST /api/transactions/qr/pay` | POST | USER, KYC, IK | qrPayload, amount (if not in QR), note, category (optional) | 201 txnRef, status, type (QR), amount, category, balanceAfter, createdAt | 400, 403, 404, 409, 422, 503 |
| `POST /api/transactions/recharge` | POST | USER, KYC, IK | mobileNumber, operator (AIRTEL/JIO/VI/BSNL), amount, category (optional) | 201 txnRef, status, operatorRef | 400, 403, 409, 422, 503 |
| `GET /api/transactions/admin/all` | GET | ADMIN | page, size, status, type, from, to | Page<Transaction> | 403 |
| `GET /api/transactions/admin/summary` | GET | ADMIN | — | counts and volume per day | 403 |

**Removed**: Generic `POST /api/transactions` (DEBIT/CREDIT/TRANSFER types).

**QR Payload Format**: `upi://pay?pa=<upiId>&pn=<name>&am=<amount>&cu=INR`

**Business Rules**:
- Cannot pay yourself (400).
- Amount between `paytrack.limits.min-amount` (1.00) and `paytrack.limits.max-transaction` (10000) else 422 `LIMIT_EXCEEDED`.
- Daily COMPLETED total must stay within `paytrack.limits.daily-total` (25000) else 422 `LIMIT_EXCEEDED`.
- `category` is one of: FOOD, TRANSPORT, HOUSING, ENTERTAINMENT, UTILITIES, HEALTHCARE, SHOPPING, EDUCATION, OTHER.

---

## 5. Expense Service (`/api/expenses`)

| Endpoint | Method | Auth | Request | Success | Errors |
|---|---|---|---|---|---|
| `GET /api/expenses` | GET | USER | page, size, month, category | Page<Expense> | 401 |
| `POST /api/expenses` | POST | USER | category, description, amount, note | Expense (source=MANUAL) | 400 |
| `PUT /api/expenses/txn/{txnRef}/category` | PUT | USER (own) | category | updated Expense (category_source=USER) | 400, 404 |
| `GET /api/expenses/summary` | GET | USER | — | totals | 401 |
| `DELETE /api/expenses/{id}` | DELETE | USER | — | 204 | 401, 404 (payment-linked cannot be deleted) |
| `GET /api/expenses/summary/monthly` | GET | USER | month (yyyy-MM) | total, byCategory (amount, percent) | 400 |
| `GET /api/expenses/summary/trend` | GET | USER | months (default 6, max 12) | monthly totals | 400 |

---

## 6. Error Codes

| Code | Meaning | HTTP Status |
|---|---|---|
| VALIDATION_ERROR | Request validation failure | 400 |
| UNAUTHORIZED | Missing or invalid authentication | 401 |
| FORBIDDEN | Insufficient role / KYC not approved / account blocked | 403 |
| KYC_NOT_APPROVED | KYC status is not APPROVED | 403 |
| ACCOUNT_BLOCKED | User account is BLOCKED | 403 |
| NOT_FOUND | Resource not found | 404 |
| DUPLICATE_RESOURCE | Resource already exists (e.g., email, phone, KYC resubmit) | 409 |
| IDEMPOTENCY_KEY_REUSED | Same idempotency key with different body | 409 |
| INSUFFICIENT_BALANCE | Wallet balance too low | 422 |
| LIMIT_EXCEEDED | Per-transaction or daily limit exceeded | 422 |
| INVALID_SIGNATURE | Payment signature mismatch | 400 |
| ORDER_NOT_FOUND | Payment order not found | 404 |
| ORDER_NOT_PAYABLE | Order cannot be paid (already paid/failed) | 409 |
| ORDER_ALREADY_PAID | Order already has PAID status | 409 |
| ORDER_FAILED | Order payment failed | 409 |
| SERVICE_UNAVAILABLE | Downstream dependency unavailable | 503 |

Each service has one `@RestControllerAdvice` mapping `ApiException(HttpStatus, errorCode, message)` to `ApiResponse<T>`.
