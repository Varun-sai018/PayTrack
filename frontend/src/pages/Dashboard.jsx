import { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { walletApi, transactionApi, expenseApi, formatINR, categoryLabels } from '../services/api'
import Card from '../components/ui/Card'
import Button from '../components/ui/Button'
import Badge from '../components/ui/Badge'

function formatDate(dateString) {
  if (!dateString) return '-'
  return new Date(dateString).toLocaleDateString('en-IN', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  })
}

function getStatusVariant(status) {
  const s = status?.toLowerCase()
  if (s === 'completed' || s === 'success' || s === 'settled') return 'success'
  if (s === 'pending') return 'warning'
  if (s === 'failed') return 'error'
  return 'default'
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
          transactionApi.getAll(0, 5),
          expenseApi.getAll(),
        ])

        if (walletRes.status === 'fulfilled') {
          setWallet(walletRes.value.data.data)
        }
        if (txnRes.status === 'fulfilled') {
          const tData = txnRes.value.data.data
          const list = Array.isArray(tData) ? tData : tData?.content || []
          setRecentTransactions(list.slice(0, 5))
        }
        if (expRes.status === 'fulfilled') {
          const eData = expRes.value.data.data
          const list = Array.isArray(eData) ? eData : eData?.content || []
          setRecentExpenses(list.slice(0, 5))
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
      value: wallet ? formatINR(wallet.balance) : formatINR(0),
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
            UPI: <span className="font-mono text-[var(--color-accent)]">{user?.phone ? `${user.phone}@paytrack` : user?.email}</span>
          </p>
        </div>
        <div className="flex gap-3">
          <Link to="/wallet">
            <Button variant="secondary">Add Funds</Button>
          </Link>
          <Link to="/transactions">
            <Button variant="primary">Send Money</Button>
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
                <p className="text-2xl font-bold font-mono text-[var(--color-text)] mt-1">{stat.value}</p>
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
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z" />
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
              <Link to="/transactions">
                <Button variant="outline" size="sm">Make a transfer</Button>
              </Link>
            </div>
          ) : (
            <div className="space-y-3">
              {recentTransactions.map((txn) => {
                const isDebit = txn.userId === user?.id?.toString()
                return (
                  <div
                    key={txn.id || txn.txnRef}
                    className="flex items-center justify-between p-3 bg-[var(--color-bg-elevated)] rounded-[var(--radius-md)] border border-[var(--color-border)]"
                  >
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 rounded-[var(--radius-md)] flex items-center justify-center bg-[var(--color-accent-glow)]">
                        <svg className="w-5 h-5 text-[var(--color-accent)]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
                        </svg>
                      </div>
                      <div>
                        <p className="text-sm font-medium text-[var(--color-text)]">
                          {txn.receiverUpiId || txn.description || 'Transfer'}
                        </p>
                        <p className="text-xs text-[var(--color-text-muted)]">{formatDate(txn.timestamp)}</p>
                      </div>
                    </div>
                    <div className="text-right">
                      <p className={`text-sm font-mono font-semibold ${isDebit ? 'text-[var(--color-error)]' : 'text-[var(--color-success)]'}`}>
                        {isDebit ? '-' : '+'}{formatINR(txn.amount)}
                      </p>
                      <Badge variant={getStatusVariant(txn.status)} size="sm">
                        {txn.status}
                      </Badge>
                    </div>
                  </div>
                )
              })}
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
              <Link to="/expenses">
                <Button variant="outline" size="sm">Add an expense</Button>
              </Link>
            </div>
          ) : (
            <div className="space-y-3">
              {recentExpenses.map((exp) => (
                <div
                  key={exp.id || exp.txnRef}
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
                        {exp.description || categoryLabels[exp.category] || exp.category}
                      </p>
                      <p className="text-xs text-[var(--color-text-muted)]">{formatDate(exp.timestamp)}</p>
                    </div>
                  </div>
                  <div className="text-right">
                    <p className="text-sm font-mono font-semibold text-[var(--color-error)]">
                      -{formatINR(exp.amount)}
                    </p>
                    <Badge variant="default" size="sm">
                      {categoryLabels[exp.category] || exp.category}
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
          <Link to="/wallet" className="group">
            <Card className="h-full text-center py-6 hover:border-[var(--color-accent)] transition-colors">
              <div className="w-12 h-12 mx-auto mb-3 rounded-[var(--radius-lg)] bg-[var(--color-accent-glow)] flex items-center justify-center group-hover:scale-105 transition-transform">
                <svg className="w-6 h-6 text-[var(--color-accent)]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
                </svg>
              </div>
              <h3 className="font-medium text-[var(--color-text)]">Top-up Wallet</h3>
              <p className="text-xs text-[var(--color-text-muted)] mt-1">Add funds securely</p>
            </Card>
          </Link>
          <Link to="/transactions" className="group">
            <Card className="h-full text-center py-6 hover:border-[var(--color-info)] transition-colors">
              <div className="w-12 h-12 mx-auto mb-3 rounded-[var(--radius-lg)] bg-[rgba(59,130,246,0.15)] flex items-center justify-center group-hover:scale-105 transition-transform">
                <svg className="w-6 h-6 text-[var(--color-info)]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
                </svg>
              </div>
              <h3 className="font-medium text-[var(--color-text)]">UPI Transfer</h3>
              <p className="text-xs text-[var(--color-text-muted)] mt-1">Send to @paytrack UPI</p>
            </Card>
          </Link>
          <Link to="/expenses" className="group">
            <Card className="h-full text-center py-6 hover:border-[var(--color-warning)] transition-colors">
              <div className="w-12 h-12 mx-auto mb-3 rounded-[var(--radius-lg)] bg-[rgba(245,158,11,0.15)] flex items-center justify-center group-hover:scale-105 transition-transform">
                <svg className="w-6 h-6 text-[var(--color-warning)]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z" />
                </svg>
              </div>
              <h3 className="font-medium text-[var(--color-text)]">Track Expense</h3>
              <p className="text-xs text-[var(--color-text-muted)] mt-1">Manual & auto tracking</p>
            </Card>
          </Link>
          <Link to="/settings" className="group">
            <Card className="h-full text-center py-6 hover:border-[var(--color-text-muted)] transition-colors">
              <div className="w-12 h-12 mx-auto mb-3 rounded-[var(--radius-lg)] bg-[var(--color-bg-elevated)] flex items-center justify-center group-hover:scale-105 transition-transform">
                <svg className="w-6 h-6 text-[var(--color-text-muted)]" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                </svg>
              </div>
              <h3 className="font-medium text-[var(--color-text)]">Settings & KYC</h3>
              <p className="text-xs text-[var(--color-text-muted)] mt-1">Profile and verification</p>
            </Card>
          </Link>
        </div>
      </Card>
    </div>
  )
}

export default Dashboard
