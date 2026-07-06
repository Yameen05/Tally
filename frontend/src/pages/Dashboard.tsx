import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { insightApi } from '../services/api';
import { TransactionRequest, InsightsResponse } from '../types';
import { useAuth } from '../hooks/useAuth';
import {
  useSummary, useMonthTransactions, usePlaidStatus, usePlaidItems,
  useCreateTransaction, useDeleteTransaction, usePlaidSync, usePlaidDisconnect,
} from '../hooks/useDashboardData';
import TransactionTable from '../components/TransactionTable';
import BudgetPanel from '../components/BudgetPanel';
import InsightsPanel from '../components/InsightsPanel';
import TransactionModal from '../components/TransactionModal';
import VerifyEmailBanner from '../components/VerifyEmailBanner';
import OverviewTab from '../components/tabs/OverviewTab';
import AccountsTab from '../components/tabs/AccountsTab';
import { MONTH_NAMES, SHORT_MONTHS, getTimeOfDay } from '../utils/dashboard';
import { useTheme } from '../hooks/useTheme';
import { useIsMobile } from '../hooks/useMediaQuery';
import { d } from './dashboard.styles';
import {
  LayoutDashboard, ArrowLeftRight, Target, Sparkles,
  LogOut, Plus, TrendingUp, ChevronLeft, ChevronRight,
  Landmark, RefreshCw, Sun, Moon,
} from 'lucide-react';

type Tab = 'overview' | 'transactions' | 'budgets' | 'accounts' | 'insights';

