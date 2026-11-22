import { useState, useEffect } from 'react'
import { useAuth } from '../context/AuthContext'
import { walletApi } from '../services/api'
import Card from '../components/ui/Card'
import Button from '../components/ui/Button'
import Input from '../components/ui/Input'
import Badge from '../components/ui/Badge'

function formatCurrency(amount) {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 2,
  }).format(amount)
}

function Wallet() {
  const { user } = useAuth()
  const [wallet, setWallet] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [operation, setOperation] = useState(null) // 'credit' | 'debit'
  const [amount, setAmount] = useState('')
  const [operationLoading, setOperationLoading] = useState(false)
  const [operationError, setOperationError] = useState('')

  useEffect(() => {
    fetchWallet()
  }, [])

  const fetchWallet = async () => {
    try {
      setLoading(true)
      const response = await walletApi.getMyWallet()
      setWallet(response.data.data)
      setError(null)
    } catch (err) {
      if (err.response?.status === 404) {
        // Wallet doesn't exist, user needs to create one
        setWallet(null)
      } else {
        setError('Failed to load wallet')
      }
    } finally {
      setLoading(false)
    }
  }

  const handleOperation = async (e) => {
    e.preventDefault()
    if (!amount || parseFloat(amount) <= 0) {
      setOperationError('Please enter a valid amount')
      return
    }

    setOperationError('')
    setOperationLoading(true)

    try {
      if (operation === 'credit') {
        await walletApi.credit(parseFloat(amount))
      } else if (operation === 'debit') {
        await walletApi.debit(parseFloat(amount))
      }
      setAmount('')
      setOperation(null)
      await fetchWallet()
    } catch (err) {
      setOperationError(err.response?.data?.message || `Failed to ${operation} wallet`)
    } finally {
      setOperationLoading(false)
    }
  }

  const openOperation = (op) => {
    setOperation(op)
    setAmount('')
    setOperationError('')
  }

  if (loading) {
    return (
      <div className="space-y-6 animate-pulse">
        <Card className="h-32" />
        <Card className="h-64" />
      </div>
    )
  }

  return (
    <div className="space-y-6 animate-fade-in">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-[var(--color-text)]">Wallet</h1>
          <p className="text-[var(--color-text-muted)] mt-1">Manage your funds and balance</p>
        </div>
        {!wallet && (
          <Button variant="primary" onClick={() => openOperation('credit')}>
            Create Wallet
          </Button>
        )}
      </div>

      {/* Wallet Card */}
      <Card className="relative overflow-hidden">
        {/* Background accent */}
        <div className="absolute top-0 right-0 w-72 h-72 bg-[var(--color-accent-glow)] rounded-full blur-3xl opacity-50" aria-hidden="true" />

        <div className="relative">
          {wallet ? (
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              {/* Main Balance */}
              <div className="md:col-span-2">
                <p className="text-sm text-[var(--color-text-muted)] uppercase tracking-wide mb-1">Current Balance</p>
                <p className="text-4xl md:text-5xl font-bold text-[var(--color-text)] font-mono">
                  {formatCurrency(wallet.balance)}
                </p>
                <p className="text-xs text-[var(--color-text-subtle)] mt-2">
                  Wallet ID: <span className="font-mono text-[var(--color-text-muted)]">{(wallet.id || wallet.userId || '').toString().slice(0, 8)}...</span>
                </p>
                <div className="mt-4 flex flex-wrap gap-2">
                  <Badge variant="accent">{wallet.currency || 'USD'}</Badge>
                  <Badge variant={wallet.balance > 0 ? 'success' : 'default'}>
                    {wallet.balance > 0 ? 'Active' : 'Empty'}
                  </Badge>
                </div>
              </div>

              {/* Quick Actions */}
              <div className="space-y-3">
                <Button
                  variant="secondary"
                  fullWidth
                  onClick={() => openOperation('credit')}
                  disabled={operationLoading}
                >
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
                  </svg>
                  Add Funds
                </Button>
                <Button
                  variant="outline"
                  fullWidth
                  onClick={() => openOperation('debit')}
                  disabled={operationLoading || (wallet?.balance || 0) <= 0}
                >
                  <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12H9m12 0a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                  Withdraw
                </Button>
              </div>
            </div>
          ) : (
            <div className="text-center py-12">
              <div className="w-16 h-16 mx-auto mb-4 rounded-[var(--radius-xl)] bg-[var(--color-bg-elevated)] flex items-center justify-center">
                <svg className="w-8 h-8 text-[var(--color-text-muted)]" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
              </div>
              <h2 className="text-xl font-semibold text-[var(--color-text)] mb-2">No wallet yet</h2>
              <p className="text-[var(--color-text-muted)] mb-6 max-w-xs mx-auto">
                Create your first wallet to start managing your digital funds.
              </p>
              <Button variant="primary" onClick={() => openOperation('credit')} size="lg">
                Create Wallet
              </Button>
            </div>
          )}
        </div>
      </Card>

      {/* Credit/Debit Modal */}
      {operation && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 animate-fade-in" role="dialog" aria-modal="true" aria-labelledby="operation-title">
          <Card className="w-full max-w-md" padding="lg">
            <div className="flex items-center justify-between mb-6">
              <h2 id="operation-title" className="text-xl font-semibold text-[var(--color-text)]">
                {operation === 'credit' ? 'Add Funds' : 'Withdraw Funds'}
              </h2>
              <button
                onClick={() => setOperation(null)}
                className="p-1 rounded-[var(--radius-md)] hover:bg-[var(--color-bg-elevated)] text-[var(--color-text-muted)] transition-colors"
                aria-label="Close"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            <form onSubmit={handleOperation} className="space-y-4">
              <Input
                label="Amount (USD)"
                type="number"
                step="0.01"
                min="0.01"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                placeholder="0.00"
                required
                autoFocus
                error={operationError}
              />

              {wallet && operation === 'debit' && (
                <p className="text-xs text-[var(--color-text-muted)]">
                  Available balance: <span className="font-mono text-[var(--color-text)]">{formatCurrency(wallet.balance)}</span>
                </p>
              )}

              {operationError && (
                <div className="p-3 bg-[rgba(239,68,68,0.1)] border border-[rgba(239,68,68,0.3)] rounded-[var(--radius-md)] text-sm text-[var(--color-error)]" role="alert">
                  {operationError}
                </div>
              )}

              <div className="flex gap-3 pt-2">
                <Button
                  type="button"
                  variant="ghost"
                  fullWidth
                  onClick={() => setOperation(null)}
                  disabled={operationLoading}
                >
                  Cancel
                </Button>
                <Button
                  type="submit"
                  variant={operation === 'credit' ? 'primary' : 'outline'}
                  fullWidth
                  loading={operationLoading}
                >
                  {operation === 'credit' ? 'Add Funds' : 'Withdraw'}
                </Button>
              </div>
            </form>
          </Card>
        </div>
      )}

      {/* Wallet Info */}
      {wallet && (
        <Card>
          <h2 className="text-lg font-semibold text-[var(--color-text)] mb-4">Wallet Details</h2>
          <dl className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm">
            <div>
              <dt className="text-[var(--color-text-subtle)]">Wallet ID</dt>
              <dd className="font-mono text-[var(--color-text)] mt-1 break-all">{wallet.id || wallet.userId || 'N/A'}</dd>
            </div>
            <div>
              <dt className="text-[var(--color-text-subtle)]">User ID</dt>
              <dd className="font-mono text-[var(--color-text)] mt-1 break-all">{wallet.userId}</dd>
            </div>
            <div>
              <dt className="text-[var(--color-text-subtle)]">Currency</dt>
              <dd className="text-[var(--color-text)] mt-1">{wallet.currency || 'USD'}</dd>
            </div>
            <div>
              <dt className="text-[var(--color-text-subtle)]">Status</dt>
              <dd className="mt-1">
                <Badge variant={wallet.balance > 0 ? 'success' : 'default'}>
                  {wallet.balance > 0 ? 'Active' : 'Empty'}
                </Badge>
              </dd>
            </div>
          </dl>
        </Card>
      )}
    </div>
  )
}

export default Wallet