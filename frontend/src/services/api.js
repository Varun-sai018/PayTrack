import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true,
})

// Add auth token to requests
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('paytrack_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    // Add Idempotency-Key for money actions
    if (config.method === 'post' && config.url?.includes('/transfer')) {
      if (!config.headers['Idempotency-Key']) {
        config.headers['Idempotency-Key'] = crypto.randomUUID()
      }
    }
    return config
  },
  (error) => Promise.reject(error)
)

// Response interceptor with refresh token flow
let isRefreshing = false;
let failedQueue = [];

const processQueue = (error, token = null) => {
  failedQueue.forEach(prom => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });
  failedQueue = [];
};

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    if (error.response?.status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        }).then(token => {
          originalRequest.headers['Authorization'] = 'Bearer ' + token;
          return api(originalRequest);
        }).catch(err => Promise.reject(err));
      }

      originalRequest._retry = true;
      isRefreshing = true;

      const refreshToken = localStorage.getItem('paytrack_refresh_token');
      if (!refreshToken) {
        localStorage.removeItem('paytrack_token');
        localStorage.removeItem('paytrack_user');
        window.location.href = '/login';
        return Promise.reject(error);
      }

      try {
        const response = await api.post('/auth/refresh', { refreshToken });
        const { accessToken } = response.data.data;
        localStorage.setItem('paytrack_token', accessToken);
        api.defaults.headers.common['Authorization'] = 'Bearer ' + accessToken;
        originalRequest.headers['Authorization'] = 'Bearer ' + accessToken;
        processQueue(null, accessToken);
        return api(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError, null);
        localStorage.removeItem('paytrack_token');
        localStorage.removeItem('paytrack_refresh_token');
        localStorage.removeItem('paytrack_user');
        window.location.href = '/login';
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }
    return Promise.reject(error);
  }
);

// INR Formatting Utility
export const formatINR = (amount) => {
  if (amount === null || amount === undefined) return '₹0.00';
  const num = typeof amount === 'string' ? parseFloat(amount) : amount;
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(num);
};

// Category labels
export const categoryLabels = {
  FOOD: 'Food',
  TRANSPORT: 'Travel',
  HOUSING: 'Housing',
  ENTERTAINMENT: 'Entertainment',
  UTILITIES: 'Bills & Recharges',
  HEALTHCARE: 'Health',
  SHOPPING: 'Shopping',
  EDUCATION: 'Education',
  OTHER: 'Others',
};

// Auth API
export const authApi = {
  register: (data) => api.post('/auth/register', data),
  login: (data) => api.post('/auth/login', data),
  refresh: (refreshToken) => api.post('/auth/refresh', { refreshToken }),
  logout: (refreshToken) => api.post('/auth/logout', { refreshToken }),
  getMe: () => api.get('/auth/me'),
  submitKyc: (data) => api.post('/auth/kyc', data),
  getKycStatus: () => api.get('/auth/kyc/status'),
  lookupUser: (upiId) => api.get('/auth/users/lookup', { params: { upiId } }),
  // Admin
  getAdminUsers: (page, size, status) => api.get('/auth/admin/users', { params: { page, size, status } }),
  updateUserStatus: (id, status) => api.put(`/auth/admin/users/${id}/status`, null, { params: { status } }),
  getAdminKyc: (page, size, status) => api.get('/auth/admin/kyc', { params: { page, size, status } }),
  reviewKyc: (id, decision, remarks) => api.put(`/auth/admin/kyc/${id}/review`, null, { params: { decision, remarks } }),
};

// Wallet API
export const walletApi = {
  getMyWallet: () => api.get('/wallets/me'),
  getStatement: (page, size, type, from, to) => api.get('/wallets/me/statement', { params: { page, size, type, from, to } }),
  topupOrder: (amount) => api.post('/wallets/topup/orders', { amount }),
  topupVerify: (data) => api.post('/wallets/topup/verify', data),
  sandboxPay: (data) => api.post('/gateway-sandbox/pay', data),
};

// Transaction API
export const transactionApi = {
  transfer: (data, idempotencyKey) => api.post('/transactions/transfer', data, { headers: { 'Idempotency-Key': idempotencyKey } }),
  qrGenerate: (data) => api.post('/transactions/qr/generate', data),
  qrPay: (data, idempotencyKey) => api.post('/transactions/qr/pay', data, { headers: { 'Idempotency-Key': idempotencyKey } }),
  recharge: (data, idempotencyKey) => api.post('/transactions/recharge', data, { headers: { 'Idempotency-Key': idempotencyKey } }),
  getAll: (page, size) => api.get('/transactions', { params: { page, size } }),
  getById: (id, txnRef) => api.get(`/transactions/${id}`, { params: { txnRef } }),
  adminAll: (page, size, status, type, from, to) => api.get('/transactions/admin/all', { params: { page, size, status, type, from, to } }),
  adminSummary: (from, to) => api.get('/transactions/admin/summary', { params: { from, to } }),
};

// Expense API
export const expenseApi = {
  create: (data) => api.post('/expenses', data),
  getAll: (month, category) => api.get('/expenses', { params: { month, category } }),
  updateCategory: (txnRef, category) => api.put(`/expenses/txn/${txnRef}/category`, { category }),
  getSummary: () => api.get('/expenses/summary'),
  getMonthlySummary: (month) => api.get('/expenses/summary/monthly', { params: { month } }),
  getTrend: (months) => api.get('/expenses/summary/trend', { params: { months } }),
  delete: (id) => api.delete(`/expenses/${id}`),
};

export default api