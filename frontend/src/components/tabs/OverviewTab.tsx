import {
  PieChart, Pie, Cell, Tooltip, ResponsiveContainer,
  BarChart, Bar, XAxis, YAxis, CartesianGrid,
} from 'recharts';
import {
  TrendingUp, TrendingDown, Wallet, ArrowLeftRight,
  Target, Sparkles, DollarSign, AlertTriangle, Landmark,
} from 'lucide-react';
import { MonthlySummary, Transaction, ConnectedItem } from '../../types';
import { PALETTE, CATEGORY_ICONS, formatCurrency, formatDate } from '../../utils/dashboard';
import { d } from '../../pages/dashboard.styles';
import { Skeleton, EmptyState } from '../common';
import StatCard from '../StatCard';
import ConnectBank from '../ConnectBank';
import NetWorthCard from '../NetWorthCard';
import RecurringCard from '../RecurringCard';

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

interface Props {
  summary: MonthlySummary | undefined;
  transactions: Transaction[];
  loading: boolean;
  plaidConfigured: boolean | null;
  items: ConnectedItem[];
  onConnected: () => void;
  onViewTransactions: () => void;
  onGetInsights: () => void;
  loadingInsights: boolean;
}

export default function OverviewTab({
  summary, transactions, loading, plaidConfigured, items,
  onConnected, onViewTransactions, onGetInsights, loadingInsights,
}: Props) {
  const pieData = summary
    ? Object.entries(summary.expensesByCategory).map(([name, value]) => ({ name, value })).sort((a, b) => b.value - a.value)
    : [];
  const barData = summary?.budgets.map(b => ({ category: b.category, percentageUsed: b.percentageUsed })) ?? [];
  const netPositive = summary ? summary.netBalance >= 0 : true;

  return (
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
          <ConnectBank onConnected={onConnected} />
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
          icon={<TrendingUp size={18} />} color="#10b981" loading={loading} />
        <StatCard label="Total Expenses" value={summary ? formatCurrency(summary.totalExpenses) : '$0.00'}
          icon={<TrendingDown size={18} />} color="#ef4444" loading={loading} />
        <StatCard label="Net Balance" value={summary ? formatCurrency(summary.netBalance) : '$0.00'}
          icon={<Wallet size={18} />} color={netPositive ? '#10b981' : '#ef4444'} loading={loading} />
        <StatCard label="Transactions" value={loading ? '' : String(transactions.length)}
          icon={<ArrowLeftRight size={18} />} color="#6366f1" loading={loading} />
      </div>

      <div style={d.chartsRow}>
        <div style={d.chartCard}>
          <h3 style={d.chartTitle}>Expenses by Category</h3>
          {loading ? (
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
                {pieData.map((entry, i) => (
                  <div key={i} style={d.legendItem}>
                    <span style={{ ...d.legendDot, background: PALETTE[i % PALETTE.length] }} />
                    <span style={d.legendName}>{entry.name}</span>
                    <span style={d.legendVal}>{formatCurrency(entry.value)}</span>
                  </div>
                ))}
              </div>
            </>
          )}
        </div>

        <div style={d.chartCard}>
          <h3 style={d.chartTitle}>Budget Usage</h3>
          {loading ? (
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

      <NetWorthCard />
      <RecurringCard />

      {!loading && transactions.length > 0 && (
        <div style={d.recentCard}>
          <div style={d.recentHeader}>
            <h3 style={d.chartTitle}>Recent Transactions</h3>
            <button style={d.viewAllBtn} onClick={onViewTransactions}>View all →</button>
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

      <button style={d.insightBtn} onClick={onGetInsights} disabled={loadingInsights}>
        <Sparkles size={16} />
        {loadingInsights ? 'Analyzing your finances…' : 'Get AI Spending Insights'}
      </button>
    </div>
  );
}
