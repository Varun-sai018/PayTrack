import { useState, useEffect } from 'react'
import { useAuth } from '../context/AuthContext'
import { expenseApi } from '../services/api'
import Card from '../components/ui/Card'
import Button from '../components/ui/Button'
import Input from '../components/ui/Input'
import Badge from '../components/ui/Badge'

const EXPENSE_CATEGORIES = [
  'Food & Dining',
  'Transportation',
  'Shopping',
  'Entertainment',
  'Bills & Utilities',
  'Healthcare',
  'Education',
  'Travel',
  'Personal Care',
  'Gifts & Donations',
  'Investments',
  'Other',
]

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
  if (s === 'approved' || s === 'reimbursed') return 'success'
  if (s === 'pending') return 'warning'
  if (s === 'rejected') return 'error'
  return 'default'
}

function Expenses() {
  const { user } = useAuth()
  const [expenses, setExpenses] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [showCreate, setShowCreate] = useState(false)
  const [creating, setCreating] = useState(false)
  const [formData, setFormData] = useState({
    category: '',
    amount: '',
    description: '',
    walletId: '',
  })
  const [formError, setFormError] = useState('')

  useEffect(() => {
    fetchExpenses()
  }, [])

  const fetchExpenses = async () => {
    try {
      setLoading(true)
      const response = await expenseApi.getAll()
      setExpenses(response.data.data)
      setError(null)
    } catch (err) {
      setError('Failed to load expenses')
    } finally {
      setLoading(false)
    }
  }

  const handleCreate = async (e) => {
    e.preventDefault()
    setFormError('')

    if (!formData.category) {
      setFormError('Please select a category')
      return
    }

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
      await expenseApi.create({
        category: formData.category,
        amount: parseFloat(formData.amount),
        description: formData.description,
        walletId: formData.walletId || undefined,
      })
      setShowCreate(false)
      setFormData({ category: '', amount: '', description: '', walletId: '' })
      await fetchExpenses()
    } catch (err) {
      setFormError(err.response?.data?.message || 'Failed to create expense')
    } finally {
      setCreating(false)
    }
  }

  // Calculate totals
  const totalExpenses = expenses.reduce((sum, exp) => sum + parseFloat(exp.amount || 0), 0)
  const pendingExpenses = expenses
    .filter((exp) => exp.status?.toLowerCase() === 'pending')
    .reduce((sum, exp) => sum + parseFloat(exp.amount || 0), 0)
  const approvedExpenses = expenses
    .filter((exp) => ['approved', 'reimbursed'].includes(exp.status?.toLowerCase()))
    .reduce((sum, exp) => sum + parseFloat(exp.amount || 0), 0)

  if (loading) {
    return (
      <div className="space-y-6 animate-pulse">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {[1, 2, 3].map((i) => (
            <Card key={i} className="h-24" />
          ))}
        </div>
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
          <h1 className="text-2xl font-bold text-[var(--color-text)]">Expenses</h1>
          <p className="text-[var(--color-text-muted)] mt-1">Track and categorize your spending</p>
        </div>
        <Button variant="primary" onClick={() => setShowCreate(true)}>
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
          </svg>
          Add Expense
        </Button>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <Card>
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-[var(--color-text-muted)]">Total Expenses</p>
              <p className="text-2xl font-bold text-[var(--color-text)] font-mono mt-1">
                {formatCurrency(totalExpenses)}
              </p>
            </div>
            <div className="w-12 h-12 rounded-[var(--radius-lg)] bg-[rgba(239,68,68,0.15)] flex items-center justify-center">
              <svg className="w-6 h-6 text-[var(--color-error)]" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z" />
              </svg>
            </div>
          </div>
        </Card>
        <Card>
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-[var(--color-text-muted)]">Pending Approval</p>
              <p className="text-2xl font-bold text-[var(--color-text)] font-mono mt-1">
                {formatCurrency(pendingExpenses)}
              </p>
            </div>
            <div className="w-12 h-12 rounded-[var(--radius-lg)] bg-[rgba(245,158,11,0.15)] flex items-center justify-center">
              <svg className="w-6 h-6 text-[var(--color-warning)]" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </div>
          </div>
        </Card>
        <Card>
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-[var(--color-text-muted)]">Approved / Reimbursed</p>
              <p className="text-2xl font-bold text-[var(--color-text)] font-mono mt-1">
                {formatCurrency(approvedExpenses)}
              </p>
            </div>
            <div className="w-12 h-12 rounded-[var(--radius-lg)] bg-[rgba(16,185,129,0.15)] flex items-center justify-center">
              <svg className="w-6 h-6 text-[var(--color-success)]" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </div>
          </div>
        </Card>
      </div>

      {/* Expenses List */}
      <Card>
        {expenses.length === 0 ? (
          <div className="text-center py-12">
            <svg className="w-16 h-16 mx-auto mb-4 text-[var(--color-text-subtle)]" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z" />
            </svg>
            <h2 className="text-lg font-medium text-[var(--color-text)] mb-2">No expenses recorded</h2>
            <p className="text-[var(--color-text-muted)] mb-6">Start tracking your spending by adding your first expense</p>
            <Button variant="primary" onClick={() => setShowCreate(true)}>
              Add Expense
            </Button>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full" role="table">
              <thead>
                <tr className="border-b border-[var(--color-border)]">
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--color-text-subtle)] uppercase tracking-wider">Date</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--color-text-subtle)] uppercase tracking-wider">Category</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-[var(--color-text-subtle)] uppercase tracking-wider">Description</th>
                  <th className="px-4 py-3 text-right text-xs font-semibold text-[var(--color-text-subtle)] uppercase tracking-wider">Amount</th>
                  <th className="px-4 py-3 text-center text-xs font-semibold text-[var(--color-text-subtle)] uppercase tracking-wider">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--color-border)]">
                {expenses.map((exp) => (
                  <tr key={exp.id} className="hover:bg-[var(--color-bg-elevated)] transition-colors">
                    <td className="px-4 py-4 text-sm text-[var(--color-text-muted)] font-mono">
                      {formatDate(exp.timestamp)}
                    </td>
                    <td className="px-4 py-4">
                      <Badge variant="info" size="sm">{exp.category}</Badge>
                    </td>
                    <td className="px-4 py-4">
                      <p className="text-sm font-medium text-[var(--color-text)]">{exp.description}</p>
                      <p className="text-xs text-[var(--color-text-subtle)] font-mono">ID: {exp.expenseId?.slice(0, 8)}...</p>
                    </td>
                    <td className="px-4 py-4 text-right">
                      <span className="font-mono font-medium text-[var(--color-error)]">
                        -{formatCurrency(exp.amount)}
                      </span>
                    </td>
                    <td className="px-4 py-4 text-center">
                      <Badge variant={getStatusVariant(exp.status)} size="sm">
                        {exp.status}
                      </Badge>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {/* Create Expense Modal */}
      {showCreate && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 animate-fade-in" role="dialog" aria-modal="true" aria-labelledby="create-exp-title">
          <Card className="w-full max-w-md max-h-[90vh] overflow-y-auto" padding="lg">
            <div className="flex items-center justify-between mb-6">
              <h2 id="create-exp-title" className="text-xl font-semibold text-[var(--color-text)]">
                Add Expense
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
                <label className="block text-sm font-medium text-[var(--color-text)] mb-2">Category</label>
                <div className="relative">
                  <select
                    value={formData.category}
                    onChange={(e) => setFormData({ ...formData, category: e.target.value })}
                    className={`
                      w-full
                      bg-[var(--color-bg-elevated)]
                      border
                      border-[var(--color-border)]
                      rounded-[var(--radius-md)]
                      px-4 py-2.5
                      text-sm
                      text-[var(--color-text)]
                      placeholder-[var(--color-text-subtle)]
                      appearance-none
                      focus:border-[var(--color-accent)]
                      focus:ring-1
                      focus:ring-[var(--color-accent)]
                      outline-none
                      transition-all
                      duration-200
                      cursor-pointer
                    `}
                    required
                  >
                    <option value="">Select a category</option>
                    {EXPENSE_CATEGORIES.map((cat) => (
                      <option key={cat} value={cat}>
                        {cat}
                      </option>
                    ))}
                  </select>
                  <div className="absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none">
                    <svg className="w-4 h-4 text-[var(--color-text-muted)]" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                    </svg>
                  </div>
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
                placeholder="e.g., Lunch with team, Uber ride"
                required
                hint="What was this expense for?"
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
                  Add Expense
                </Button>
              </div>
            </form>
          </Card>
        </div>
      )}
    </div>
  )
}

export default Expenses