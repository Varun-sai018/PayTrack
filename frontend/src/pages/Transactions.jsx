import { useState, useEffect } from 'react'
import { useAuth } from '../context/AuthContext'
import { transactionApi, authApi, formatINR, categoryLabels } from '../services/api'
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
    hour: '2-digit',
    minute: '2-digit',
  })
}

function getStatusVariant(status) {
  const s = status?.toLowerCase()
  if (s === 'completed' || s === 'success' || s === 'settled') return 'success'
  if (s === 'pending') return 'warning'
  if (s === 'failed') return 'error'
  return 'default'
}

function Transactions() {
  const { user } = useAuth()
  const [transactions, setTransactions] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  // Modals state
  const [activeModal, setActiveModal] = useState(null) // 'transfer' | 'recharge'
  const [actionLoading, setActionLoading] = useState(false)
  const [actionError, setActionError] = useState('')
  const [actionSuccess, setActionSuccess] = useState('')

  // Transfer form
  const [transferData, setTransferData] = useState({
    receiverUpiId: '',
    amount: '',
    note: '',
    category: 'OTHER',
  })
  const [receiverPreview, setReceiverPreview] = useState(null)
  const [lookupLoading, setLookupLoading] = useState(false)

  // Recharge form
  const [rechargeData, setRechargeData] = useState({
    mobileNumber: '',
    operator: 'JIO',
    amount: '',
  })

  useEffect(() => {
    fetchTransactions()
  }, [])

  const fetchTransactions = async () => {
    try {
      setLoading(true)
      const response = await transactionApi.getAll(0, 20)
      const data = response.data.data
      setTransactions(Array.isArray(data) ? data : data?.content || [])
      setError(null)
    } catch (err) {
      setError('Failed to load transactions')
    } finally {
      setLoading(false)
    }
  }

  // Lookup receiver on UPI ID change / blur
  const handleUpiLookup = async () => {
    const upi = transferData.receiverUpiId.trim()
    if (!upi || !upi.includes('@paytrack')) {
      setReceiverPreview(null)
      return
    }
    setLookupLoading(true)
    try {
      const res = await authApi.lookupUser(upi)
      setReceiverPreview(res.data.data)
      setActionError('')
    } catch (err) {
      setReceiverPreview(null)
      setActionError('User not found with this UPI ID')
    } finally {
      setLookupLoading(false)
    }
  }

  const handleTransfer = async (e) => {
    e.preventDefault()
    setActionError('')
    setActionSuccess('')

    const numAmount = parseFloat(transferData.amount)
    if (!numAmount || numAmount < 1 || numAmount > 10000) {
      setActionError('Amount must be between ₹1.00 and ₹10,000.00')
      return
    }

    if (!transferData.receiverUpiId.endsWith('@paytrack')) {
      setActionError('UPI ID must end with @paytrack')
      return
    }

    setActionLoading(true)
    try {
      const idempotencyKey = crypto.randomUUID()
      const res = await transactionApi.transfer({
        receiverUpiId: transferData.receiverUpiId.trim(),
        amount: numAmount,
        note: transferData.note.trim() || undefined,
        category: transferData.category || undefined,
      }, idempotencyKey)

      setActionSuccess(`Payment of ${formatINR(numAmount)} sent successfully! (Ref: ${res.data.data.txnRef})`)
      setTransferData({ receiverUpiId: '', amount: '', note: '', category: 'OTHER' })
      setReceiverPreview(null)
      setTimeout(() => {
        setActiveModal(null)
        setActionSuccess('')
      }, 1800)
      await fetchTransactions()
    } catch (err) {
      setActionError(err.response?.data?.message || 'Transfer failed. Check your wallet balance and KYC.')
    } finally {
      setActionLoading(false)
    }
  }

  const handleRecharge = async (e) => {
    e.preventDefault()
    setActionError('')
    setActionSuccess('')

    const numAmount = parseFloat(rechargeData.amount)
    if (!numAmount || numAmount < 10 || numAmount > 2000) {
      setActionError('Recharge amount must be between ₹10.00 and ₹2,000.00')
      return
    }

    if (!/^\d{10}$/.test(rechargeData.mobileNumber)) {
      setActionError('Mobile number must be exactly 10 digits')
      return
    }

    setActionLoading(true)
    try {
      const idempotencyKey = crypto.randomUUID()
      const res = await transactionApi.recharge({
        mobileNumber: rechargeData.mobileNumber,
        operator: rechargeData.operator,
        amount: numAmount,
        category: 'UTILITIES',
      }, idempotencyKey)

      setActionSuccess(`Recharge of ${formatINR(numAmount)} for ${rechargeData.mobileNumber} was successful!`)
      setRechargeData({ mobileNumber: '', operator: 'JIO', amount: '' })
      setTimeout(() => {
        setActiveModal(null)
        setActionSuccess('')
      }, 1800)
      await fetchTransactions()
    } catch (err) {
      setActionError(err.response?.data?.message || 'Recharge failed. Please check your balance.')
    } finally {
      setActionLoading(false)
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
          <p className="text-[var(--color-text-muted)] mt-1">Send money via UPI, recharge mobile, and review past transfers</p>
        </div>
        <div className="flex gap-2">
          <Button variant="secondary" onClick={() => { setActiveModal('recharge'); setActionError(''); setActionSuccess(''); }}>
            <svg className="w-4 h-4 mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 18h.01M8 21h8a2 2 0 002-2V5a2 2 0 00-2-2H8a2 2 0 00-2 2v14a2 2 0 002 2z" />
            </svg>
            Recharge Mobile
          </Button>
          <Button variant="primary" onClick={() => { setActiveModal('transfer'); setActionError(''); setActionSuccess(''); }}>
            <svg className="w-4 h-4 mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
            </svg>
            Send Money (UPI)
          </Button>
        </div>
      </div>

      {/* Transfer Modal */}
      {activeModal === 'transfer' && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 animate-fade-in" role="dialog" aria-modal="true">
          <Card className="w-full max-w-md" padding="lg">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-xl font-semibold text-[var(--color-text)]">
                Send Money via UPI
              </h2>
              <button
                onClick={() => setActiveModal(null)}
                className="p-1 rounded-[var(--radius-md)] hover:bg-[var(--color-bg-elevated)] text-[var(--color-text-muted)] transition-colors"
                aria-label="Close"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            <form onSubmit={handleTransfer} className="space-y-4">
              <div>
                <Input
                  label="Receiver UPI ID"
                  type="text"
                  value={transferData.receiverUpiId}
                  onChange={(e) => setTransferData({ ...transferData, receiverUpiId: e.target.value.toLowerCase() })}
                  onBlur={handleUpiLookup}
                  placeholder="name.surname@paytrack"
                  required
                  autoFocus
                />
                {lookupLoading && <p className="text-xs text-[var(--color-text-muted)] mt-1">Verifying recipient...</p>}
                {receiverPreview && (
                  <div className="mt-1.5 px-3 py-1.5 bg-[var(--color-bg-elevated)] rounded-[var(--radius-md)] border border-[var(--color-border)] text-xs flex items-center justify-between">
                    <span className="text-[var(--color-text-muted)]">Verified Recipient:</span>
                    <span className="font-semibold text-[var(--color-success)]">{receiverPreview.maskedName || receiverPreview.fullName}</span>
                  </div>
                )}
              </div>

              <Input
                label="Amount (INR)"
                type="number"
                step="0.01"
                min="1.00"
                max="10000.00"
                value={transferData.amount}
                onChange={(e) => setTransferData({ ...transferData, amount: e.target.value })}
                placeholder="0.00"
                required
              />

              <Input
                label="Note / Purpose"
                type="text"
                value={transferData.note}
                onChange={(e) => setTransferData({ ...transferData, note: e.target.value })}
                placeholder="Dinner, uber ride, rent..."
              />

              <div>
                <label className="block text-sm font-medium text-[var(--color-text)] mb-1">
                  Expense Category
                </label>
                <select
                  value={transferData.category}
                  onChange={(e) => setTransferData({ ...transferData, category: e.target.value })}
                  className="w-full px-3 py-2 bg-[var(--color-bg-elevated)] border border-[var(--color-border)] rounded-[var(--radius-md)] text-sm text-[var(--color-text)] focus:outline-none focus:border-[var(--color-accent)]"
                >
                  {Object.entries(categoryLabels).map(([key, label]) => (
                    <option key={key} value={key}>{label}</option>
                  ))}
                </select>
              </div>

              {actionSuccess && (
                <div className="p-3 bg-[rgba(34,197,94,0.1)] border border-[rgba(34,197,94,0.3)] rounded-[var(--radius-md)] text-sm text-[var(--color-success)]">
                  {actionSuccess}
                </div>
              )}

              {actionError && (
                <div className="p-3 bg-[rgba(239,68,68,0.1)] border border-[rgba(239,68,68,0.3)] rounded-[var(--radius-md)] text-sm text-[var(--color-error)]">
                  {actionError}
                </div>
              )}

              <div className="flex gap-3 pt-2">
                <Button type="button" variant="ghost" fullWidth onClick={() => setActiveModal(null)} disabled={actionLoading}>
                  Cancel
                </Button>
                <Button type="submit" variant="primary" fullWidth loading={actionLoading}>
                  Pay Now
                </Button>
              </div>
            </form>
          </Card>
        </div>
      )}

      {/* Recharge Modal */}
      {activeModal === 'recharge' && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 animate-fade-in" role="dialog" aria-modal="true">
          <Card className="w-full max-w-md" padding="lg">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-xl font-semibold text-[var(--color-text)]">
                Mobile Recharge
              </h2>
              <button
                onClick={() => setActiveModal(null)}
                className="p-1 rounded-[var(--radius-md)] hover:bg-[var(--color-bg-elevated)] text-[var(--color-text-muted)] transition-colors"
                aria-label="Close"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            <form onSubmit={handleRecharge} className="space-y-4">
              <Input
                label="Mobile Number (10 digits)"
                type="tel"
                value={rechargeData.mobileNumber}
                onChange={(e) => setRechargeData({ ...rechargeData, mobileNumber: e.target.value })}
                placeholder="9876543210"
                required
                autoFocus
              />

              <div>
                <label className="block text-sm font-medium text-[var(--color-text)] mb-1">
                  Operator
                </label>
                <div className="grid grid-cols-4 gap-2">
                  {['JIO', 'AIRTEL', 'VI', 'BSNL'].map((op) => (
                    <button
                      key={op}
                      type="button"
                      onClick={() => setRechargeData({ ...rechargeData, operator: op })}
                      className={`py-2 text-xs font-semibold rounded-[var(--radius-md)] border transition-all ${
                        rechargeData.operator === op
                          ? 'border-[var(--color-accent)] bg-[var(--color-accent-glow)] text-[var(--color-accent)]'
                          : 'border-[var(--color-border)] hover:bg-[var(--color-bg-elevated)] text-[var(--color-text)]'
                      }`}
                    >
                      {op}
                    </button>
                  ))}
                </div>
              </div>

              <Input
                label="Amount (INR)"
                type="number"
                step="1"
                min="10"
                max="2000"
                value={rechargeData.amount}
                onChange={(e) => setRechargeData({ ...rechargeData, amount: e.target.value })}
                placeholder="299"
                required
              />

              {/* Quick plans */}
              <div className="flex gap-2">
                {[199, 299, 499, 719].map((val) => (
                  <button
                    key={val}
                    type="button"
                    onClick={() => setRechargeData({ ...rechargeData, amount: val.toString() })}
                    className="flex-1 py-1 text-xs rounded border border-[var(--color-border)] text-[var(--color-text-muted)] hover:text-[var(--color-text)]"
                  >
                    ₹{val}
                  </button>
                ))}
              </div>

              {actionSuccess && (
                <div className="p-3 bg-[rgba(34,197,94,0.1)] border border-[rgba(34,197,94,0.3)] rounded-[var(--radius-md)] text-sm text-[var(--color-success)]">
                  {actionSuccess}
                </div>
              )}

              {actionError && (
                <div className="p-3 bg-[rgba(239,68,68,0.1)] border border-[rgba(239,68,68,0.3)] rounded-[var(--radius-md)] text-sm text-[var(--color-error)]">
                  {actionError}
                </div>
              )}

              <div className="flex gap-3 pt-2">
                <Button type="button" variant="ghost" fullWidth onClick={() => setActiveModal(null)} disabled={actionLoading}>
                  Cancel
                </Button>
                <Button type="submit" variant="primary" fullWidth loading={actionLoading}>
                  Recharge
                </Button>
              </div>
            </form>
          </Card>
        </div>
      )}

      {/* Transactions Table */}
      <Card>
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold text-[var(--color-text)]">Transaction History</h2>
          <Button variant="ghost" size="sm" onClick={fetchTransactions}>
            Refresh
          </Button>
        </div>

        {transactions.length === 0 ? (
          <div className="text-center py-12">
            <p className="text-[var(--color-text-muted)] mb-4">No transactions found.</p>
            <Button variant="primary" onClick={() => setActiveModal('transfer')}>
              Make First Transfer
            </Button>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-[var(--color-border)] text-[var(--color-text-subtle)] text-xs uppercase">
                  <th className="pb-3 font-semibold">Date</th>
                  <th className="pb-3 font-semibold">Txn Ref</th>
                  <th className="pb-3 font-semibold">Type</th>
                  <th className="pb-3 font-semibold">Counterparty / Note</th>
                  <th className="pb-3 font-semibold">Status</th>
                  <th className="pb-3 font-semibold text-right">Amount</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--color-border)]">
                {transactions.map((txn) => {
                  const isDebit = txn.userId === user?.id?.toString()
                  return (
                    <tr key={txn.id || txn.txnRef} className="hover:bg-[var(--color-bg-elevated)] transition-colors">
                      <td className="py-3 text-[var(--color-text-muted)] whitespace-nowrap">
                        {formatDate(txn.timestamp)}
                      </td>
                      <td className="py-3 font-mono text-xs text-[var(--color-accent)]">
                        {txn.txnRef}
                      </td>
                      <td className="py-3">
                        <span className="text-xs font-semibold px-2 py-0.5 rounded bg-[var(--color-bg-elevated)] border border-[var(--color-border)]">
                          {txn.type || 'TRANSFER'}
                        </span>
                      </td>
                      <td className="py-3 text-[var(--color-text)]">
                        <div className="font-medium text-xs font-mono">{txn.receiverUpiId || '-'}</div>
                        <div className="text-xs text-[var(--color-text-muted)] truncate max-w-xs">{txn.description || txn.requestedCategory || ''}</div>
                      </td>
                      <td className="py-3">
                        <Badge variant={getStatusVariant(txn.status)} size="sm">
                          {txn.status}
                        </Badge>
                      </td>
                      <td className={`py-3 text-right font-mono font-medium ${
                        txn.status === 'FAILED'
                          ? 'text-[var(--color-text-muted)] line-through'
                          : isDebit ? 'text-[var(--color-error)]' : 'text-[var(--color-success)]'
                      }`}>
                        {isDebit ? '-' : '+'}{formatINR(txn.amount)}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  )
}

export default Transactions
