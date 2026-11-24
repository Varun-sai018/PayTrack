export interface User {
  id?: number;
  userId: string;
  email: string;
  fullName: string;
  phone?: string;
  upiId?: string;
  role: string;
  kycStatus: string;
  accountStatus?: string;
  active?: boolean;
}

export interface Profile {
  id: string;
  full_name?: string | null;
  email?: string | null;
  phone?: string | null;
  upi_id?: string | null;
  kyc_status?: string | null;
  role?: string | null;
  created_at?: string | null;
}

export interface AdminUser {
  id?: number | string;
  userId: string;
  email: string;
  fullName: string;
  phone?: string;
  upiId?: string;
  role: string;
  kycStatus: string;
  accountStatus?: string;
  active?: boolean;
  createdAt?: string;
}

export interface KycItem {
  id: string;
  userId: string;
  docType: "AADHAAR" | "PAN" | "VOTER_ID" | string;
  docLast4: string;
  status: "PENDING" | "APPROVED" | "REJECTED" | string;
  remarks?: string;
  reviewedBy?: string;
  submittedAt?: string;
  reviewedAt?: string;
  createdAt?: string;
}

export interface TransactionSummary {
  date?: string;
  totalTransactions: number;
  totalVolume: number;
  countByType: Record<string, number>;
  volumeByType: Record<string, number>;
  recentTransactions: Transaction[];
}

export interface ServiceHealth {
  name: string;
  key: string;
  endpoint: string;
  status: "UP" | "DOWN";
  latencyMs: number;
  lastChecked: string;
  error?: string;
}

export interface Wallet {
  id: string;
  user_id: string;
  balance: number;
  currency: string;
  status: string;
  created_at?: string;
  updated_at?: string;
}

export interface LedgerEntry {
  id: string;
  wallet_id?: string;
  type: string;
  entry_type: "CREDIT" | "DEBIT";
  amount: number;
  balance_after: number;
  description?: string;
  created_at: string;
}

export interface Transaction {
  id: string;
  txn_ref?: string;
  user_id: string;
  receiver_user_id?: string | null;
  receiver_upi_id?: string | null;
  sender_upi_id?: string | null;
  type: string;
  status: string;
  amount: number;
  description?: string;
  category?: string;
  created_at: string;
}

export interface Expense {
  id: string;
  user_id?: string;
  amount: number;
  category: string;
  description?: string;
  source?: string;
  txn_ref?: string;
  created_at: string;
}

const TOKEN_KEY = "paytrack_token";
const REFRESH_TOKEN_KEY = "paytrack_refresh_token";
const USER_KEY = "paytrack_user";

export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(TOKEN_KEY);
}

export function getRefreshToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(REFRESH_TOKEN_KEY);
}

