import { useState, useEffect } from 'react'
import { useAuth } from '../context/AuthContext'
import { expenseApi, formatINR, categoryLabels } from '../services/api'
import Card from '../components/ui/Card'
import Button from '../components/ui/Button'
import Input from '../components/ui/Input'
import Badge from '../components/ui/Badge'

function formatDate(dateString) {
  if (!dateString) return '-'
  return new Date(dateString).toLocaleDateString('en-IN', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  })
}

function Expenses() {
  const { user } = useAuth()
  const [expenses, setExpenses] = useState([])
  const [monthlySummary, setMonthlySummary] = useState(null)
  const [selectedMonth, setSelectedMonth] = useState('')
  const [selectedCategory, setSelectedCategory] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  // Create manual expense modal
  const [showCreate, setShowCreate] = useState(false)
  const [creating, setCreating] = useState(false)
  const [formData, setFormData] = useState({
    category: 'FOOD',
    amount: '',
    description: '',
  })
  const [formError, setFormError] = useState('')

  // Edit category modal
  const [editingExpense, setEditingExpense] = useState(null)
  const [newCategory, setNewCategory] = useState('OTHER')
  const [updatingCategory, setUpdatingCategory] = useState(false)

  useEffect(() => {
    fetchExpenses()
    fetchSummary()
  }, [selectedMonth, selectedCategory])

  const fetchExpenses = async () => {
    try {
      setLoading(true)
      const response = await expenseApi.getAll(selectedMonth || undefined, selectedCategory || undefined)
      const data = response.data.data
      setExpenses(Array.isArray(data) ? data : data?.content || [])
      setError(null)
    } catch (err) {
      setError('Failed to load expenses')
    } finally {
      setLoading(false)
    }
  }

  const fetchSummary = async () => {
    try {
      const response = await expenseApi.getMonthlySummary(selectedMonth || undefined)
      setMonthlySummary(response.data.data)
    } catch (err) {
      // Summary may be empty if no expenses
    }
  }

  const handleCreate = async (e) => {
    e.preventDefault()
    setFormError('')

    const numAmount = parseFloat(formData.amount)
    if (!numAmount || numAmount <= 0) {
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
        amount: numAmount,
        description: formData.description.trim(),
        currency: 'INR',
      })
      setShowCreate(false)
      setFormData({ category: 'FOOD', amount: '', description: '' })
      await fetchExpenses()
      await fetchSummary()
    } catch (err) {
      setFormError(err.response?.data?.message || 'Failed to create expense')
    } finally {
      setCreating(false)
    }
  }

  const handleDelete = async (id, source) => {
    if (source === 'PAYMENT') {
      alert('Payment expenses are automatically tracked and cannot be deleted.')
      return
    }
    if (!confirm('Are you sure you want to delete this manual expense?')) return

    try {
      await expenseApi.delete(id)
      await fetchExpenses()
      await fetchSummary()
    } catch (err) {
      alert(err.response?.data?.message || 'Failed to delete expense')
    }
  }

  const handleUpdateCategory = async (e) => {
    e.preventDefault()
    if (!editingExpense) return
    setUpdatingCategory(true)
    try {
      await expenseApi.updateCategory(editingExpense.txnRef, newCategory)
      setEditingExpense(null)
      await fetchExpenses()
      await fetchSummary()
    } catch (err) {
      alert(err.response?.data?.message || 'Failed to update category')
    } finally {
      setUpdatingCategory(false)
    }
  }

  // Calculate totals
  const totalAmount = monthlySummary?.totalAmount !== undefined 
    ? monthlySummary.totalAmount 
    : expenses.reduce((sum, exp) => sum + parseFloat(exp.amount || 0), 0)

  if (loading && expenses.length === 0) {
    return (
      <div className="space-y-6 animate-pulse">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {[1, 2, 3].map((i) => (
            <Card key={i} className="h-24" />
          ))}
        </div>
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
          <p className="text-[var(--color-text-muted)] mt-1">
            Automated spending categorization, category rules, and expense tracker
          </p>
        </div>
        <Button variant="primary" onClick={() => setShowCreate(true)}>
          <svg className="w-5 h-5 mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
          </svg>
          Add Manual Expense
        </Button>
      </div>

      {/* Summary Stats */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <Card>
          <p className="text-xs text-[var(--color-text-muted)] uppercase tracking-wider mb-1">Total Expenses</p>
          <p className="text-2xl font-bold font-mono text-[var(--color-text)]">{formatINR(totalAmount)}</p>
        </Card>
        <Card>
          <p className="text-xs text-[var(--color-text-muted)] uppercase tracking-wider mb-1">Expense Entries</p>
          <p className="text-2xl font-bold font-mono text-[var(--color-text)]">{expenses.length}</p>
        </Card>
        <Card>
          <p className="text-xs text-[var(--color-text-muted)] uppercase tracking-wider mb-1">Top Category</p>
          <p className="text-2xl font-bold text-[var(--color-accent)]">
            {monthlySummary?.categoryBreakdown && Object.keys(monthlySummary.categoryBreakdown).length > 0
              ? Object.entries(monthlySummary.categoryBreakdown).sort((a,b) => b[1] - a[1])[0][0]
              : 'N/A'}
          </p>
        </Card>
      </div>

      {/* Category Breakdown Chips */}
      {monthlySummary?.categoryBreakdown && Object.keys(monthlySummary.categoryBreakdown).length > 0 && (
        <Card padding="sm">
          <p className="text-xs font-semibold text-[var(--color-text-muted)] mb-2 px-1 uppercase tracking-wider">Category Breakdown</p>
          <div className="flex flex-wrap gap-2">
            {Object.entries(monthlySummary.categoryBreakdown).map(([cat, amt]) => (
              <div key={cat} className="px-3 py-1 bg-[var(--color-bg-elevated)] border border-[var(--color-border)] rounded-full text-xs flex items-center gap-2">
                <span className="font-medium text-[var(--color-text)]">{categoryLabels[cat] || cat}</span>
                <span className="font-mono text-[var(--color-accent)]">{formatINR(amt)}</span>
              </div>
            ))}
          </div>
        </Card>
      )}

      {/* Filters */}
      <div className="flex flex-wrap gap-3 items-center justify-between">
        <div className="flex gap-2 items-center">
          <select
            value={selectedCategory}
            onChange={(e) => setSelectedCategory(e.target.value)}
            className="px-3 py-1.5 text-xs bg-[var(--color-bg-card)] border border-[var(--color-border)] rounded-[var(--radius-md)] text-[var(--color-text)]"
          >
            <option value="">All Categories</option>
            {Object.entries(categoryLabels).map(([key, label]) => (
              <option key={key} value={key}>{label}</option>
            ))}
          </select>
        </div>
        <Button variant="ghost" size="sm" onClick={() => { fetchExpenses(); fetchSummary(); }}>
          Refresh
        </Button>
      </div>

      {/* Expenses Table */}
      <Card>
        {expenses.length === 0 ? (
          <div className="text-center py-12">
            <p className="text-[var(--color-text-muted)] mb-4">No expenses found for this period.</p>
            <Button variant="primary" onClick={() => setShowCreate(true)}>
              Add First Expense
            </Button>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-[var(--color-border)] text-[var(--color-text-subtle)] text-xs uppercase">
                  <th className="pb-3 font-semibold">Date</th>
                  <th className="pb-3 font-semibold">Category</th>
                  <th className="pb-3 font-semibold">Description</th>
                  <th className="pb-3 font-semibold">Source</th>
                  <th className="pb-3 font-semibold text-right">Amount</th>
                  <th className="pb-3 font-semibold text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--color-border)]">
                {expenses.map((exp) => (
                  <tr key={exp.id || exp.txnRef} className="hover:bg-[var(--color-bg-elevated)] transition-colors">
                    <td className="py-3 text-[var(--color-text-muted)] whitespace-nowrap">
                      {formatDate(exp.timestamp)}
                    </td>
                    <td className="py-3">
                      <div className="flex items-center gap-1.5">
                        <span className="font-medium text-[var(--color-text)]">
                          {categoryLabels[exp.category] || exp.category}
                        </span>
                        {exp.txnRef && (
                          <button
                            onClick={() => { setEditingExpense(exp); setNewCategory(exp.category || 'OTHER'); }}
                            title="Override category"
                            className="p-0.5 text-[var(--color-text-subtle)] hover:text-[var(--color-accent)]"
                          >
                            <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" />
                            </svg>
                          </button>
                        )}
                      </div>
                    </td>
                    <td className="py-3 text-[var(--color-text)]">
                      <div>{exp.description || 'Expense'}</div>
                      {exp.txnRef && (
                        <div className="text-xs font-mono text-[var(--color-text-subtle)]">{exp.txnRef}</div>
                      )}
                    </td>
                    <td className="py-3">
                      <Badge variant={exp.source === 'MANUAL' ? 'accent' : 'default'} size="sm">
                        {exp.source || 'PAYMENT'}
                      </Badge>
                    </td>
                    <td className="py-3 text-right font-mono font-medium text-[var(--color-text)]">
                      {formatINR(exp.amount)}
                    </td>
                    <td className="py-3 text-right">
                      {exp.source === 'MANUAL' ? (
                        <button
                          onClick={() => handleDelete(exp.id, exp.source)}
                          className="text-xs text-[var(--color-error)] hover:underline"
                        >
                          Delete
                        </button>
                      ) : (
                        <span className="text-xs text-[var(--color-text-subtle)]">Auto-sync</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {/* Create Manual Expense Modal */}
      {showCreate && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 animate-fade-in" role="dialog" aria-modal="true">
          <Card className="w-full max-w-md" padding="lg">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-xl font-semibold text-[var(--color-text)]">
                Add Manual Expense
              </h2>
              <button
                onClick={() => setShowCreate(false)}
                className="p-1 rounded-[var(--radius-md)] hover:bg-[var(--color-bg-elevated)] text-[var(--color-text-muted)] transition-colors"
                aria-label="Close"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            <form onSubmit={handleCreate} className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-[var(--color-text)] mb-1">
                  Category
                </label>
                <select
                  value={formData.category}
                  onChange={(e) => setFormData({ ...formData, category: e.target.value })}
                  className="w-full px-3 py-2 bg-[var(--color-bg-elevated)] border border-[var(--color-border)] rounded-[var(--radius-md)] text-sm text-[var(--color-text)] focus:outline-none focus:border-[var(--color-accent)]"
                >
                  {Object.entries(categoryLabels).map(([key, label]) => (
                    <option key={key} value={key}>{label}</option>
                  ))}
                </select>
              </div>

              <Input
                label="Amount (INR)"
                type="number"
                step="0.01"
                min="0.01"
                value={formData.amount}
                onChange={(e) => setFormData({ ...formData, amount: e.target.value })}
                placeholder="0.00"
                required
              />

              <Input
                label="Description"
                type="text"
                value={formData.description}
                onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                placeholder="e.g. Groceries at local market"
                required
              />

              {formError && (
                <div className="p-3 bg-[rgba(239,68,68,0.1)] border border-[rgba(239,68,68,0.3)] rounded-[var(--radius-md)] text-sm text-[var(--color-error)]">
                  {formError}
                </div>
              )}

              <div className="flex gap-3 pt-2">
                <Button type="button" variant="ghost" fullWidth onClick={() => setShowCreate(false)} disabled={creating}>
                  Cancel
                </Button>
                <Button type="submit" variant="primary" fullWidth loading={creating}>
                  Save Expense
                </Button>
              </div>
            </form>
          </Card>
        </div>
      )}

      {/* Edit Category Modal */}
      {editingExpense && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 animate-fade-in" role="dialog" aria-modal="true">
          <Card className="w-full max-w-sm" padding="lg">
            <h3 className="text-lg font-semibold text-[var(--color-text)] mb-3">
              Change Expense Category
            </h3>
            <p className="text-xs text-[var(--color-text-muted)] mb-4">
              Update category for transaction <span className="font-mono">{editingExpense.txnRef}</span>
            </p>
            <form onSubmit={handleUpdateCategory} className="space-y-4">
              <div>
                <select
                  value={newCategory}
                  onChange={(e) => setNewCategory(e.target.value)}
                  className="w-full px-3 py-2 bg-[var(--color-bg-elevated)] border border-[var(--color-border)] rounded-[var(--radius-md)] text-sm text-[var(--color-text)]"
                >
                  {Object.entries(categoryLabels).map(([key, label]) => (
                    <option key={key} value={key}>{label}</option>
                  ))}
                </select>
              </div>
              <div className="flex gap-2">
                <Button type="button" variant="ghost" fullWidth onClick={() => setEditingExpense(null)} disabled={updatingCategory}>
                  Cancel
                </Button>
                <Button type="submit" variant="primary" fullWidth loading={updatingCategory}>
                  Update
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
