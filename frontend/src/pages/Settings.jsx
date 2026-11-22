import { useState, useEffect } from 'react'
import { useAuth } from '../context/AuthContext'
import Card from '../components/ui/Card'
import Button from '../components/ui/Button'
import Input from '../components/ui/Input'
import Badge from '../components/ui/Badge'

function Settings() {
  const { user, fetchUser } = useAuth()
  const [activeTab, setActiveTab] = useState('profile')
  const [saving, setSaving] = useState(false)
  const [saveSuccess, setSaveSuccess] = useState(false)
  const [formData, setFormData] = useState({
    fullName: user?.fullName || '',
    email: user?.email || '',
  })
  const [passwordData, setPasswordData] = useState({
    currentPassword: '',
    newPassword: '',
    confirmPassword: '',
  })
  const [passwordError, setPasswordError] = useState('')

  useEffect(() => {
    if (user) {
      setFormData({
        fullName: user.fullName || '',
        email: user.email || '',
      })
    }
  }, [user])

  const handleProfileSave = async (e) => {
    e.preventDefault()
    setSaving(true)
    setSaveSuccess(false)

    // Simulate API call
    await new Promise((resolve) => setTimeout(resolve, 1000))

    setSaving(false)
    setSaveSuccess(true)
    setTimeout(() => setSaveSuccess(false), 3000)
  }

  const handlePasswordChange = async (e) => {
    e.preventDefault()
    setPasswordError('')

    if (passwordData.newPassword !== passwordData.confirmPassword) {
      setPasswordError('New passwords do not match')
      return
    }

    if (passwordData.newPassword.length < 8) {
      setPasswordError('New password must be at least 8 characters')
      return
    }

    // Simulate API call
    setSaving(true)
    await new Promise((resolve) => setTimeout(resolve, 1000))
    setSaving(false)

    setPasswordData({ currentPassword: '', newPassword: '', confirmPassword: '' })
    setSaveSuccess(true)
    setTimeout(() => setSaveSuccess(false), 3000)
  }

  const tabs = [
    { id: 'profile', label: 'Profile', icon: 'user' },
    { id: 'security', label: 'Security', icon: 'security' },
    { id: 'preferences', label: 'Preferences', icon: 'preferences' },
  ]

  return (
    <div className="space-y-6 animate-fade-in max-w-3xl">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-[var(--color-text)]">Settings</h1>
        <p className="text-[var(--color-text-muted)] mt-1">Manage your account settings and preferences</p>
      </div>

      {/* Tab Navigation */}
      <Card padding="none" className="overflow-hidden">
        <nav className="flex border-b border-[var(--color-border)]" aria-label="Settings tabs">
          {tabs.map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`
                flex-1 py-4 px-6 text-sm font-medium
                border-b-2 transition-all duration-200
                ${activeTab === tab.id
                  ? 'border-[var(--color-accent)] text-[var(--color-accent)]'
                  : 'border-transparent text-[var(--color-text-muted)] hover:text-[var(--color-text)] hover:border-[var(--color-border-hover)]'
                }
              `}
              role="tab"
              aria-selected={activeTab === tab.id}
              aria-controls={`${tab.id}-panel`}
            >
              {tab.label}
            </button>
          ))}
        </nav>

        {/* Tab Panels */}
        <div className="p-6" role="tabpanel">
          {activeTab === 'profile' && (
            <form onSubmit={handleProfileSave} className="space-y-6" id="profile-panel">
              <div className="flex items-center gap-6">
                <div className="w-20 h-20 rounded-full bg-[var(--color-accent-glow)] flex items-center justify-center flex-shrink-0">
                  <span className="text-2xl font-bold text-[var(--color-accent)]">
                    {user?.fullName?.charAt(0) || user?.email?.charAt(0)?.toUpperCase() || 'U'}
                  </span>
                </div>
                <div>
                  <h2 className="text-lg font-semibold text-[var(--color-text)]">{user?.fullName || 'User'}</h2>
                  <p className="text-sm text-[var(--color-text-muted)]">{user?.email}</p>
                  <p className="text-xs text-[var(--color-text-subtle)] mt-1">
                    Member since {new Date(user?.createdAt || Date.now()).toLocaleDateString('en-US', { month: 'long', year: 'numeric' })}
                  </p>
                </div>
              </div>

              <div className="border-t border-[var(--color-border)] pt-6 space-y-4">
                <h3 className="text-sm font-semibold text-[var(--color-text)]">Personal Information</h3>

                <Input
                  label="Full Name"
                  value={formData.fullName}
                  onChange={(e) => setFormData({ ...formData, fullName: e.target.value })}
                  placeholder="John Doe"
                  required
                />

                <Input
                  label="Email"
                  type="email"
                  value={formData.email}
                  onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                  placeholder="you@example.com"
                  required
                  disabled
                  hint="Email cannot be changed. Contact support if needed."
                />

                <div className="flex gap-3 pt-4">
                  <Button type="submit" variant="primary" loading={saving}>
                    Save Changes
                  </Button>
                  {saveSuccess && (
                    <span className="flex items-center text-sm text-[var(--color-success)]">
                      <svg className="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                      </svg>
                      Saved!
                    </span>
                  )}
                </div>
              </div>
            </form>
          )}

          {activeTab === 'security' && (
            <div className="space-y-8" id="security-panel">
              <div>
                <h3 className="text-sm font-semibold text-[var(--color-text)] mb-4">Change Password</h3>
                <form onSubmit={handlePasswordChange} className="space-y-4">
                  <Input
                    label="Current Password"
                    type="password"
                    name="currentPassword"
                    value={passwordData.currentPassword}
                    onChange={(e) => setPasswordData({ ...passwordData, currentPassword: e.target.value })}
                    placeholder="••••••••"
                    required
                  />

                  <Input
                    label="New Password"
                    type="password"
                    name="newPassword"
                    value={passwordData.newPassword}
                    onChange={(e) => setPasswordData({ ...passwordData, newPassword: e.target.value })}
                    placeholder="••••••••"
                    required
                    hint="At least 8 characters"
                  />

                  <Input
                    label="Confirm New Password"
                    type="password"
                    name="confirmPassword"
                    value={passwordData.confirmPassword}
                    onChange={(e) => setPasswordData({ ...passwordData, confirmPassword: e.target.value })}
                    placeholder="••••••••"
                    required
                  />

                  {passwordError && (
                    <div className="p-3 bg-[rgba(239,68,68,0.1)] border border-[rgba(239,68,68,0.3)] rounded-[var(--radius-md)] text-sm text-[var(--color-error)]" role="alert">
                      {passwordError}
                    </div>
                  )}

                  <Button type="submit" variant="primary" loading={saving}>
                    Update Password
                  </Button>
                </form>
              </div>

              <div className="border-t border-[var(--color-border)] pt-8">
                <h3 className="text-sm font-semibold text-[var(--color-text)] mb-4">Active Sessions</h3>
                <Card className="p-0">
                  <div className="p-4 border-b border-[var(--color-border)] flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 rounded-[var(--radius-md)] bg-[var(--color-accent-glow)] flex items-center justify-center">
                        <svg className="w-5 h-5 text-[var(--color-accent)]" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
                        </svg>
                      </div>
                      <div>
                        <p className="font-medium text-[var(--color-text)]">Current Session</p>
                        <p className="text-xs text-[var(--color-text-muted)]">This device • Active now</p>
                      </div>
                    </div>
                    <Badge variant="success" size="sm">Current</Badge>
                  </div>
                  <div className="p-4 text-center text-[var(--color-text-muted)] text-sm">
                    No other active sessions
                  </div>
                </Card>
              </div>

              <div className="border-t border-[var(--color-border)] pt-8">
                <h3 className="text-sm font-semibold text-[var(--color-text)] mb-4">Danger Zone</h3>
                <Card className="p-0 border-[rgba(239,68,68,0.3)]">
                  <div className="p-4">
                    <div className="flex items-center justify-between">
                      <div>
                        <p className="font-medium text-[var(--color-text)]">Delete Account</p>
                        <p className="text-sm text-[var(--color-text-muted)] mt-1">
                          Permanently delete your account and all associated data. This action cannot be undone.
                        </p>
                      </div>
                      <Button variant="danger" size="sm">Delete Account</Button>
                    </div>
                  </div>
                </Card>
              </div>
            </div>
          )}

          {activeTab === 'preferences' && (
            <div className="space-y-8" id="preferences-panel">
              <div>
                <h3 className="text-sm font-semibold text-[var(--color-text)] mb-4">Display</h3>
                <Card className="p-0">
                  <div className="p-4 border-b border-[var(--color-border)] flex items-center justify-between">
                    <div>
                      <p className="font-medium text-[var(--color-text)]">Dark Mode</p>
                      <p className="text-sm text-[var(--color-text-muted)]">Use dark theme (system default)</p>
                    </div>
                    <label className="relative inline-flex items-center cursor-pointer">
                      <input type="checkbox" className="sr-only peer" defaultChecked />
                      <div className="w-11 h-6 bg-[var(--color-border)] peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-[var(--color-accent)] rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-[var(--color-accent)]"></div>
                    </label>
                  </div>
                  <div className="p-4 flex items-center justify-between">
                    <div>
                      <p className="font-medium text-[var(--color-text)]">Compact Mode</p>
                      <p className="text-sm text-[var(--color-text-muted)]">Reduce spacing for more content</p>
                    </div>
                    <label className="relative inline-flex items-center cursor-pointer">
                      <input type="checkbox" className="sr-only peer" />
                      <div className="w-11 h-6 bg-[var(--color-border)] peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-[var(--color-accent)] rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-[var(--color-accent)]"></div>
                    </label>
                  </div>
                </Card>
              </div>

              <div className="border-t border-[var(--color-border)] pt-8">
                <h3 className="text-sm font-semibold text-[var(--color-text)] mb-4">Notifications</h3>
                <Card className="p-0">
                  {[
                    { title: 'Transaction Alerts', desc: 'Get notified for all transactions', enabled: true },
                    { title: 'Expense Reminders', desc: 'Weekly summary of your expenses', enabled: true },
                    { title: 'Security Alerts', desc: 'Login attempts and security changes', enabled: true },
                    { title: 'Marketing Emails', desc: 'Product updates and tips', enabled: false },
                  ].map((notif, i) => (
                    <div key={i} className={`p-4 flex items-center justify-between ${i > 0 ? 'border-t border-[var(--color-border)]' : ''}`}>
                      <div>
                        <p className="font-medium text-[var(--color-text)]">{notif.title}</p>
                        <p className="text-sm text-[var(--color-text-muted)]">{notif.desc}</p>
                      </div>
                      <label className="relative inline-flex items-center cursor-pointer">
                        <input
                          type="checkbox"
                          className="sr-only peer"
                          defaultChecked={notif.enabled}
                        />
                        <div className="w-11 h-6 bg-[var(--color-border)] peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-[var(--color-accent)] rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-[var(--color-accent)]"></div>
                      </label>
                    </div>
                  ))}
                </Card>
              </div>

              <div className="border-t border-[var(--color-border)] pt-8">
                <h3 className="text-sm font-semibold text-[var(--color-text)] mb-4">Currency</h3>
                <Card className="p-0">
                  <div className="p-4">
                    <label className="block text-sm font-medium text-[var(--color-text)] mb-3">Default Currency</label>
                    <div className="relative">
                      <select className={`
                        w-full
                        bg-[var(--color-bg-elevated)]
                        border
                        border-[var(--color-border)]
                        rounded-[var(--radius-md)]
                        px-4 py-2.5
                        text-sm
                        text-[var(--color-text)]
                        appearance-none
                        focus:border-[var(--color-accent)]
                        focus:ring-1
                        focus:ring-[var(--color-accent)]
                        outline-none
                        transition-all
                        duration-200
                        cursor-pointer
                        pr-10
                      `}>
                        <option value="USD">USD - US Dollar ($)</option>
                        <option value="EUR">EUR - Euro (€)</option>
                        <option value="GBP">GBP - British Pound (£)</option>
                        <option value="INR">INR - Indian Rupee (₹)</option>
                        <option value="JPY">JPY - Japanese Yen (¥)</option>
                      </select>
                      <div className="absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none">
                        <svg className="w-4 h-4 text-[var(--color-text-muted)]" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                        </svg>
                      </div>
                    </div>
                  </div>
                </Card>
              </div>
            </div>
          )}
        </div>
      </Card>
    </div>
  )
}

export default Settings