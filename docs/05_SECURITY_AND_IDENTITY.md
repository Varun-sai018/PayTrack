# 05. Security and Identity

## 1. Identity Header Lifecycle (Gateway)
For every incoming request, the API Gateway:
1. **Removes** any client-supplied `X-User-Id`, `X-User-Role`, `X-Kyc-Status`, `X-Gateway-Secret`.
2. Verifies the JWT access token (HS256, `JWT_SECRET` env, 15-minute expiry).
3. Sets verified headers from claims:
   - `X-User-Id` → JWT claim `userId`
   - `X-User-Role` → JWT claim `role`
   - `X-Kyc-Status` → JWT claim `kycStatus`
   - `X-Gateway-Secret` → env `GATEWAY_SECRET`
4. Routes to the business service via Eureka `lb://`.

Public paths (no JWT required): register, login, refresh (plus actuator).

## 2. Service-Side Trust Filter
Every business service (auth, wallet, transaction, expense) has a filter that:
- Rejects (401) any non-actuator request lacking the correct `X-Gateway-Secret`.
- Fills `UserContextHolder` with user id, role, and kyc status from the verified headers.
- `/internal/**` endpoints skip the gateway-secret filter but require `X-Internal-Key` equal to env `INTERNAL_API_KEY`.

Admin endpoints additionally check `X-User-Role == ADMIN` in the service (gateway rejects `/admin/` paths for non-admins as second layer).

## 3. JWT Token Model

### Access Token
- Algorithm: HS256, secret from env `JWT_SECRET` (minimum 32 bytes).
- Claims: `sub` (email), `userId`, `role`, `kycStatus`, `iss`, `exp`, `jti`.
- Expiry: 15 minutes (replaces hardcoded 24-hour value).
- Shared by gateway and auth service only.
- A KYC approval or block reaches the token at next refresh (up to 15 minutes lag; accepted and documented).

### Refresh Token
- Random 256-bit value, returned once at login/register.
- Stored hashed (SHA-256) in `refresh_tokens` table (`token_hash` unique).
- Valid 7 days, revoked on logout.
- `POST /api/auth/refresh` returns a new access token (reloads `kycStatus` and `role` from database).
- `POST /api/auth/logout` revokes the refresh token and returns 204.

## 4. Internal Endpoint Security
- Gateway never routes any path containing `/internal/`.
- `/internal/**` endpoints require `X-Internal-Key` header matching env `INTERNAL_API_KEY`.
- Feign interceptor automatically adds `X-Internal-Key`.
- Docker Compose never publishes service ports 8081–8084 or Eureka port 8761; only Nginx (and gateway in dev) are reachable externally.

## 5. Secrets Management
- Real values live in `docker/.env` (git-ignored).
- Repository contains only `docker/.env.example` with placeholders:
  - `JWT_SECRET`, `GATEWAY_SECRET`, `INTERNAL_API_KEY`, `GATEWAY_KEY_SECRET`
  - `ADMIN_EMAIL`, `ADMIN_PASSWORD`
  - MySQL passwords per schema
  - RabbitMQ credentials
  - Limits, `SPRING_PROFILES_ACTIVE`
- No hardcoded secrets in any source code file.
