import { useState, useEffect, useCallback } from 'react';
import { transactionApi, budgetApi, insightApi, plaidApi } from '../services/api';
import { MonthlySummary, Transaction, TransactionType, CATEGORIES, ConnectedItem } from '../types';
import { useAuth } from '../hooks/useAuth';
import ConnectBank from '../components/ConnectBank';
import StatCard from '../components/StatCard';
import TransactionTable from '../components/TransactionTable';
import BudgetPanel from '../components/BudgetPanel';
import InsightsPanel from '../components/InsightsPanel';
import { Skeleton, EmptyState } from '../components/common';
import {
  PALETTE, CATEGORY_ICONS, MONTH_NAMES, SHORT_MONTHS,
  formatCurrency, formatDate, formatRelative, getTimeOfDay,
} from '../utils/dashboard';
import {
  PieChart, Pie, Cell, Tooltip, ResponsiveContainer,
  BarChart, Bar, XAxis, YAxis, CartesianGrid
} from 'recharts';
import {
  LayoutDashboard, ArrowLeftRight, Target, Sparkles,
  LogOut, Plus, TrendingUp, TrendingDown,
  Wallet, ChevronLeft, ChevronRight, X, DollarSign,
  AlertTriangle, Landmark, RefreshCw,
  CreditCard, Building2, Clock, Trash2
} from 'lucide-react';

function PieTooltip({ active, payload }: any) {
  if (!active || !payload?.length) return null;
  return (
    <div style={{ background: '#1a1a35', border: '1px solid rgba(255,255,255,0.12)', borderRadius: 10, padding: '10px 14px' }}>
      <p style={{ color: 'rgba(255,255,255,0.6)', fontSize: 12, marginBottom: 4 }}>{payload[0].name}</p>
      <p style={{ color: '#fff', fontWeight: 700, fontSize: 15 }}>{formatCurrency(payload[0].value)}</p>
    </div>
  );
}

function BarTooltip({ active, payload, label }: any) {
  if (!active || !payload?.length) return null;
  return (
    <div style={{ background: '#1a1a35', border: '1px solid rgba(255,255,255,0.12)', borderRadius: 10, padding: '10px 14px' }}>
      <p style={{ color: 'rgba(255,255,255,0.6)', fontSize: 12, marginBottom: 4 }}>{label}</p>
      <p style={{ color: '#fff', fontWeight: 700, fontSize: 15 }}>{payload[0].value.toFixed(1)}%</p>
    </div>
  );
}

interface TransactionFormState {
  description: string;
  amount: string;
  type: TransactionType;
  category: string;
  date: string;
  notes: string;
}

