import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true,
})

// Request interceptor to add auth token
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('paytrack_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// Response interceptor for error handling
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('paytrack_token')
      localStorage.removeItem('paytrack_user')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

// Auth API
export const authApi = {
  register: (data) => api.post('/auth/register', data),
  login: (data) => api.post('/auth/login', data),
  getMe: () => api.get('/auth/me'),
}

// Wallet API
export const walletApi = {
  getMyWallet: () => api.get('/wallets/me'),
  createWallet: (data) => api.post('/wallets', data),
  credit: (amount) => api.post('/wallets/credit', null, { params: { amount } }),
  debit: (amount) => api.post('/wallets/debit', null, { params: { amount } }),
  updateBalance: (data) => api.put('/wallets/balance', data),
}

// Transaction API
export const transactionApi = {
  create: (data) => api.post('/transactions', data),
  getAll: () => api.get('/transactions'),
  getById: (id) => api.get(`/transactions/${id}`),
  updateStatus: (id, status) => api.put(`/transactions/${id}/status`, null, { params: { status } }),
  getByWallet: (walletId) => api.get(`/transactions/wallet/${walletId}`),
}

// Expense API
export const expenseApi = {
  create: (data) => api.post('/expenses', data),
  getAll: () => api.get('/expenses'),
  getById: (id) => api.get(`/expenses/${id}`),
  updateStatus: (id, status) => api.put(`/expenses/${id}/status`, null, { params: { status } }),
  getByWallet: (walletId) => api.get(`/expenses/wallet/${walletId}`),
}

export default api