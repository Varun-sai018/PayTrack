# 06. Frontend Specification

## 1. Tech Stack
- React 18.2, Vite 5, Tailwind CSS 3, React Router 6, Axios.
- Theme: background `#0A0F1A`, surface `#111827`, primary `#06D6A0`, accent `#3B82F6`, fonts Space Grotesk and JetBrains Mono.
- Environment variables: `VITE_API_BASE_URL` (default `/api`), `VITE_GATEWAY_MODE`.

## 2. Authentication Flow
- Token stored in `localStorage` (known trade-off documented).
- `authApi.login` returns accessToken + refreshToken.
- On 401: attempt one token refresh; if refresh fails or returns 401, logout and redirect to `/login`.
- Each money action sends a fresh UUID `Idempotency-Key`; reuse the same key on retry of the same action.

## 3. Role-Aware Navigation
- USER: Wallet, Send Money, My QR, Scan & Pay, Recharge, Transaction Detail, Analytics, Expenses, Statement, Settings, KYC.
- ADMIN: Admin Dashboard, Admin KYC, Admin Users, Admin Transactions.
- KYC banner shown when KYC status is not APPROVED; money actions disabled until KYC approved.

## 4. Currency Formatting
- All amounts displayed in INR with Indian grouping (`en-IN`) and rupee symbol (₹).
- USD symbols removed; "Withdraw" action removed.

## 5. Pages & Components

### Core Pages
| Page | Route | Owner | Description |
|---|---|---|---|
| Login | /login | Public | Email/password login |
| Register | /register | Public | Registration with phone |
| Dashboard | /dashboard | USER/ADMIN | Balance, recent transactions, expenses, quick actions |
| Wallet | /wallet | USER | Wallet balance, statement |
| SendMoney | /send-money | USER | P2P transfer by UPI ID |
| MyQr | /my-qr | USER | Generate QR payload (`qrcode.react`) |
| ScanPay | /scan-pay | USER | Scan QR or paste payload (`html5-qrcode`) |
| Recharge | /recharge | USER | Mobile recharge with operator stub |
| TransactionDetail | /transactions/:id | USER | View transaction; editable category |
| Analytics | /analytics | USER | Month picker, category doughnut, 6-month trend (Recharts) |
| Expenses | /expenses | USER | Manual entry + payment-linked expenses with category source |
| Statement | /wallet/statement | USER | Ledger entries paginated |
| Kyc | /kyc | USER | Submit KYC doc; view status |
| AdminDashboard | /admin/dashboard | ADMIN | Platform stats |
| AdminKyc | /admin/kyc | ADMIN | Review KYC requests |
| AdminUsers | /admin/users | ADMIN | User list, block/unblock |
| AdminTransactions | /admin/transactions | ADMIN | Transaction monitoring |

### Shared Components
- `CategorySelect`: Preselects auto-suggested or default category; client computes suggestion with same seed keywords; server decides final category.
- Error/loading/empty states on every page; form validation mirroring backend; responsive layout; backend `message` shown to user.

## 6. Wallet "Add Money" Flow (replaces direct credit)
1. Amount → `POST /api/wallets/topup/orders` → orderId, keyId.
2. `SandboxCheckout` modal (UPI and Card tabs, test instruments listed, shown only when `VITE_GATEWAY_MODE=sandbox`).
3. Sandbox returns paymentId + signature.
4. `POST /api/wallets/topup/verify` → orderId, status PAID, updated balance.

## 7. Sandbox Test Instruments
- UPI: `success@paytrack` (succeeds), `failure@paytrack` (fails).
- Card: `4111111111111111` (succeeds), `4000000000000002` (fails CARD_DECLINED), `4000000000009995` (fails INSUFFICIENT_FUNDS); anything else → INVALID_INSTRUMENT.