export default function Dashboard() {
  const { user, logout } = useAuth();
  const now = new Date();
  const [month, setMonth] = useState(now.getMonth() + 1);
  const [year, setYear] = useState(now.getFullYear());
  const [summary, setSummary] = useState<MonthlySummary | null>(null);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [insights, setInsights] = useState('');
  const [loadingInsights, setLoadingInsights] = useState(false);
  const [loadingData, setLoadingData] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [form, setForm] = useState<TransactionFormState>({
    description: '', amount: '', type: 'EXPENSE',
    category: 'Food', date: now.toISOString().split('T')[0], notes: ''
  });
  const [activeTab, setActiveTab] = useState<'overview' | 'transactions' | 'budgets' | 'accounts' | 'insights'>('overview');
  const [items, setItems] = useState<ConnectedItem[]>([]);
  const [plaidConfigured, setPlaidConfigured] = useState<boolean | null>(null);
  const [syncing, setSyncing] = useState(false);

  const fetchData = useCallback(async () => {
    setLoadingData(true);
    try {
      const [summaryRes, txRes] = await Promise.all([
        budgetApi.getSummary(year, month),
        transactionApi.getByMonth(year, month),
      ]);
      setSummary(summaryRes.data);
      setTransactions(txRes.data);
    } catch (e) { console.error(e); }
    finally { setLoadingData(false); }
  }, [month, year]);

  const fetchItems = useCallback(async () => {
    try {
      const res = await plaidApi.listItems();
      setItems(res.data);
    } catch (e) { /* user might not have any items yet */ }
  }, []);

  const handleSync = useCallback(async () => {
    if (syncing) return;
    setSyncing(true);
    try {
      await plaidApi.sync();
      await Promise.all([fetchData(), fetchItems()]);
    } catch (e) { console.error(e); }
    finally { setSyncing(false); }
  }, [syncing, fetchData, fetchItems]);

  const handleDisconnect = async (itemId: number) => {
    if (!confirm('Disconnect this bank? Existing transactions will remain.')) return;
    try { await plaidApi.disconnect(itemId); await fetchItems(); }
    catch (e) { console.error(e); }
  };

  useEffect(() => {
    plaidApi.status()
      .then(r => { if (typeof r.data?.configured === 'boolean') setPlaidConfigured(r.data.configured); })
      .catch(() => {});
  }, []);

  useEffect(() => { fetchData(); }, [fetchData]);
  useEffect(() => { fetchItems(); }, [fetchItems]);

  const handleAddTransaction = async (e: React.FormEvent) => {
    e.preventDefault();
    const amount = parseFloat(form.amount);
    if (isNaN(amount) || amount <= 0 || submitting) return;
    setSubmitting(true);
    try {
      await transactionApi.create({ ...form, amount });
      setShowForm(false);
      setForm({ description: '', amount: '', type: 'EXPENSE', category: 'Food', date: now.toISOString().split('T')[0], notes: '' });
      fetchData();
    } catch (e) { console.error(e); }
    finally { setSubmitting(false); }
  };

  const handleDelete = async (id: number) => {
    setDeletingId(id);
    try { await transactionApi.delete(id); fetchData(); }
    catch (e) { console.error(e); }
    finally { setDeletingId(null); }
  };

  const handleGetInsights = async () => {
    setLoadingInsights(true);
    setActiveTab('insights');
    try {
      const res = await insightApi.getInsights(year, month);
      setInsights(res.data.insights);
    } catch { setInsights('Failed to load insights. Please check your connection and try again.'); }
    finally { setLoadingInsights(false); }
  };

  const prevMonth = () => { if (month === 1) { setMonth(12); setYear(y => y - 1); } else setMonth(m => m - 1); };
  const nextMonth = () => { if (month === 12) { setMonth(1); setYear(y => y + 1); } else setMonth(m => m + 1); };

  const pieData = summary
    ? Object.entries(summary.expensesByCategory).map(([name, value]) => ({ name, value })).sort((a, b) => b.value - a.value)
    : [];
  const barData = summary?.budgets.map(b => ({ category: b.category, percentageUsed: b.percentageUsed })) ?? [];
  const netPositive = summary ? summary.netBalance >= 0 : true;

  const navItems = [
    { key: 'overview', label: 'Overview', icon: <LayoutDashboard size={17} /> },
    { key: 'transactions', label: 'Transactions', icon: <ArrowLeftRight size={17} /> },
    { key: 'accounts', label: 'Accounts', icon: <Landmark size={17} /> },
    { key: 'budgets', label: 'Budgets', icon: <Target size={17} /> },
    { key: 'insights', label: 'AI Insights', icon: <Sparkles size={17} /> },
  ] as const;

  const userInitial = user?.name?.charAt(0).toUpperCase() ?? '?';

  return (
    <div style={d.root}>
      {/* ── Sidebar ── */}
      <aside style={d.sidebar}>
        <div style={d.sidebarTop}>
          <div style={d.logo}>
            <div style={d.logoIcon}><TrendingUp size={18} color="#fff" /></div>
            <span style={d.logoText}>FinTrack</span>
          </div>
          <nav style={d.nav}>
            {navItems.map(({ key, label, icon }) => {
              const active = activeTab === key;
              return (
                <button key={key} style={{ ...d.navBtn, ...(active ? d.navBtnActive : {}) }}
                  onClick={() => setActiveTab(key)}>
                  <span style={{ color: active ? '#818cf8' : 'rgba(255,255,255,0.4)', display: 'flex' }}>{icon}</span>
                  <span>{label}</span>
                  {key === 'insights' && <span style={d.aiBadge}>AI</span>}
                </button>
              );
            })}
          </nav>
        </div>
        <div style={d.sidebarFooter}>
          <div style={d.userRow}>
            <div style={d.avatar}>{userInitial}</div>
            <div style={d.userInfo}>
              <p style={d.userName}>{user?.name}</p>
              <p style={d.userEmail}>{user?.email}</p>
            </div>
          </div>
          <button style={d.logoutBtn} onClick={logout} title="Sign out">
            <LogOut size={16} color="rgba(255,255,255,0.35)" />
          </button>
        </div>
      </aside>

      {/* ── Main ── */}
      <main style={d.main}>
        {/* Header */}
        <div style={d.header}>
          <div>
            <h1 style={d.greeting}>
              {activeTab === 'overview' ? `Good ${getTimeOfDay()}, ${user?.name?.split(' ')[0]}` :
               activeTab === 'transactions' ? 'Transactions' :
               activeTab === 'accounts' ? 'Connected Accounts' :
               activeTab === 'budgets' ? 'Budgets' : 'AI Insights'}
            </h1>
            <p style={d.subGreeting}>
              {activeTab === 'overview' ? "Here's your financial snapshot" :
               activeTab === 'transactions' ? `All transactions for ${MONTH_NAMES[month - 1]} ${year}` :
               activeTab === 'accounts' ? `${items.length} bank${items.length === 1 ? '' : 's'} connected` :
               activeTab === 'budgets' ? `Budget tracking for ${MONTH_NAMES[month - 1]} ${year}` :
               'Personalized spending analysis'}
            </p>
          </div>
          <div style={d.headerRight}>
            <div style={d.monthPicker}>
              <button style={d.arrowBtn} onClick={prevMonth}><ChevronLeft size={15} /></button>
              <span style={d.monthLabel}>{SHORT_MONTHS[month - 1]} {year}</span>
              <button style={d.arrowBtn} onClick={nextMonth}><ChevronRight size={15} /></button>
            </div>
            {items.length > 0 && (
              <button style={d.syncBtn} onClick={handleSync} disabled={syncing} title="Sync transactions from connected banks">
                <RefreshCw size={14} style={syncing ? { animation: 'spin 0.8s linear infinite' } : undefined} />
                <span>{syncing ? 'Syncing…' : 'Sync'}</span>
              </button>
            )}
            <button style={d.addBtn} onClick={() => setShowForm(true)}>
              <Plus size={16} />
              <span>Add Transaction</span>
            </button>
          </div>
        </div>

        {/* ── Overview Tab ── */}
        {activeTab === 'overview' && (
          <div className="fade-in">
            {plaidConfigured && items.length === 0 && (
              <div style={d.plaidBanner}>
                <div style={d.plaidBannerLeft}>
                  <div style={d.plaidBannerIcon}><Landmark size={22} color="#a5b4fc" /></div>
                  <div>
                    <h3 style={d.plaidBannerTitle}>Connect your bank in one tap</h3>
                    <p style={d.plaidBannerSub}>Auto-import transactions from 12,000+ banks. Bank-grade security via Plaid.</p>
                  </div>
                </div>
                <ConnectBank onConnected={() => { fetchItems(); fetchData(); }} />
              </div>
            )}
            {plaidConfigured === false && (
              <div style={d.warnBanner}>
                <AlertTriangle size={16} color="#f59e0b" />
                <span>Plaid is not configured. Set <code style={d.codeChip}>PLAID_CLIENT_ID</code> and <code style={d.codeChip}>PLAID_SECRET</code> on the backend to enable bank linking.</span>
              </div>
            )}

            <div style={d.statsGrid}>
              <StatCard label="Total Income" value={summary ? formatCurrency(summary.totalIncome) : '$0.00'}
                icon={<TrendingUp size={18} />} color="#10b981" loading={loadingData} />
              <StatCard label="Total Expenses" value={summary ? formatCurrency(summary.totalExpenses) : '$0.00'}
                icon={<TrendingDown size={18} />} color="#ef4444" loading={loadingData} />
              <StatCard label="Net Balance" value={summary ? formatCurrency(summary.netBalance) : '$0.00'}
                icon={<Wallet size={18} />} color={netPositive ? '#10b981' : '#ef4444'} loading={loadingData} />
              <StatCard label="Transactions" value={loadingData ? '' : String(transactions.length)}
                icon={<ArrowLeftRight size={18} />} color="#6366f1" loading={loadingData} />
            </div>

            <div style={d.chartsRow}>
              <div style={d.chartCard}>
                <h3 style={d.chartTitle}>Expenses by Category</h3>
                {loadingData ? (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginTop: 16 }}>
                    {[120, 90, 100, 80].map((w, i) => <Skeleton key={i} h={14} w={`${w}px`} />)}
                  </div>
                ) : pieData.length === 0 ? (
                  <EmptyState icon={<PieChart />} text="No expenses recorded this month" />
                ) : (
                  <>
                    <ResponsiveContainer width="100%" height={200}>
                      <PieChart>
                        <Pie data={pieData} dataKey="value" nameKey="name" cx="50%" cy="50%"
                          innerRadius={55} outerRadius={85} paddingAngle={2}>
                          {pieData.map((_, i) => <Cell key={i} fill={PALETTE[i % PALETTE.length]} strokeWidth={0} />)}
                        </Pie>
                        <Tooltip content={<PieTooltip />} />
                      </PieChart>
                    </ResponsiveContainer>
                    <div style={d.legend}>
                      {pieData.map((d2, i) => (
                        <div key={i} style={d.legendItem}>
                          <span style={{ ...d.legendDot, background: PALETTE[i % PALETTE.length] }} />
                          <span style={d.legendName}>{d2.name}</span>
                          <span style={d.legendVal}>{formatCurrency(d2.value)}</span>
                        </div>
                      ))}
                    </div>
                  </>
                )}
              </div>

              <div style={d.chartCard}>
                <h3 style={d.chartTitle}>Budget Usage</h3>
                {loadingData ? (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginTop: 16 }}>
                    {[140, 100, 120, 90].map((w, i) => <Skeleton key={i} h={14} w={`${w}px`} />)}
                  </div>
                ) : barData.length === 0 ? (
                  <EmptyState icon={<Target size={32} />} text="No budgets set for this month" />
                ) : (
                  <ResponsiveContainer width="100%" height={240}>
                    <BarChart data={barData} layout="vertical" margin={{ left: 0, right: 16 }}>
                      <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.04)" horizontal={false} />
                      <XAxis type="number" domain={[0, 100]} tick={{ fill: 'rgba(255,255,255,0.3)', fontSize: 11 }}
                        axisLine={false} tickLine={false} tickFormatter={v => `${v}%`} />
                      <YAxis type="category" dataKey="category" tick={{ fill: 'rgba(255,255,255,0.5)', fontSize: 12 }}
                        width={88} axisLine={false} tickLine={false} />
                      <Tooltip content={<BarTooltip />} cursor={{ fill: 'rgba(255,255,255,0.04)' }} />
                      <Bar dataKey="percentageUsed" radius={[0, 6, 6, 0]} maxBarSize={14}>
                        {barData.map((entry, i) => (
                          <Cell key={i} fill={entry.percentageUsed > 90 ? '#ef4444' : entry.percentageUsed > 70 ? '#f59e0b' : '#6366f1'} />
                        ))}
                      </Bar>
                    </BarChart>
                  </ResponsiveContainer>
                )}
              </div>
            </div>

            {!loadingData && transactions.length > 0 && (
              <div style={d.recentCard}>
                <div style={d.recentHeader}>
                  <h3 style={d.chartTitle}>Recent Transactions</h3>
                  <button style={d.viewAllBtn} onClick={() => setActiveTab('transactions')}>View all →</button>
                </div>
                <div style={d.recentList}>
                  {transactions.slice(0, 5).map(t => (
                    <div key={t.id} style={d.recentRow}>
                      <div style={{ ...d.txIcon, background: t.type === 'INCOME' ? 'rgba(16,185,129,0.12)' : 'rgba(239,68,68,0.1)' }}>
                        <span style={{ color: t.type === 'INCOME' ? '#10b981' : '#f87171' }}>
                          {CATEGORY_ICONS[t.category] ?? <DollarSign size={14} />}
                        </span>
                      </div>
                      <div style={d.txMeta}>
                        <span style={d.txDesc}>{t.merchantName ?? t.description}</span>
                        <span style={d.txCat}>{t.category} · {formatDate(t.date)}</span>
                      </div>
                      <span style={{ ...d.txAmt, color: t.type === 'INCOME' ? '#10b981' : '#f87171' }}>
                        {t.type === 'INCOME' ? '+' : '−'}{formatCurrency(t.amount)}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            <button style={d.insightBtn} onClick={handleGetInsights} disabled={loadingInsights}>
              <Sparkles size={16} />
              {loadingInsights ? 'Analyzing your finances…' : 'Get AI Spending Insights'}
            </button>
          </div>
        )}

        {/* ── Transactions Tab ── */}
        {activeTab === 'transactions' && (
          <div className="fade-in">
            <TransactionTable
              transactions={transactions}
              loading={loadingData}
              deletingId={deletingId}
              onDelete={handleDelete}
            />
          </div>
        )}

        {/* ── Accounts Tab ── */}
        {activeTab === 'accounts' && (
          <div className="fade-in">
            {plaidConfigured === false ? (
              <div style={d.warnBanner}>
                <AlertTriangle size={16} color="#f59e0b" />
                <span>Plaid is not configured on the server. Set <code style={d.codeChip}>PLAID_CLIENT_ID</code> and <code style={d.codeChip}>PLAID_SECRET</code> env vars.</span>
              </div>
            ) : items.length === 0 ? (
              <div style={d.connectEmpty}>
                <div style={d.connectEmptyIcon}><Landmark size={32} color="#818cf8" /></div>
                <h3 style={d.connectEmptyTitle}>No banks connected yet</h3>
                <p style={d.connectEmptySub}>
                  Connect your bank to automatically import transactions and balances —
                  no manual entry, no spreadsheets, just clarity.
                </p>
                <div style={{ marginTop: 24 }}>
                  <ConnectBank onConnected={() => { fetchItems(); fetchData(); }} />
                </div>
                <div style={d.trustRow}>
                  <span style={d.trustItem}>🔒 256-bit encryption</span>
                  <span style={d.trustItem}>🏦 Powered by Plaid</span>
                  <span style={d.trustItem}>✓ Read-only access</span>
                </div>
              </div>
            ) : (
              <>
                <div style={d.accountsHeader}>
                  <span style={{ color: 'rgba(255,255,255,0.45)', fontSize: 13 }}>
                    {items.length} bank{items.length === 1 ? '' : 's'} connected ·{' '}
                    {items.reduce((sum, i) => sum + i.accounts.length, 0)} account
                    {items.reduce((sum, i) => sum + i.accounts.length, 0) === 1 ? '' : 's'}
                  </span>
                  <ConnectBank variant="secondary" label="Add another bank" onConnected={() => { fetchItems(); fetchData(); }} />
                </div>
                <div style={d.itemsGrid}>
                  {items.map(item => (
                    <div key={item.id} style={d.itemCard}>
                      <div style={d.itemHeader}>
                        <div style={d.itemHeaderLeft}>
                          <div style={d.itemIcon}><Building2 size={18} color="#a5b4fc" /></div>
                          <div>
                            <div style={d.itemName}>{item.institutionName ?? 'Bank'}</div>
                            <div style={d.itemMeta}>
                              <Clock size={11} />
                              {item.lastSyncedAt ? `Synced ${formatRelative(item.lastSyncedAt)}` : 'Not yet synced'}
                            </div>
                          </div>
                        </div>
                        <button style={d.disconnectBtn} onClick={() => handleDisconnect(item.id)}>
                          <Trash2 size={14} />
                        </button>
                      </div>
                      {item.syncError && (
                        <div style={d.itemError}>
                          <AlertTriangle size={13} /> {item.syncError}
                        </div>
                      )}
                      <div style={d.accountList}>
                        {item.accounts.length === 0 ? (
                          <p style={{ color: 'rgba(255,255,255,0.3)', fontSize: 13 }}>No accounts yet — try syncing.</p>
                        ) : (
                          item.accounts.map(acc => (
                            <div key={acc.id} style={d.accountRow}>
                              <div style={d.accountLeft}>
                                <div style={d.accountIcon}>
                                  {acc.type === 'credit' ? <CreditCard size={14} /> : <Wallet size={14} />}
                                </div>
                                <div>
                                  <div style={d.accountName}>{acc.name}</div>
                                  <div style={d.accountSubtype}>
                                    {acc.subtype ?? acc.type ?? 'Account'}
                                    {acc.mask && ` · ••${acc.mask}`}
                                  </div>
                                </div>
                              </div>
                              <div style={d.accountBalance}>
                                {acc.currentBalance != null ? formatCurrency(acc.currentBalance) : '—'}
                              </div>
                            </div>
                          ))
                        )}
                      </div>
                    </div>
                  ))}
                </div>
              </>
            )}
          </div>
        )}

        {/* ── Budgets Tab ── */}
        {activeTab === 'budgets' && (
          <div className="fade-in">
            <BudgetPanel summary={summary} loading={loadingData} month={month} year={year} onRefresh={fetchData} />
          </div>
        )}

        {/* ── Insights Tab ── */}
        {activeTab === 'insights' && (
          <div className="fade-in">
            <InsightsPanel
              insights={insights}
              loading={loadingInsights}
              month={month}
              year={year}
              onGenerate={handleGetInsights}
            />
          </div>
        )}
      </main>

      {/* ── Add Transaction Modal ── */}
      {showForm && (
        <div style={d.overlay} onClick={e => { if (e.target === e.currentTarget) setShowForm(false); }}>
          <div style={d.modal}>
            <div style={d.modalHeader}>
              <h3 style={d.modalTitle}>New Transaction</h3>
              <button style={d.closeBtn} onClick={() => setShowForm(false)}><X size={18} /></button>
            </div>
            <form onSubmit={handleAddTransaction} style={d.modalForm}>
              <div style={d.formRow}>
                <div style={d.formField}>
                  <label style={d.formLabel}>Description</label>
                  <input style={d.formInput} placeholder="e.g. Grocery run" required
                    value={form.description} onChange={e => setForm({ ...form, description: e.target.value })}
                    onFocus={e => Object.assign(e.target.style, formInputFocus)}
                    onBlur={e => Object.assign(e.target.style, formInputBlur)} />
                </div>
                <div style={d.formField}>
                  <label style={d.formLabel}>Amount (USD)</label>
                  <input style={d.formInput} type="number" placeholder="0.00" required min="0.01" step="0.01"
                    value={form.amount} onChange={e => setForm({ ...form, amount: e.target.value })}
                    onFocus={e => Object.assign(e.target.style, formInputFocus)}
                    onBlur={e => Object.assign(e.target.style, formInputBlur)} />
                </div>
              </div>
              <div style={d.formRow}>
                <div style={d.formField}>
                  <label style={d.formLabel}>Type</label>
                  <select style={d.formInput} value={form.type} onChange={e => setForm({ ...form, type: e.target.value as TransactionType })}>
                    <option value="EXPENSE">Expense</option>
                    <option value="INCOME">Income</option>
                  </select>
                </div>
                <div style={d.formField}>
                  <label style={d.formLabel}>Category</label>
                  <select style={d.formInput} value={form.category} onChange={e => setForm({ ...form, category: e.target.value })}>
                    {CATEGORIES.map(c => <option key={c}>{c}</option>)}
                  </select>
                </div>
              </div>
              <div style={d.formField}>
                <label style={d.formLabel}>Date</label>
                <input style={d.formInput} type="date" value={form.date}
                  onChange={e => setForm({ ...form, date: e.target.value })}
                  onFocus={e => Object.assign(e.target.style, formInputFocus)}
                  onBlur={e => Object.assign(e.target.style, formInputBlur)} />
              </div>
              <div style={d.formField}>
                <label style={d.formLabel}>Notes <span style={{ color: 'rgba(255,255,255,0.25)' }}>(optional)</span></label>
                <textarea style={{ ...d.formInput, resize: 'none' }} placeholder="Any extra details…" rows={2}
                  value={form.notes} onChange={e => setForm({ ...form, notes: e.target.value })} />
              </div>
              <div style={d.modalActions}>
                <button type="button" style={d.cancelBtn} onClick={() => setShowForm(false)}>Cancel</button>
                <button type="submit" style={{ ...d.confirmBtn, opacity: submitting ? 0.7 : 1 }} disabled={submitting}>
                  {submitting ? 'Adding…' : 'Add Transaction'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

const formInputFocus = { borderColor: 'rgba(99,102,241,0.6)', boxShadow: '0 0 0 3px rgba(99,102,241,0.12)' };
const formInputBlur = { borderColor: 'rgba(255,255,255,0.1)', boxShadow: 'none' };

const d: Record<string, React.CSSProperties> = {
  root: { display: 'flex', minHeight: '100vh', background: '#070714' },

  sidebar: {
    width: 236, background: 'rgba(255,255,255,0.025)',
    borderRight: '1px solid rgba(255,255,255,0.07)',
    display: 'flex', flexDirection: 'column',
    padding: '24px 0', position: 'sticky', top: 0, height: '100vh', flexShrink: 0,
  },
  sidebarTop: { flex: 1, padding: '0 14px' },
  logo: { display: 'flex', alignItems: 'center', gap: 10, padding: '4px 10px', marginBottom: 32 },
  logoIcon: {
    width: 34, height: 34, borderRadius: 9,
    background: 'linear-gradient(135deg, #6366f1, #8b5cf6)',
    display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
  },
  logoText: { fontSize: 17, fontWeight: 700, letterSpacing: '-0.3px', color: '#fff' },
  nav: { display: 'flex', flexDirection: 'column', gap: 2 },
  navBtn: {
    display: 'flex', alignItems: 'center', gap: 10,
    padding: '10px 12px', borderRadius: 10, border: 'none',
    background: 'transparent', color: 'rgba(255,255,255,0.45)',
    cursor: 'pointer', width: '100%', textAlign: 'left',
    fontSize: 14, fontWeight: 500, transition: 'all 0.15s', position: 'relative',
  },
  navBtnActive: { background: 'rgba(99,102,241,0.18)', color: '#c7d2fe' },
  aiBadge: {
    marginLeft: 'auto', fontSize: 10, fontWeight: 700,
    background: 'linear-gradient(135deg, #6366f1, #8b5cf6)',
    color: '#fff', padding: '2px 6px', borderRadius: 4, letterSpacing: '0.05em',
  },
  sidebarFooter: {
    borderTop: '1px solid rgba(255,255,255,0.07)', padding: '16px 14px 0',
    display: 'flex', alignItems: 'center', gap: 8,
  },
  userRow: { display: 'flex', alignItems: 'center', gap: 10, flex: 1, minWidth: 0 },
  avatar: {
    width: 34, height: 34, borderRadius: 10, flexShrink: 0,
    background: 'linear-gradient(135deg, #6366f1, #8b5cf6)',
    display: 'flex', alignItems: 'center', justifyContent: 'center',
    fontSize: 14, fontWeight: 700, color: '#fff',
  },
  userInfo: { minWidth: 0 },
  userName: { fontSize: 13, fontWeight: 600, color: '#e2e8f0', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' },
  userEmail: { fontSize: 11, color: 'rgba(255,255,255,0.3)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' },
  logoutBtn: { background: 'transparent', border: 'none', cursor: 'pointer', display: 'flex', padding: 6, borderRadius: 8, flexShrink: 0 },

  main: { flex: 1, padding: '32px 40px', overflowY: 'auto', minWidth: 0 },
  header: { display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 32, gap: 16, flexWrap: 'wrap' },
  greeting: { fontSize: 22, fontWeight: 700, color: '#f1f5f9', letterSpacing: '-0.4px', marginBottom: 4 },
  subGreeting: { fontSize: 13, color: 'rgba(255,255,255,0.4)' },
  headerRight: { display: 'flex', gap: 10, alignItems: 'center', flexShrink: 0 },
  monthPicker: {
    display: 'flex', alignItems: 'center', gap: 4,
    background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.1)',
    borderRadius: 10, padding: '6px 8px',
  },
  arrowBtn: { background: 'transparent', border: 'none', cursor: 'pointer', color: 'rgba(255,255,255,0.5)', display: 'flex', alignItems: 'center', padding: '2px 4px', borderRadius: 6 },
  monthLabel: { fontSize: 13, fontWeight: 600, color: '#e2e8f0', minWidth: 72, textAlign: 'center' },
  addBtn: {
    display: 'flex', alignItems: 'center', gap: 8,
    padding: '9px 18px', borderRadius: 10, border: 'none',
    background: 'linear-gradient(135deg, #6366f1, #8b5cf6)',
    color: '#fff', fontWeight: 600, fontSize: 13, cursor: 'pointer',
    boxShadow: '0 4px 14px rgba(99,102,241,0.3)',
  },
  syncBtn: {
    display: 'flex', alignItems: 'center', gap: 6,
    padding: '8px 14px', borderRadius: 9,
    background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.1)',
    color: 'rgba(255,255,255,0.7)', fontWeight: 500, fontSize: 12, cursor: 'pointer',
  },

  plaidBanner: {
    display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 20,
    background: 'linear-gradient(135deg, rgba(99,102,241,0.12), rgba(139,92,246,0.08))',
    border: '1px solid rgba(99,102,241,0.25)',
    borderRadius: 16, padding: '20px 24px', marginBottom: 20,
  },
  plaidBannerLeft: { display: 'flex', alignItems: 'center', gap: 16 },
  plaidBannerIcon: {
    width: 44, height: 44, borderRadius: 12, flexShrink: 0,
    background: 'rgba(99,102,241,0.2)', border: '1px solid rgba(99,102,241,0.3)',
    display: 'flex', alignItems: 'center', justifyContent: 'center',
  },
  plaidBannerTitle: { fontSize: 16, fontWeight: 700, color: '#f1f5f9', marginBottom: 4, letterSpacing: '-0.2px' },
  plaidBannerSub: { fontSize: 13, color: 'rgba(255,255,255,0.55)', maxWidth: 480 },
  warnBanner: {
    display: 'flex', alignItems: 'center', gap: 10,
    background: 'rgba(245,158,11,0.08)', border: '1px solid rgba(245,158,11,0.25)',
    borderRadius: 12, padding: '12px 16px', marginBottom: 20, color: '#fbbf24', fontSize: 13,
  },
  codeChip: { background: 'rgba(255,255,255,0.08)', padding: '1px 6px', borderRadius: 4, fontFamily: 'ui-monospace, SFMono-Regular, monospace', fontSize: 12 },

  statsGrid: { display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 14, marginBottom: 20 },
  chartsRow: { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginBottom: 20 },
  chartCard: { background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 16, padding: '22px 24px' },
  chartTitle: { fontSize: 13, fontWeight: 600, color: 'rgba(255,255,255,0.6)', marginBottom: 16, letterSpacing: '0.02em', textTransform: 'uppercase' },
  legend: { display: 'flex', flexDirection: 'column', gap: 6, marginTop: 12 },
  legendItem: { display: 'flex', alignItems: 'center', gap: 8, fontSize: 13 },
  legendDot: { width: 8, height: 8, borderRadius: '50%', flexShrink: 0 },
  legendName: { color: 'rgba(255,255,255,0.6)', flex: 1 },
  legendVal: { color: 'rgba(255,255,255,0.85)', fontWeight: 600, fontVariantNumeric: 'tabular-nums' },

  recentCard: { background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 16, padding: '22px 24px', marginBottom: 20 },
  recentHeader: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 },
  viewAllBtn: { background: 'none', border: 'none', color: '#818cf8', fontSize: 13, fontWeight: 600, cursor: 'pointer' },
  recentList: { display: 'flex', flexDirection: 'column', gap: 2 },
  recentRow: { display: 'flex', alignItems: 'center', gap: 12, padding: '10px 4px', borderRadius: 8 },
  txIcon: { width: 36, height: 36, borderRadius: 10, flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' },
  txMeta: { flex: 1, minWidth: 0 },
  txDesc: { display: 'block', fontSize: 14, fontWeight: 500, color: '#e2e8f0', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' },
  txCat: { display: 'block', fontSize: 12, color: 'rgba(255,255,255,0.35)', marginTop: 2 },
  txAmt: { fontSize: 14, fontWeight: 700, whiteSpace: 'nowrap', fontVariantNumeric: 'tabular-nums' },

  insightBtn: {
    display: 'inline-flex', alignItems: 'center', gap: 8,
    padding: '12px 22px', borderRadius: 10, border: 'none',
    background: 'linear-gradient(135deg, #6366f1, #8b5cf6)',
    color: '#fff', fontWeight: 600, fontSize: 14, cursor: 'pointer',
    boxShadow: '0 4px 16px rgba(99,102,241,0.3)',
  },

  connectEmpty: { background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.07)', borderRadius: 18, padding: '56px 32px', textAlign: 'center' },
  connectEmptyIcon: { width: 72, height: 72, borderRadius: 18, margin: '0 auto 20px', background: 'rgba(99,102,241,0.15)', border: '1px solid rgba(99,102,241,0.25)', display: 'flex', alignItems: 'center', justifyContent: 'center' },
  connectEmptyTitle: { fontSize: 22, fontWeight: 700, color: '#f1f5f9', marginBottom: 10, letterSpacing: '-0.3px' },
  connectEmptySub: { fontSize: 14, color: 'rgba(255,255,255,0.5)', maxWidth: 460, margin: '0 auto', lineHeight: 1.6 },
  trustRow: { display: 'flex', justifyContent: 'center', gap: 28, marginTop: 28, flexWrap: 'wrap' },
  trustItem: { fontSize: 12, color: 'rgba(255,255,255,0.35)', fontWeight: 500 },
  accountsHeader: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 18 },
  itemsGrid: { display: 'flex', flexDirection: 'column', gap: 14 },
  itemCard: { background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 16, padding: 22 },
  itemHeader: { display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 16 },
  itemHeaderLeft: { display: 'flex', alignItems: 'center', gap: 14 },
  itemIcon: { width: 40, height: 40, borderRadius: 10, background: 'rgba(99,102,241,0.12)', border: '1px solid rgba(99,102,241,0.2)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 },
  itemName: { fontSize: 15, fontWeight: 700, color: '#f1f5f9', marginBottom: 3 },
  itemMeta: { fontSize: 12, color: 'rgba(255,255,255,0.4)', display: 'flex', alignItems: 'center', gap: 5 },
  itemError: { background: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.2)', color: '#fca5a5', fontSize: 12, padding: '8px 12px', borderRadius: 8, marginBottom: 12, display: 'flex', alignItems: 'center', gap: 6 },
  disconnectBtn: { background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.08)', color: 'rgba(255,255,255,0.4)', borderRadius: 8, cursor: 'pointer', display: 'flex', padding: 8 },
  accountList: { display: 'flex', flexDirection: 'column', gap: 6, borderTop: '1px solid rgba(255,255,255,0.06)', paddingTop: 14 },
  accountRow: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0' },
  accountLeft: { display: 'flex', alignItems: 'center', gap: 12 },
  accountIcon: { width: 30, height: 30, borderRadius: 8, background: 'rgba(255,255,255,0.05)', color: 'rgba(255,255,255,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center' },
  accountName: { fontSize: 14, fontWeight: 500, color: '#e2e8f0' },
  accountSubtype: { fontSize: 12, color: 'rgba(255,255,255,0.35)', marginTop: 2, textTransform: 'capitalize' },
  accountBalance: { fontSize: 15, fontWeight: 700, color: '#f1f5f9', fontVariantNumeric: 'tabular-nums' },

  overlay: { position: 'fixed', inset: 0, background: 'rgba(7,7,20,0.8)', backdropFilter: 'blur(4px)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 200, padding: 24 },
  modal: { background: '#0f0f24', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 18, padding: '28px 32px', width: '100%', maxWidth: 520, boxShadow: '0 24px 80px rgba(0,0,0,0.6)' },
  modalHeader: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 },
  modalTitle: { fontSize: 18, fontWeight: 700, color: '#fff', letterSpacing: '-0.3px' },
  closeBtn: { background: 'rgba(255,255,255,0.07)', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 8, cursor: 'pointer', color: 'rgba(255,255,255,0.5)', display: 'flex', padding: 7 },
  modalForm: { display: 'flex', flexDirection: 'column', gap: 16 },
  formRow: { display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 },
  formField: { display: 'flex', flexDirection: 'column', gap: 7 },
  formLabel: { fontSize: 12, fontWeight: 600, color: 'rgba(255,255,255,0.5)', textTransform: 'uppercase', letterSpacing: '0.05em' },
  formInput: { padding: '11px 14px', borderRadius: 9, border: '1px solid rgba(255,255,255,0.1)', background: 'rgba(255,255,255,0.05)', color: '#fff', fontSize: 14, outline: 'none', transition: 'border-color 0.15s, box-shadow 0.15s', fontFamily: 'inherit' },
  modalActions: { display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 4 },
  cancelBtn: { padding: '10px 18px', borderRadius: 9, border: '1px solid rgba(255,255,255,0.1)', background: 'rgba(255,255,255,0.05)', color: 'rgba(255,255,255,0.6)', fontSize: 14, fontWeight: 500, cursor: 'pointer' },
  confirmBtn: { padding: '10px 20px', borderRadius: 9, border: 'none', background: 'linear-gradient(135deg, #6366f1, #8b5cf6)', color: '#fff', fontSize: 14, fontWeight: 600, cursor: 'pointer', boxShadow: '0 4px 14px rgba(99,102,241,0.3)' },
};
