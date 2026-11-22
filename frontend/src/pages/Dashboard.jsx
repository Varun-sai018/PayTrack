import { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { walletApi, transactionApi, expenseApi } from '../services/api'
import Card from '../components/ui/Card'
import Button from '../components/ui/Button'
import Badge from '../components/ui/Badge'

function formatCurrency(amount) {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 2,
  }).format(amount)
}

function formatDate(dateString) {
  return new Date(dateString).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  })
}

function getStatusVariant(status) {
  const s = status?.toLowerCase()
  if (s === 'completed' || s === 'approved' || s === 'settled') return 'success'
  if (s === 'pending') return 'warning'
  if (s === 'failed' || s === 'rejected') return 'error'
  return 'default'
}

function getTransactionTypeColor(type) {
  const t = type?.toLowerCase()
  if (t === 'credit' || t === 'deposit') return 'text-[var(--color-success)]'
  if (t === 'debit' || t === 'withdrawal' || t === 'payment') return 'text-[var(--color-error)]'
  return 'text-[var(--color-text)]'
}

function Dashboard() {
  const { user } = useAuth()
  const [wallet, setWallet] = useState(null)
  const [recentTransactions, setRecentTransactions] = useState([])
  const [recentExpenses, setRecentExpenses] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true)
        const [walletRes, txnRes, expRes] = await Promise.allSettled([
          walletApi.getMyWallet(),
          transactionApi.getAll(),
          expenseApi.getAll(),
        ])

        if (walletRes.status === 'fulfilled') {
          setWallet(walletRes.value.data.data)
        }
        if (txnRes.status === 'fulfilled') {
          setRecentTransactions(txnRes.value.data.data.slice(0, 5))
        }
        if (expRes.status === 'fulfilled') {
          setRecentExpenses(expRes.value.data.data.slice(0, 5))
        }
      } catch (err) {
        setError('Failed to load dashboard data')
      } finally {
        setLoading(false)
      }
    }

    fetchData()
  }, [])

  if (loading) {
    return (
      <div className="space-y-6 animate-pulse">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {[1, 2, 3].map((i) => (
            <Card key={i} className="h-24" />
          ))}
        </div>
        <Card className="h-64" />
        <Card className="h-64" />
      </div>
    )
  }

  const stats = [
    {
      label: 'Wallet Balance',
      value: wallet ? formatCurrency(wallet.balance) : '$0.00',
      icon: 'wallet',
      color: 'var(--color-accent)',
    },
    {
      label: 'Total Transactions',
      value: recentTransactions.length.toString(),
      icon: 'transactions',
      color: 'var(--color-info)',
    },
    {
      label: 'Total Expenses',
      value: recentExpenses.length.toString(),
      icon: 'expenses',
      color: 'var(--color-warning)',
    },
  ]

  return (
    <div className="space-y-6 animate-fade-in">
      {/* Welcome Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-[var(--color-text)]">
            Welcome back, {user?.fullName?.split(' ')[0] || 'User'}
          </h1>
          <p className="text-[var(--color-text-muted)] mt-1">
            Here's an overview of your financial activity
          </p>
        </div>
        <div className="flex gap-3">
          <Link to="/wallet/credit">
            <Button variant="secondary">Add Funds</Button>
          </Link>
          <Link to="/transactions/new">
            <Button variant="primary">New Transaction</Button>
          </Link>
        </div>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {stats.map((stat, index) => (
          <Card key={index} hover>
            <div className="flex items-start justify-between">
              <div>
                <p className="text-sm text-[var(--color-text-muted)]">{stat.label}</p>
                <p className="text-2xl font-bold text-[var(--color-text)] mt-1">{stat.value}</p>
              </div>
              <div
                className="w-12 h-12 rounded-[var(--radius-lg)] flex items-center justify-center"
                style={{ backgroundColor: `${stat.color}20` }}
              >
                <svg className="w-6 h-6" style={{ color: stat.color }} fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                  {stat.icon === 'wallet' && (
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                  )}
                  {stat.icon === 'transactions' && (
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4" />
                  )}
                  {stat.icon === 'expenses' && (
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                  )}
                </svg>
              </div>
            </div>
          </Card>
        ))}
      </div>

      {/* Recent Transactions & Expenses */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Recent Transactions */}
        <Card>
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold text-[var(--color-text)]">Recent Transactions</h2>
            <Link to="/transactions" className="text-sm text-[var(--color-accent)] hover:underline">
              View all
            </Link>
          </div>
          {recentTransactions.length === 0 ? (
            <div className="text-center py-8 text-[var(--color-text-muted)]">
              <p className="mb-2">No transactions yet</p>
              <Link to="/transactions/new">
                <Button variant="outline" size="sm">Create your first transaction</Button>
              </Link>
            </div>
          ) : (
            <div className="space-y-3">
              {recentTransactions.map((txn) => (
                <div
                  key={txn.id}
                  className="flex items-center justify-between p-3 bg-[var(--color-bg-elevated)] rounded-[var(--radius-md)] border border-[var(--color-border)]"
                >
                  <div className="flex items-center gap-3">
                    <div
                      className="w-10 h-10 rounded-[var(--radius-md)] flex items-center justify-center"
                      style={{
                        backgroundColor: getTransactionTypeColor(txn.type).includes('success')
                          ? 'rgba(16,185,129,0.15)'
                          : 'rgba(239,68,68,0.15)',
                      }}
                    >
                      <svg className="w-5 h-5" style={{ color: getTransactionTypeColor(txn.type) }} fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                        {txn.type?.toLowerCase() === 'credit' || txn.type?.toLowerCase() === 'deposit' ? (
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
                        ) : (
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12H9m12 0a9 9 0 11-18 0 9 9 0 0118 0z" />
                        )}
                      </svg>
                    </div>
                    <div>
                      <p className="text-sm font-medium text-[var(--color-text)]">
                        {txn.description || 'Transaction'}
                      </p>
                      <p className="text-xs text-[var(--color-text-muted)]">{formatDate(txn.timestamp)}</p>
                    </div>
                  </div>
                  <div className="text-right">
                    <p
                      className="text-sm font-semibold"
                      style={{ color: getTransactionTypeColor(txn.type) }}
                    >
                      {txn.type?.toLowerCase() === 'credit' || txn.type?.toLowerCase() === 'deposit' ? '+' : '-'}
                      {formatCurrency(txn.amount)}
                    </p>
                    <Badge variant={getStatusVariant(txn.status)} size="sm">
                      {txn.status}
                    </Badge>
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>

        {/* Recent Expenses */}
        <Card>
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold text-[var(--color-text)]">Recent Expenses</h2>
            <Link to="/expenses" className="text-sm text-[var(--color-accent)] hover:underline">
              View all
            </Link>
          </div>
          {recentExpenses.length === 0 ? (
            <div className="text-center py-8 text-[var(--color-text-muted)]">
              <p className="mb-2">No expenses recorded</p>
              <Link to="/expenses/new">
                <Button variant="outline" size="sm">Add your first expense</Button>
              </Link>
            </div>
          ) : (
            <div className="space-y-3">
              {recentExpenses.map((exp) => (
                <div
                  key={exp.id}
                  className="flex items-center justify-between p-3 bg-[var(--color-bg-elevated)] rounded-[var(--radius-md)] border border-[var(--color-border)]"
                >
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-[var(--radius-md)] bg-[rgba(245,158,11,0.15)] flex items-center justify-center">
                      <svg className="w-5 h-5 text-[var(--color-warning)]" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z" />
                      </svg>
                    </div>
                    <div>
                      <p className="text-sm font-medium text-[var(--color-text)]">
                        {exp.description || exp.category}
                      </p>
                      <p className="text-xs text-[var(--color-text-muted)]">{formatDate(exp.timestamp)}</p>
                    </div>
                  </div>
                  <div className="text-right">
                    <p className="text-sm font-semibold text-[var(--color-error)]">
                      -{formatCurrency(exp.amount)}
                    </p>
                    <Badge variant={getStatusVariant(exp.status)} size="sm">
                      {exp.status}
                    </Badge>
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>
      </div>

      {/* Quick Actions */}
      <Card>
        <h2 className="text-lg font-semibold text-[var(--color-text)] mb-4">Quick Actions</h2>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <Link to="/wallet/credit" className="group">
            <Card className="h-full text-center py-6 hover:border-[var(--color-accent)] transition-colors">
              <div className="w-12 h-12 mx-auto mb-3 rounded-[var(--radius-lg)] bg-[var(--color-accent-glow)] flex items-center justify-center group-hover:scale-105 transition-transform">
                <svg className="w-6 h-6 text-[var(--color-accent)]" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
                </svg>
              </div>
              <h3 className="font-medium text-[var(--color-text)]">Add Funds</h3>
              <p className="text-sm text-[var(--color-text-muted)] mt-1">Credit your wallet</p>
            </Card>
          </Link>
          <Link to="/transactions/new" className="group">
            <Card className="h-full text-center py-6 hover:border-[var(--color-info)] transition-colors">
              <div className="w-12 h-12 mx-auto mb-3 rounded-[var(--radius-lg)] bg-[rgba(59,130,246,0.15)] flex items-center justify-center group-hover:scale-105 transition-transform">
                <svg className="w-6 h-6 text-[var(--color-info)]" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 9V7a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2m2 4h10a2 2 0 002-2v-6a2 2 0 00-2-2H9a2 2 0 00-2 2v6a2 2 0 002 2zm7-5a2 2 0 11-4 0 2 2 0 014 0z" />
                </svg>
              </div>
              <h3 className="font-medium text-[var(--color-text)]">New Transaction</h3>
              <p className="text-sm text-[var(--color-text-muted)] mt-1">Send or receive money</p>
            </Card>
          </Link>
          <Link to="/expenses/new" className="group">
            <Card className="h-full text-center py-6 hover:border-[var(--color-warning)] transition-colors">
              <div className="w-12 h-12 mx-auto mb-3 rounded-[var(--radius-lg)] bg-[rgba(245,158,11,0.15)] flex items-center justify-center group-hover:scale-105 transition-transform">
                <svg className="w-6 h-6 text-[var(--color-warning)]" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z" />
                </svg>
              </div>
              <h3 className="font-medium text-[var(--color-text)]">Add Expense</h3>
              <p className="text-sm text-[var(--color-text-muted)] mt-1">Track your spending</p>
            </Card>
          </Link>
          <Link to="/settings" className="group">
            <Card className="h-full text-center py-6 hover:border-[var(--color-text-muted)] transition-colors">
              <div className="w-12 h-12 mx-auto mb-3 rounded-[var(--radius-lg)] bg-[var(--color-bg-elevated)] flex items-center justify-center group-hover:scale-105 transition-transform">
                <svg className="w-6 h-6 text-[var(--color-text-muted)]" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                </svg>
              </div>
              <h3 className="font-medium text-[var(--color-text)]">Settings</h3>
              <p className="text-sm text-[var(--color-text-muted)] mt-1">Manage your account</p>
            </Card>
          </Link>
        </div>
      </Card>
    </div>
  )
}

export default Dashboard