export function getUser(): User | null {
  if (typeof window === "undefined") return null;
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

export function isAuthenticated(): boolean {
  return Boolean(getToken() && getUser());
}

export function setAuth(accessToken: string, refreshToken: string, user: User) {
  if (typeof window === "undefined") return;
  localStorage.setItem(TOKEN_KEY, accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function clearAuth() {
  if (typeof window === "undefined") return;
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

export async function apiFetch<T = any>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getToken();
  const headers = new Headers(options.headers || {});
  if (!headers.has("Content-Type") && !(options.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }
  if (token && !headers.has("Authorization")) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const url = path.startsWith("http") ? path : path.startsWith("/") ? path : `/${path}`;
  const res = await fetch(url, { ...options, headers });

  if (res.status === 204) {
    return null as T;
  }

  let body: any = null;
  const text = await res.text();
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    body = text;
  }

  if (!res.ok) {
    const errorMsg =
      (body && typeof body === "object" && (body.message || body.error || (body.data && JSON.stringify(body.data)))) ||
      `Request failed with status ${res.status}`;
    if (res.status === 401 && typeof window !== "undefined") {
      clearAuth();
    }
    throw new Error(errorMsg);
  }

  if (body && typeof body === "object" && "success" in body) {
    if (body.success === false) {
      throw new Error(body.message || "Operation failed");
    }
    return (body.data !== undefined ? body.data : body) as T;
  }

  return body as T;
}

export const api = {
  async register(req: { fullName: string; email: string; phone: string; password: string }) {
    await apiFetch("/api/auth/register", {
      method: "POST",
      body: JSON.stringify({
        fullName: req.fullName,
        email: req.email,
        phone: req.phone,
        password: req.password,
      }),
    });
    // Instant login after registration for seamless user experience
    return api.login({ email: req.email, password: req.password });
  },

  async login(req: { email: string; password: string }) {
    const data = await apiFetch<any>("/api/auth/login", {
      method: "POST",
      body: JSON.stringify(req),
    });
    setAuth(data.accessToken, data.refreshToken, data.user);
    return data;
  },

  async logout() {
    const refreshToken = getRefreshToken();
    try {
      if (refreshToken) {
        await apiFetch("/api/auth/logout", {
          method: "POST",
          body: JSON.stringify({ refreshToken }),
        });
      }
    } finally {
      clearAuth();
    }
  },

  async getCurrentUser(): Promise<Profile | null> {
    const user = getUser();
    if (!user) return null;
    try {
      const data = await apiFetch<any>("/api/auth/me");
      if (data) {
        const updatedUser: User = { ...user, ...data };
        if (typeof window !== "undefined") {
          localStorage.setItem(USER_KEY, JSON.stringify(updatedUser));
        }
        return {
          id: data.userId || String(data.id || user.userId),
          full_name: data.fullName || user.fullName,
          email: data.email || user.email,
          phone: data.phone || user.phone,
          upi_id: data.upiId || user.upiId,
          kyc_status: data.kycStatus || user.kycStatus,
          role: data.role || user.role,
          created_at: data.createdAt,
        };
      }
    } catch {
      // Fallback to locally stored user profile
    }
    return {
      id: user.userId,
      full_name: user.fullName,
      email: user.email,
      phone: user.phone,
      upi_id: user.upiId,
      kyc_status: user.kycStatus,
      role: user.role,
      created_at: null,
    };
  },

  async lookupUpi(upiId: string) {
    return apiFetch<any>(`/api/auth/users/lookup?upiId=${encodeURIComponent(upiId)}`);
  },

  async getWallet(): Promise<Wallet | null> {
    const data = await apiFetch<any>("/api/wallets/me");
    if (!data) return null;
    return {
      id: String(data.id),
      user_id: data.userId,
      balance: Number(data.balance ?? 0),
      currency: data.currency || "INR",
      status: "ACTIVE",
      created_at: data.createdAt,
      updated_at: data.updatedAt,
    };
  },

  async getStatement(): Promise<LedgerEntry[]> {
    const data = await apiFetch<any>("/api/wallets/me/statement");
    const list = Array.isArray(data) ? data : data?.content || [];
    return list.map((e: any) => ({
      id: String(e.id || e.reference_id || Math.random()),
      wallet_id: e.walletId || "",
      type: e.type || "TRANSFER",
      entry_type: e.type === "CREDIT" || e.entryType === "CREDIT" ? "CREDIT" : "DEBIT",
      amount: Number(e.amount),
      balance_after: Number(e.balanceAfter ?? e.balance_after ?? 0),
      description: e.description || e.txnRef || "Wallet entry",
      created_at: e.createdAt || e.created_at || new Date().toISOString(),
    }));
  },

  async topup(amount: number) {
    // Step 1: Create topup order
    const order = await apiFetch<any>("/api/wallets/topup/orders", {
      method: "POST",
      body: JSON.stringify({ amount }),
    });

    // Step 2: Process sandbox payment
    const payment = await apiFetch<any>("/api/gateway-sandbox/pay", {
      method: "POST",
      body: JSON.stringify({
        orderId: order.orderId,
        method: "UPI",
        instrument: "success@paytrack",
      }),
    });

    // Step 3: Verify topup and credit wallet
    return apiFetch<any>("/api/wallets/topup/verify", {
      method: "POST",
      body: JSON.stringify({
        orderId: order.orderId,
        paymentId: payment.paymentId,
        signature: payment.signature,
      }),
    });
  },

  async transfer(params: {
    receiverUpiId: string;
    amount: number;
    note?: string;
    category?: string;
    idempotencyKey?: string;
  }) {
    const key = params.idempotencyKey || (typeof crypto !== "undefined" ? crypto.randomUUID() : String(Date.now()));
    return apiFetch<any>("/api/transactions/transfer", {
      method: "POST",
      headers: {
        "Idempotency-Key": key,
      },
      body: JSON.stringify({
        receiverUpiId: params.receiverUpiId,
        amount: params.amount,
        note: params.note || "",
        category: params.category || "OTHER",
      }),
    });
  },

  async recharge(params: {
    mobileNumber: string;
    operator: string;
    amount: number;
    category?: string;
    idempotencyKey?: string;
  }) {
    const key = params.idempotencyKey || (typeof crypto !== "undefined" ? crypto.randomUUID() : String(Date.now()));
    return apiFetch<any>("/api/transactions/recharge", {
      method: "POST",
      headers: {
        "Idempotency-Key": key,
      },
      body: JSON.stringify({
        mobileNumber: params.mobileNumber,
        operator: params.operator,
        amount: params.amount,
        category: params.category || "UTILITIES",
      }),
    });
  },

  async getTransactions(limit = 20): Promise<{ rows: Transaction[]; total: number }> {
    const data = await apiFetch<any>(`/api/transactions?page=0&size=${limit}`);
    const items = Array.isArray(data) ? data : data?.content || [];
    const rows: Transaction[] = items.map((t: any) => ({
      id: String(t.id),
      txn_ref: t.txnRef || t.txn_ref,
      user_id: t.userId || t.user_id,
      receiver_user_id: t.receiverUserId || t.receiver_user_id,
      receiver_upi_id: t.receiverUpiId || t.receiver_upi_id,
      sender_upi_id: t.senderUpiId || t.sender_upi_id,
      type: t.type,
      status: t.status,
      amount: Number(t.amount),
      description: t.description,
      category: t.requestedCategory || t.category || "OTHER",
      created_at: t.timestamp || t.createdAt || t.created_at || new Date().toISOString(),
    }));
    return { rows, total: rows.length };
  },

  async getExpenses(): Promise<Expense[]> {
    const data = await apiFetch<any>("/api/expenses");
    const items = Array.isArray(data) ? data : data?.content || [];
    return items.map((e: any) => ({
      id: String(e.id),
      user_id: e.userId || e.user_id,
      amount: Number(e.amount),
      category: e.category,
      description: e.description,
      source: e.source || "PAYMENT",
      txn_ref: e.txnRef || e.txn_ref,
      created_at: e.timestamp || e.createdAt || e.created_at || new Date().toISOString(),
    }));
  },

  async createExpense(input: { category: string; amount: number; description: string }): Promise<Expense> {
    return apiFetch<any>("/api/expenses", {
      method: "POST",
      body: JSON.stringify(input),
    });
  },

  async deleteExpense(id: string | number) {
    return apiFetch<any>(`/api/expenses/${id}`, {
      method: "DELETE",
    });
  },

  async submitKyc(docType: string, docNumber: string) {
    return apiFetch<any>("/api/auth/kyc", {
      method: "POST",
      body: JSON.stringify({ docType, docNumber }),
    });
  },

  // Admin APIs
  async getAdminUsers(params?: { page?: number; size?: number; status?: string }): Promise<AdminUser[]> {
    const page = params?.page ?? 0;
    const size = params?.size ?? 50;
    let url = `/api/auth/admin/users?page=${page}&size=${size}`;
    if (params?.status) {
      url += `&status=${encodeURIComponent(params.status)}`;
    }
    const data = await apiFetch<any>(url);
    const list = Array.isArray(data) ? data : data?.content || [];
    return list.map((u: any) => ({
      id: u.id || u.userId,
      userId: u.userId,
      email: u.email,
      fullName: u.fullName || u.full_name || "Unknown",
      phone: u.phone,
      upiId: u.upiId || u.upi_id,
      role: u.role || "ROLE_USER",
      kycStatus: u.kycStatus || "PENDING",
      accountStatus: u.accountStatus || (u.active !== false ? "ACTIVE" : "SUSPENDED"),
      active: u.active ?? true,
      createdAt: u.createdAt || u.created_at,
    }));
  },

  async updateUserStatus(userId: string, status: "ACTIVE" | "SUSPENDED" | "LOCKED" | string) {
    return apiFetch<any>(`/api/auth/admin/users/${userId}/status?status=${encodeURIComponent(status)}`, {
      method: "PUT",
    });
  },

  async getAdminKyc(params?: { page?: number; size?: number; status?: string }): Promise<KycItem[]> {
    const page = params?.page ?? 0;
    const size = params?.size ?? 50;
    let url = `/api/auth/admin/kyc?page=${page}&size=${size}`;
    if (params?.status) {
      url += `&status=${encodeURIComponent(params.status)}`;
    }
    const data = await apiFetch<any>(url);
    const list = Array.isArray(data) ? data : data?.content || [];
    return list.map((k: any) => ({
      id: k.id,
      userId: k.userId,
      docType: k.docType,
      docLast4: k.docLast4,
      status: k.status,
      remarks: k.remarks,
      reviewedBy: k.reviewedBy,
      submittedAt: k.submittedAt || k.createdAt,
      reviewedAt: k.reviewedAt,
      createdAt: k.createdAt,
    }));
  },

  async reviewKyc(kycId: string, decision: "APPROVED" | "REJECTED", remarks?: string) {
    let url = `/api/auth/admin/kyc/${kycId}/review?decision=${encodeURIComponent(decision)}`;
    if (remarks) {
      url += `&remarks=${encodeURIComponent(remarks)}`;
    }
    return apiFetch<any>(url, {
      method: "PUT",
    });
  },

  async getAdminTransactionSummary(from?: string, to?: string): Promise<TransactionSummary> {
    let url = "/api/transactions/admin/summary";
    const queryParts: string[] = [];
    if (from) queryParts.push(`from=${encodeURIComponent(from)}`);
    if (to) queryParts.push(`to=${encodeURIComponent(to)}`);
    if (queryParts.length > 0) {
      url += `?${queryParts.join("&")}`;
    }
    const data = await apiFetch<any>(url);
    const recent = Array.isArray(data?.recentTransactions)
      ? data.recentTransactions.map((t: any) => ({
          id: String(t.id),
          txn_ref: t.txnRef || t.txn_ref,
          user_id: t.userId || t.user_id,
          receiver_user_id: t.receiverUserId || t.receiver_user_id,
          receiver_upi_id: t.receiverUpiId || t.receiver_upi_id,
          sender_upi_id: t.senderUpiId || t.sender_upi_id,
          type: t.type,
          status: t.status,
          amount: Number(t.amount),
          description: t.description,
          category: t.requestedCategory || t.category || "OTHER",
          created_at: t.timestamp || t.createdAt || t.created_at || new Date().toISOString(),
        }))
      : [];

    return {
      date: data?.date,
      totalTransactions: Number(data?.totalTransactions ?? 0),
      totalVolume: Number(data?.totalVolume ?? 0),
      countByType: data?.countByType || {},
      volumeByType: data?.volumeByType || {},
      recentTransactions: recent,
    };
  },

  async getAdminAllTransactions(params?: {
    page?: number;
    size?: number;
    status?: string;
    type?: string;
    from?: string;
    to?: string;
  }): Promise<{ rows: Transaction[]; total: number }> {
    const page = params?.page ?? 0;
    const size = params?.size ?? 50;
    const q: string[] = [`page=${page}`, `size=${size}`];
    if (params?.status) q.push(`status=${encodeURIComponent(params.status)}`);
    if (params?.type) q.push(`type=${encodeURIComponent(params.type)}`);
    if (params?.from) q.push(`from=${encodeURIComponent(params.from)}`);
    if (params?.to) q.push(`to=${encodeURIComponent(params.to)}`);

    const data = await apiFetch<any>(`/api/transactions/admin/all?${q.join("&")}`);
    const items = Array.isArray(data) ? data : data?.content || [];
    const rows: Transaction[] = items.map((t: any) => ({
      id: String(t.id),
      txn_ref: t.txnRef || t.txn_ref,
      user_id: t.userId || t.user_id,
      receiver_user_id: t.receiverUserId || t.receiver_user_id,
      receiver_upi_id: t.receiverUpiId || t.receiver_upi_id,
      sender_upi_id: t.senderUpiId || t.sender_upi_id,
      type: t.type,
      status: t.status,
      amount: Number(t.amount),
      description: t.description,
      category: t.requestedCategory || t.category || "OTHER",
      created_at: t.timestamp || t.createdAt || t.created_at || new Date().toISOString(),
    }));
    return { rows, total: rows.length };
  },

  async checkServicesHealth(): Promise<ServiceHealth[]> {
    const services = [
      { name: "Auth Service", key: "auth", path: "/api/auth/admin/users?page=0&size=1" },
      { name: "Transaction Service", key: "transaction", path: "/api/transactions/admin/summary" },
      { name: "Wallet Service", key: "wallet", path: "/api/wallets/me" },
      { name: "Expense Service", key: "expense", path: "/api/expenses" },
    ];

    const results = await Promise.all(
      services.map(async (svc) => {
        const start = performance.now();
        try {
          await apiFetch(svc.path);
          const latencyMs = Math.round(performance.now() - start);
          return {
            name: svc.name,
            key: svc.key,
            endpoint: svc.path,
            status: "UP" as const,
            latencyMs,
            lastChecked: new Date().toISOString(),
          };
        } catch (err: any) {
          const latencyMs = Math.round(performance.now() - start);
          // If wallet returns 404 WALLET_NOT_FOUND or valid API error, service is responsive and UP
          const isAlive =
            err?.message &&
            (err.message.includes("Wallet not found") ||
              err.message.includes("WALLET_NOT_FOUND") ||
              err.message.includes("404"));
          return {
            name: svc.name,
            key: svc.key,
            endpoint: svc.path,
            status: (isAlive ? "UP" : "DOWN") as const,
            latencyMs,
            lastChecked: new Date().toISOString(),
            error: isAlive ? undefined : err?.message || "Service unreachable",
          };
        }
      })
    );

    return results;
  },
};