export default function Dashboard() {
  const { user, logout } = useAuth();
  const [theme, toggleTheme] = useTheme();
  const isMobile = useIsMobile();
  const queryClient = useQueryClient();
  const now = new Date();
  const [month, setMonth] = useState(now.getMonth() + 1);
  const [year, setYear] = useState(now.getFullYear());
  const [activeTab, setActiveTab] = useState<Tab>('overview');
  const [showForm, setShowForm] = useState(false);
  const [insights, setInsights] = useState<InsightsResponse | null>(null);
  const [loadingInsights, setLoadingInsights] = useState(false);

  const summaryQuery = useSummary(year, month);
  const transactionsQuery = useMonthTransactions(year, month);
  const plaidStatusQuery = usePlaidStatus();
  const itemsQuery = usePlaidItems();

  const createTransaction = useCreateTransaction();
  const deleteTransaction = useDeleteTransaction();
  const plaidSync = usePlaidSync();
  const plaidDisconnect = usePlaidDisconnect();

  const summary = summaryQuery.data;
  const transactions = transactionsQuery.data ?? [];
  const loadingData = summaryQuery.isPending || transactionsQuery.isPending;
  const plaidConfigured = plaidStatusQuery.data ?? null;
  const items = itemsQuery.data ?? [];

  const refreshAll = () => {
    queryClient.invalidateQueries({ queryKey: ['summary'] });
    queryClient.invalidateQueries({ queryKey: ['transactions'] });
    queryClient.invalidateQueries({ queryKey: ['plaid-items'] });
  };

  const handleAddTransaction = (data: TransactionRequest) => {
    createTransaction.mutate(data, { onSuccess: () => setShowForm(false) });
  };

  const handleDisconnect = (itemId: number) => {
    if (!confirm('Disconnect this bank? Existing transactions will remain.')) return;
    plaidDisconnect.mutate(itemId);
  };

  const handleGetInsights = async () => {
    setLoadingInsights(true);
    setActiveTab('insights');
    try {
      const res = await insightApi.getInsights(year, month);
      setInsights(res.data);
    } catch {
      setInsights({ cards: [], narrative: 'Failed to load insights. Please check your connection and try again.' });
    }
    finally { setLoadingInsights(false); }
  };

  const handleLogout = () => {
    queryClient.clear();
    logout();
  };

  const prevMonth = () => { if (month === 1) { setMonth(12); setYear(y => y - 1); } else setMonth(m => m - 1); };
  const nextMonth = () => { if (month === 12) { setMonth(1); setYear(y => y + 1); } else setMonth(m => m + 1); };

  const navItems = [
    { key: 'overview', label: 'Overview', icon: <LayoutDashboard size={17} /> },
    { key: 'transactions', label: 'Transactions', icon: <ArrowLeftRight size={17} /> },
    { key: 'accounts', label: 'Accounts', icon: <Landmark size={17} /> },
    { key: 'budgets', label: 'Budgets', icon: <Target size={17} /> },
    { key: 'insights', label: 'AI Insights', icon: <Sparkles size={17} /> },
  ] as const;

  const userInitial = user?.name?.charAt(0).toUpperCase() ?? '?';
  const syncing = plaidSync.isPending;

  const themeToggleButton = (
    <button style={d.logoutBtn} onClick={toggleTheme}
      title={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}>
      {theme === 'dark'
        ? <Sun size={16} color="var(--text-3)" />
        : <Moon size={16} color="var(--text-3)" />}
    </button>
  );

  return (
    <div style={d.root}>
      {/* ── Sidebar (desktop) ── */}
      {!isMobile && (
      <aside style={d.sidebar}>
        <div style={d.sidebarTop}>
          <div style={d.logo}>
            <div style={d.logoIcon}><TrendingUp size={18} color="#fff" /></div>
            <span style={d.logoText}>Tally</span>
          </div>
          <nav style={d.nav}>
            {navItems.map(({ key, label, icon }) => {
              const active = activeTab === key;
              return (
                <button key={key} style={{ ...d.navBtn, ...(active ? d.navBtnActive : {}) }}
                  onClick={() => setActiveTab(key)}>
                  <span style={{ color: active ? '#818cf8' : 'var(--text-3)', display: 'flex' }}>{icon}</span>
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
          {themeToggleButton}
          <button style={d.logoutBtn} onClick={handleLogout} title="Sign out">
            <LogOut size={16} color="var(--text-3)" />
          </button>
        </div>
      </aside>
      )}

      {/* ── Bottom tab bar (mobile) ── */}
      {isMobile && (
        <nav style={d.mobileNav}>
          {navItems.map(({ key, label, icon }) => {
            const active = activeTab === key;
            return (
              <button key={key} style={{ ...d.mobileNavBtn, color: active ? '#818cf8' : 'var(--text-3)' }}
                onClick={() => setActiveTab(key)}>
                {icon}
                <span style={d.mobileNavLabel}>{label === 'AI Insights' ? 'Insights' : label}</span>
              </button>
            );
          })}
        </nav>
      )}

      {/* ── Main ── */}
      <main style={{ ...d.main, ...(isMobile ? d.mainMobile : {}) }}>
        {isMobile && (
          <div style={d.mobileTopBar}>
            <div style={d.logo}>
              <div style={d.logoIcon}><TrendingUp size={18} color="#fff" /></div>
              <span style={d.logoText}>Tally</span>
            </div>
            <div style={{ display: 'flex', gap: 4 }}>
              {themeToggleButton}
              <button style={d.logoutBtn} onClick={handleLogout} title="Sign out">
                <LogOut size={16} color="var(--text-3)" />
              </button>
            </div>
          </div>
        )}
        {user && !user.emailVerified && <VerifyEmailBanner />}
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
              <button style={d.syncBtn} onClick={() => plaidSync.mutate()} disabled={syncing}
                title="Sync transactions from connected banks">
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

        {activeTab === 'overview' && (
          <OverviewTab
            summary={summary}
            transactions={transactions}
            loading={loadingData}
            plaidConfigured={plaidConfigured}
            items={items}
            onConnected={refreshAll}
            onViewTransactions={() => setActiveTab('transactions')}
            onGetInsights={handleGetInsights}
            loadingInsights={loadingInsights}
          />
        )}

        {activeTab === 'transactions' && (
          <div className="fade-in">
            <TransactionTable
              transactions={transactions}
              loading={loadingData}
              deletingId={deleteTransaction.isPending ? (deleteTransaction.variables ?? null) : null}
              onDelete={(id) => deleteTransaction.mutate(id)}
            />
          </div>
        )}

        {activeTab === 'accounts' && (
          <AccountsTab
            items={items}
            plaidConfigured={plaidConfigured}
            onConnected={refreshAll}
            onDisconnect={handleDisconnect}
          />
        )}

        {activeTab === 'budgets' && (
          <div className="fade-in">
            <BudgetPanel summary={summary ?? null} loading={loadingData} month={month} year={year} />
          </div>
        )}

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

      {showForm && (
        <TransactionModal
          onClose={() => setShowForm(false)}
          onSubmit={handleAddTransaction}
          submitting={createTransaction.isPending}
        />
      )}
    </div>
  );
}
