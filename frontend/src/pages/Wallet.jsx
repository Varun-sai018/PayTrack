import { useState, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { walletApi, formatINR } from '../services/api'
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

function Wallet() {
  const { user } = useAuth()
  const [wallet, setWallet] = useState(null)
  const [statement, setStatement] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [topupOpen, setTopupOpen] = useState(false)
  const [amount, setAmount] = useState('')
  const [topupLoading, setTopupLoading] = useState(false)
  const [topupError, setTopupError] = useState('')
  const [topupSuccess, setTopupSuccess] = useState('')

  useEffect(() => {
    fetchWalletData()
  }, [])

  const fetchWalletData = async () => {
    try {
      setLoading(true)
      const [walletRes, statementRes] = await Promise.allSettled([
        walletApi.getMyWallet(),
        walletApi.getStatement(0, 10),
      ])

      if (walletRes.status === 'fulfilled') {
        setWallet(walletRes.value.data.data)
        setError(null)
      } else if (walletRes.reason?.response?.status === 404) {
        setWallet(null)
      } else {
        setError('Failed to load wallet data')
      }

      if (statementRes.status === 'fulfilled') {
        const data = statementRes.value.data.data
        // Page object contains content array
        setStatement(data?.content || data || [])
      }
    } catch (err) {
      setError('Failed to load wallet')
    } finally {
      setLoading(false)
    }
  }

  const handleTopup = async (e) => {
    e.preventDefault()
    const numAmount = parseFloat(amount)
    if (!numAmount || numAmount < 1) {
      setTopupError('Minimum top-up amount is ₹1.00')
      return
    }

    setTopupError('')
    setTopupSuccess('')
    setTopupLoading(true)

    try {
      // 1. Create top-up order
      const orderRes = await walletApi.topupOrder(numAmount)
      const orderData = orderRes.data.data

      // 2. Simulate payment completion in sandbox environment
      const payRes = await walletApi.sandboxPay({
        orderId: orderData.orderId,
        method: 'UPI',
        instrument: 'success@paytrack',
      })
      const verifyPayload = {
        orderId: orderData.orderId,
        paymentId: payRes.data.data.paymentId,
        signature: payRes.data.data.signature,
      }

      // 3. Verify payment signature and credit ledger
      await walletApi.topupVerify(verifyPayload)

      setTopupSuccess(`Successfully added ${formatINR(numAmount)} to your wallet!`)
      setAmount('')
      setTimeout(() => {
        setTopupOpen(false)
        setTopupSuccess('')
      }, 1500)

      await fetchWalletData()
    } catch (err) {
      setTopupError(err.response?.data?.message || 'Failed to complete top-up')
    } finally {
      setTopupLoading(false)
    }
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
          <p className="text-[var(--color-text-muted)] mt-1">Manage your funds, add money, and view double-entry statements</p>
        </div>
        <div className="flex gap-2">
          <Link to="/transactions">
            <Button variant="outline">
              <svg className="w-4 h-4 mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
              </svg>
              Send Money
            </Button>
          </Link>
          <Button variant="primary" onClick={() => setTopupOpen(true)}>
            <svg className="w-4 h-4 mr-1.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
            </svg>
            Add Funds
          </Button>
        </div>
      </div>

      {/* Wallet Card */}
      <Card className="relative overflow-hidden">
        <div className="absolute top-0 right-0 w-72 h-72 bg-[var(--color-accent-glow)] rounded-full blur-3xl opacity-50" aria-hidden="true" />

        <div className="relative">
          {wallet ? (
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              {/* Main Balance */}
              <div className="md:col-span-2">
                <p className="text-sm text-[var(--color-text-muted)] uppercase tracking-wide mb-1">Available Balance</p>
                <p className="text-4xl md:text-5xl font-bold text-[var(--color-text)] font-mono">
                  {formatINR(wallet.balance)}
                </p>
                <p className="text-xs text-[var(--color-text-subtle)] mt-2">
                  Wallet ID: <span className="font-mono text-[var(--color-text-muted)]">{(wallet.id || '').toString().slice(0, 8)}...</span>
                </p>
                <div className="mt-4 flex flex-wrap gap-2">
                  <Badge variant="accent">{wallet.currency || 'INR'}</Badge>
                  <Badge variant={wallet.status === 'ACTIVE' ? 'success' : 'default'}>
                    {wallet.status || 'ACTIVE'}
                  </Badge>
                </div>
              </div>

              {/* Quick Actions */}
              <div className="space-y-3 flex flex-col justify-center">
                <Button
                  variant="primary"
                  fullWidth
                  onClick={() => setTopupOpen(true)}
                >
                  <svg className="w-5 h-5 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
                  </svg>
                  Top-up Wallet
                </Button>
                <Link to="/transactions" className="w-full">
                  <Button variant="secondary" fullWidth>
                    Transfer to UPI
                  </Button>
                </Link>
              </div>
            </div>
          ) : (
            <div className="text-center py-12">
              <h2 className="text-xl font-semibold text-[var(--color-text)] mb-2">No wallet detected</h2>
              <p className="text-[var(--color-text-muted)] mb-6 max-w-xs mx-auto">
                Your wallet is usually created automatically on registration.
              </p>
              <Button variant="primary" onClick={fetchWalletData}>
                Refresh Wallet
              </Button>
            </div>
          )}
        </div>
      </Card>

      {/* Top-up Modal */}
      {topupOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 animate-fade-in" role="dialog" aria-modal="true">
          <Card className="w-full max-w-md" padding="lg">
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-xl font-semibold text-[var(--color-text)]">
                Add Funds (Top-up)
              </h2>
              <button
                onClick={() => setTopupOpen(false)}
                className="p-1 rounded-[var(--radius-md)] hover:bg-[var(--color-bg-elevated)] text-[var(--color-text-muted)] transition-colors"
                aria-label="Close"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            <form onSubmit={handleTopup} className="space-y-4">
              <Input
                label="Amount (INR)"
                type="number"
                step="0.01"
                min="1.00"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                placeholder="500.00"
                required
                autoFocus
              />

              {/* Preset quick buttons */}
              <div className="flex gap-2">
                {[100, 500, 1000, 2000].map((val) => (
                  <button
                    key={val}
                    type="button"
                    onClick={() => setAmount(val.toString())}
                    className="flex-1 py-1.5 px-2 text-xs font-medium rounded-[var(--radius-md)] border border-[var(--color-border)] hover:bg-[var(--color-bg-elevated)] text-[var(--color-text)] transition-colors"
                  >
                    ₹{val}
                  </button>
                ))}
              </div>

              {topupSuccess && (
                <div className="p-3 bg-[rgba(34,197,94,0.1)] border border-[rgba(34,197,94,0.3)] rounded-[var(--radius-md)] text-sm text-[var(--color-success)]">
                  {topupSuccess}
                </div>
              )}

              {topupError && (
                <div className="p-3 bg-[rgba(239,68,68,0.1)] border border-[rgba(239,68,68,0.3)] rounded-[var(--radius-md)] text-sm text-[var(--color-error)]">
                  {topupError}
                </div>
              )}

              <div className="flex gap-3 pt-2">
                <Button
                  type="button"
                  variant="ghost"
                  fullWidth
                  onClick={() => setTopupOpen(false)}
                  disabled={topupLoading}
                >
                  Cancel
                </Button>
                <Button
                  type="submit"
                  variant="primary"
                  fullWidth
                  loading={topupLoading}
                >
                  Proceed to Pay
                </Button>
              </div>
            </form>
          </Card>
        </div>
      )}

      {/* Statement / Ledger Entries */}
      <Card>
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold text-[var(--color-text)]">Ledger Statement</h2>
          <Button variant="ghost" size="sm" onClick={fetchWalletData}>
            Refresh
          </Button>
        </div>

        {statement.length === 0 ? (
          <p className="text-center text-[var(--color-text-muted)] py-8 text-sm">
            No ledger transactions recorded yet.
          </p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-[var(--color-border)] text-[var(--color-text-subtle)] text-xs uppercase">
                  <th className="pb-3 font-semibold">Date</th>
                  <th className="pb-3 font-semibold">Type</th>
                  <th className="pb-3 font-semibold">Description</th>
                  <th className="pb-3 font-semibold text-right">Amount</th>
                  <th className="pb-3 font-semibold text-right">Balance After</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--color-border)]">
                {statement.map((entry, idx) => (
                  <tr key={entry.id || idx} className="hover:bg-[var(--color-bg-elevated)] transition-colors">
                    <td className="py-3 text-[var(--color-text-muted)] whitespace-nowrap">
                      {formatDate(entry.createdAt || entry.timestamp)}
                    </td>
                    <td className="py-3">
                      <Badge variant={entry.entryType === 'CREDIT' ? 'success' : 'default'} size="sm">
                        {entry.entryType}
                      </Badge>
                    </td>
                    <td className="py-3 text-[var(--color-text)]">
                      {entry.description || entry.referenceId || '-'}
                    </td>
                    <td className={`py-3 text-right font-mono font-medium ${
                      entry.entryType === 'CREDIT' ? 'text-[var(--color-success)]' : 'text-[var(--color-error)]'
                    }`}>
                      {entry.entryType === 'CREDIT' ? '+' : '-'}{formatINR(entry.amount)}
                    </td>
                    <td className="py-3 text-right font-mono text-[var(--color-text-muted)]">
                      {entry.balanceAfter !== undefined ? formatINR(entry.balanceAfter) : '-'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  )
}

export default Wallet
