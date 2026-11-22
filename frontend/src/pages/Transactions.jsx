import { useState, useEffect } from 'react'
import { useAuth } from '../context/AuthContext'
import { transactionApi, walletApi } from '../services/api'
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

function formatDate(dateString) {
  return new Date(dateString).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function getStatusVariant(status) {
  const s = status?.toLowerCase()
  if (s === 'completed' || s === 'settled') return 'success'
  if (s === 'pending') return 'warning'
  if (s === 'failed') return 'error'
  return 'default'
}

function getTransactionTypeIcon(type) {
  const t = type?.toLowerCase()
  if (t === 'credit' || t === 'deposit') return 'credit'
  if (t === 'debit' || t === 'withdrawal' || t === 'payment') return 'debit'
  return 'default'
}

function Transactions() {
  const { user } = useAuth()
  const [transactions, setTransactions] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [showCreate, setShowCreate] = useState(false)
  const [creating, setCreating] = useState(false)
  const [formData, setFormData] = useState({
    type: 'DEBIT',
    amount: '',
    description: '',
    walletId: '',
  })
  const [formError, setFormError] = useState('')

  useEffect(() => {
    fetchTransactions()
  }, [])

  const fetchTransactions = async () => {
    try {
      setLoading(true)
      const response = await transactionApi.getAll()
      setTransactions(response.data.data)
      setError(null)
    } catch (err) {
      setError('Failed to load transactions')
    } finally {
      setLoading(false)
    }
  }

  const handleCreate = async (e) => {
    e.preventDefault()
    setFormError('')

    if (!formData.amount || parseFloat(formData.amount) <= 0) {
      setFormError('Please enter a valid amount')
      return
    }

    if (!formData.description.trim()) {
      setFormError('Description is required')
      return
    }

    setCreating(true)
    try {
      await transactionApi.create({
        type: formData.type,
        amount: parseFloat(formData.amount),
        description: formData.description,
        walletId: formData.walletId || undefined,
      })
      setShowCreate(false)
      setFormData({ type: 'DEBIT', amount: '', description: '', walletId: '' })
      await fetchTransactions()
    } catch (err) {
      setFormError(err.response?.data?.message || 'Failed to create transaction')
    } finally {
      setCreating(false)
    }
  }

  if (loading) {
    return (
      <div className="space-y-6 animate-pulse">
        <Card className="h-24" />
        <Card className="h-96" />
      </div>
    )
  }

  return (
    <div className="space-y-6 animate-fade-in">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-[var(--color-text)]">Transactions</h1>
          <p className="text-[var(--color-text-muted)] mt-1">View and manage your transaction history</p>
        </div>
        <Button variant="primary" onClick={() => setShowCreate(true)}>
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
          </svg>
          New Transaction
        </Button>
      </div>

      {/* Transactions List */}
      <Card>
        {transactions.length === 0 ? (
          <div className="text-center py-12">
            <svg className="w-16 h-16 mx-auto mb-4 text-[var(--color-text-subtle)]" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4" />
            </svg>
            <h2 className="text-lg font-medium text-[var(--color-text)] mb-2">No transactions yet</h2>
            <p className="text-[var(--color-text-muted)] mb-6">Start by creating your first transaction</p>
            <Button variant="primary" onClick={() => setShowCreate(true)}>
              Create Transaction
            </Button>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full" role="table">
              <thead>
                <tr className="border-b border-[var(--color-border)]">
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--color-text-subtle)] uppercase tracking-wider">Date</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--color-text-subtle)] uppercase tracking-wider">Description</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--color-text-subtle)] uppercase tracking-wider">Type</th>
                  <th className="px-4 py-3 text-right text-xs font-semibold text-[var(--color-text-subtle)] uppercase tracking-wider">Amount</th>
                  <th className="px-4 py-3 text-center text-xs font-semibold text-[var(--color-text-subtle)] uppercase tracking-wider">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--color-border)]">
                {transactions.map((txn) => (
                  <tr key={txn.id} className="hover:bg-[var(--color-bg-elevated)] transition-colors">
                    <td className="px-4 py-4 text-sm text-[var(--color-text-muted)] font-mono">
                      {formatDate(txn.timestamp)}
                    </td>
                    <td className="px-4 py-4">
                      <p className="text-sm font-medium text-[var(--color-text)]">{txn.description}</p>
                      <p className="text-xs text-[var(--color-text-subtle)] font-mono">ID: {txn.transactionId?.slice(0, 8)}...</p>
                    </td>
                    <td className="px-4 py-4">
                      <Badge
                        variant={
                          txn.type?.toLowerCase() === 'credit' || txn.type?.toLowerCase() === 'deposit'
                            ? 'success'
                            : txn.type?.toLowerCase() === 'debit' || txn.type?.toLowerCase() === 'withdrawal' || txn.type?.toLowerCase() === 'payment'
                            ? 'error'
                            : 'info'
                        }
                        size="sm"
                      >
                        {txn.type}
                      </Badge>
                    </td>
                    <td className="px-4 py-4 text-right">
                      <span
                        className="font-mono font-medium"
                        style={{
                          color:
                            txn.type?.toLowerCase() === 'credit' || txn.type?.toLowerCase() === 'deposit'
                              ? 'var(--color-success)'
                              : 'var(--color-error)',
                        }}
                      >
                        {txn.type?.toLowerCase() === 'credit' || txn.type?.toLowerCase() === 'deposit' ? '+' : '-'}
                        {formatCurrency(txn.amount)}
                      </span>
                    </td>
                    <td className="px-4 py-4 text-center">
                      <Badge variant={getStatusVariant(txn.status)} size="sm">
                        {txn.status}
                      </Badge>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {/* Create Transaction Modal */}
      {showCreate && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 animate-fade-in" role="dialog" aria-modal="true" aria-labelledby="create-txn-title">
          <Card className="w-full max-w-md" padding="lg">
            <div className="flex items-center justify-between mb-6">
              <h2 id="create-txn-title" className="text-xl font-semibold text-[var(--color-text)]">
                New Transaction
              </h2>
              <button
                onClick={() => setShowCreate(false)}
                className="p-1 rounded-[var(--radius-md)] hover:bg-[var(--color-bg-elevated)] text-[var(--color-text-muted)] transition-colors"
                aria-label="Close"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            <form onSubmit={handleCreate} className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-[var(--color-text)] mb-2">Transaction Type</label>
                <div className="grid grid-cols-2 gap-3">
                  {['DEBIT', 'CREDIT', 'WITHDRAWAL', 'DEPOSIT', 'PAYMENT', 'TRANSFER'].map((type) => (
                    <button
                      key={type}
                      type="button"
                      onClick={() => setFormData({ ...formData, type })}
                      className={`
                        px-4 py-2.5 rounded-[var(--radius-md)]
                        text-sm font-medium
                        transition-all duration-200
                        border
                        ${formData.type === type
                          ? 'border-[var(--color-accent)] bg-[var(--color-accent-glow)] text-[var(--color-accent)]'
                          : 'border-[var(--color-border)] text-[var(--color-text-muted)] hover:border-[var(--color-border-hover)] hover:text-[var(--color-text)]'
                        }
                      `}
                    >
                      {type}
                    </button>
                  ))}
                </div>
              </div>

              <Input
                label="Amount (USD)"
                type="number"
                step="0.01"
                min="0.01"
                value={formData.amount}
                onChange={(e) => setFormData({ ...formData, amount: e.target.value })}
                placeholder="0.00"
                required
                error={formError}
              />

              <Input
                label="Description"
                type="text"
                value={formData.description}
                onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                placeholder="e.g., Coffee purchase, Salary deposit"
                required
                hint="What was this transaction for?"
              />

              <Input
                label="Wallet ID (optional)"
                type="text"
                value={formData.walletId}
                onChange={(e) => setFormData({ ...formData, walletId: e.target.value })}
                placeholder="Leave blank to use default wallet"
                hint="Defaults to your user ID if not specified"
              />

              {formError && (
                <div className="p-3 bg-[rgba(239,68,68,0.1)] border border-[rgba(239,68,68,0.3)] rounded-[var(--radius-md)] text-sm text-[var(--color-error)]" role="alert">
                  {formError}
                </div>
              )}

              <div className="flex gap-3 pt-2">
                <Button
                  type="button"
                  variant="ghost"
                  fullWidth
                  onClick={() => setShowCreate(false)}
                  disabled={creating}
                >
                  Cancel
                </Button>
                <Button type="submit" variant="primary" fullWidth loading={creating}>
                  Create Transaction
                </Button>
              </div>
            </form>
          </Card>
        </div>
      )}
    </div>
  )
}

export default Transactions