import { createContext, useContext, useState, useEffect, useCallback } from 'react'
import { authApi } from '../services/api'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [token, setToken] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  // Initialize from localStorage
  useEffect(() => {
    const savedToken = localStorage.getItem('paytrack_token')
    const savedUser = localStorage.getItem('paytrack_user')
    if (savedToken && savedUser) {
      setToken(savedToken)
      setUser(JSON.parse(savedUser))
    }
    setLoading(false)
  }, [])

  const login = useCallback(async (email, password) => {
    setError(null)
    try {
      const response = await authApi.login({ email, password })
      const { accessToken, user: userData } = response.data.data
      localStorage.setItem('paytrack_token', accessToken)
      localStorage.setItem('paytrack_user', JSON.stringify(userData))
      setToken(accessToken)
      setUser(userData)
      return { success: true }
    } catch (err) {
      const message = err.response?.data?.message || 'Login failed. Please check your credentials.'
      setError(message)
      return { success: false, error: message }
    }
  }, [])

  const register = useCallback(async (data) => {
    setError(null)
    try {
      const response = await authApi.register(data)
      const userData = response.data.data
      // Registration doesn't auto-login, user needs to login separately
      return { success: true, user: userData }
    } catch (err) {
      const message = err.response?.data?.message || 'Registration failed. Please try again.'
      setError(message)
      return { success: false, error: message }
    }
  }, [])

  const logout = useCallback(() => {
    localStorage.removeItem('paytrack_token')
    localStorage.removeItem('paytrack_user')
    setToken(null)
    setUser(null)
  }, [])

  const fetchUser = useCallback(async () => {
    if (!token) return
    try {
      const response = await authApi.getMe()
      const userData = response.data.data
      localStorage.setItem('paytrack_user', JSON.stringify(userData))
      setUser(userData)
    } catch (err) {
      // Token might be expired
      logout()
    }
  }, [token, logout])

  const value = {
    user,
    token,
    loading,
    error,
    login,
    register,
    logout,
    fetchUser,
    isAuthenticated: !!token && !!user,
  }

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}