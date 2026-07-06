import { useState, useEffect, useMemo, useRef } from 'react';
import { ArrowLeftRight, DollarSign, Trash2, Download, Upload, Search, X } from 'lucide-react';
import { Transaction } from '../types';
import { CATEGORY_ICONS, formatDate, formatCurrency } from '../utils/dashboard';
import { Skeleton, EmptyState } from './common';
import { useImportTransactions } from '../hooks/useDashboardData';

interface Props {
  transactions: Transaction[];
  loading: boolean;
  deletingId: number | null;
  onDelete: (id: number) => void;
}

const PAGE_SIZE = 20;

function exportCsv(transactions: Transaction[]) {
  const header = 'Date,Description,Category,Type,Amount,Notes';
  const escape = (s: string) => `"${s.replace(/"/g, '""')}"`;
  const rows = transactions.map(t =>
    [
      t.date,
      escape(t.merchantName ?? t.description),
      t.category,
      t.type,
      t.amount,
      escape(t.notes ?? ''),
    ].join(',')
  );
  const csv = [header, ...rows].join('\n');
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = 'transactions.csv';
  a.click();
  URL.revokeObjectURL(url);
}

export default function TransactionTable({ transactions, loading, deletingId, onDelete }: Props) {
  const [visibleCount, setVisibleCount] = useState(PAGE_SIZE);
  const [search, setSearch] = useState('');
  const [typeFilter, setTypeFilter] = useState<'ALL' | 'INCOME' | 'EXPENSE'>('ALL');
  const [categoryFilter, setCategoryFilter] = useState('All');
  const fileInputRef = useRef<HTMLInputElement>(null);
  const importCsvMutation = useImportTransactions();

  // Reset pagination whenever the underlying list or the filters change
  useEffect(() => { setVisibleCount(PAGE_SIZE); }, [transactions, search, typeFilter, categoryFilter]);

  const categories = useMemo(
    () => [...new Set(transactions.map(t => t.category))].sort(),
    [transactions]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return transactions.filter(t => {
      if (typeFilter !== 'ALL' && t.type !== typeFilter) return false;
      if (categoryFilter !== 'All' && t.category !== categoryFilter) return false;
      if (!q) return true;
      return [t.description, t.merchantName, t.notes, t.category]
        .some(field => field?.toLowerCase().includes(q));
    });
  }, [transactions, search, typeFilter, categoryFilter]);

  const handleImportFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) importCsvMutation.mutate(file);
    e.target.value = ''; // allow re-importing the same file
  };

  if (loading) {
    return (
      <div style={s.tableCard}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14, padding: '8px 20px' }}>
          {[...Array(5)].map((_, i) => <Skeleton key={i} h={52} radius={10} />)}
        </div>
      </div>
    );
  }

  const hasActiveFilters = search.trim() !== '' || typeFilter !== 'ALL' || categoryFilter !== 'All';

  const importControls = (
    <>
      <input ref={fileInputRef} type="file" accept=".csv,text/csv" style={{ display: 'none' }}
        onChange={handleImportFile} />
      <button style={s.exportBtn} onClick={() => fileInputRef.current?.click()}
        disabled={importCsvMutation.isPending}
        title="Import transactions from a CSV file (Date,Description,Category,Type,Amount,Notes)">
        <Upload size={13} />
        {importCsvMutation.isPending ? 'Importing…' : 'Import CSV'}
      </button>
    </>
  );

  if (transactions.length === 0) {
    return (
      <div style={s.tableCard}>
        <div style={{ ...s.toolbar, justifyContent: 'flex-end' }}>{importControls}</div>
        <EmptyState
          icon={<ArrowLeftRight size={36} />}
          text="No transactions for this month"
          sub="Click + Add Transaction, or import a CSV"
        />
      </div>
    );
  }

  const visible = filtered.slice(0, visibleCount);
  const remaining = filtered.length - visibleCount;

  return (
    <div style={s.tableCard}>
      {/* Toolbar */}
      <div style={s.toolbar}>
        <div style={s.filters}>
          <div style={s.searchWrap}>
            <Search size={13} style={{ color: 'var(--text-3)', flexShrink: 0 }} />
            <input
              style={s.searchInput}
              placeholder="Search transactions…"
              value={search}
              onChange={e => setSearch(e.target.value)}
            />
            {search && (
              <button style={s.clearBtn} onClick={() => setSearch('')} title="Clear search">
                <X size={12} />
              </button>
            )}
          </div>
          <select style={s.filterSelect} value={typeFilter}
            onChange={e => setTypeFilter(e.target.value as typeof typeFilter)}>
            <option value="ALL">All types</option>
            <option value="INCOME">Income</option>
            <option value="EXPENSE">Expense</option>
          </select>
          <select style={s.filterSelect} value={categoryFilter}
            onChange={e => setCategoryFilter(e.target.value)}>
            <option value="All">All categories</option>
            {categories.map(c => <option key={c}>{c}</option>)}
          </select>
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <span style={s.count}>
            {hasActiveFilters
              ? `${filtered.length} of ${transactions.length}`
              : `${transactions.length} transaction${transactions.length === 1 ? '' : 's'}`}
          </span>
          {importControls}
          <button style={s.exportBtn} onClick={() => exportCsv(filtered)}
            title="Export the listed transactions as CSV">
            <Download size={13} />
            Export CSV
          </button>
        </div>
      </div>

      {filtered.length === 0 && (
        <EmptyState
          icon={<Search size={32} />}
          text="No transactions match your filters"
          sub="Try a different search or clear the filters"
        />
      )}

      {filtered.length > 0 && (
      <table style={s.table}>
        <thead>
          <tr>
            {['Date', 'Description', 'Category', 'Type', 'Amount', ''].map(h => (
              <th key={h} style={s.th}>{h}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {visible.map(t => (
            <tr key={t.id} style={s.tr}>
              <td style={{ ...s.td, color: 'var(--text-3)', fontSize: 12, whiteSpace: 'nowrap' }}>
                {formatDate(t.date)}
              </td>
              <td style={s.td}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  <div style={{
                    ...s.txIcon,
                    background: t.type === 'INCOME' ? 'rgba(16,185,129,0.1)' : 'rgba(99,102,241,0.1)',
                    width: 32, height: 32,
                  }}>
                    <span style={{ color: t.type === 'INCOME' ? '#10b981' : '#818cf8' }}>
                      {CATEGORY_ICONS[t.category] ?? <DollarSign size={14} />}
                    </span>
                  </div>
                  <div>
                    <div style={{ fontSize: 14, fontWeight: 500, color: 'var(--text-1)' }}>
                      {t.merchantName ?? t.description}
                    </div>
                    {t.notes && <div style={{ fontSize: 12, color: 'var(--text-3)', marginTop: 2 }}>{t.notes}</div>}
                    {t.pending && <div style={{ fontSize: 11, color: '#f59e0b', marginTop: 2 }}>Pending</div>}
                  </div>
                </div>
              </td>
              <td style={s.td}><span style={s.catBadge}>{t.category}</span></td>
              <td style={s.td}>
                <span style={{
                  ...s.typeBadge,
                  background: t.type === 'INCOME' ? 'rgba(16,185,129,0.12)' : 'rgba(239,68,68,0.1)',
                  color: t.type === 'INCOME' ? '#10b981' : '#f87171',
                }}>
                  {t.type === 'INCOME' ? '↑' : '↓'} {t.type}
                </span>
              </td>
              <td style={{ ...s.td, fontWeight: 700, fontSize: 14, color: t.type === 'INCOME' ? '#10b981' : '#f87171', whiteSpace: 'nowrap' }}>
                {t.type === 'INCOME' ? '+' : '−'}{formatCurrency(t.amount)}
              </td>
              <td style={s.td}>
                <button
                  style={s.deleteBtn}
                  disabled={deletingId === t.id}
                  onClick={() => onDelete(t.id)}
                  title="Delete transaction"
                >
                  {deletingId === t.id ? <div style={s.spinnerSm} /> : <Trash2 size={14} />}
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      )}

      {/* Load more */}
      {remaining > 0 && (
        <div style={s.loadMore}>
          <button style={s.loadMoreBtn} onClick={() => setVisibleCount(c => c + PAGE_SIZE)}>
            Show {Math.min(remaining, PAGE_SIZE)} more
            <span style={s.loadMoreSub}> ({remaining} remaining)</span>
          </button>
        </div>
      )}
    </div>
  );
}

const s: Record<string, React.CSSProperties> = {
  tableCard: {
    background: 'var(--surface-1)', border: '1px solid var(--border)',
    borderRadius: 16, padding: '8px 0', overflowX: 'auto',
  },
  toolbar: {
    display: 'flex', justifyContent: 'space-between', alignItems: 'center',
    padding: '10px 20px 10px', borderBottom: '1px solid var(--input-bg)',
    gap: 12, flexWrap: 'wrap',
  },
  count: { fontSize: 12, color: 'var(--text-3)', fontWeight: 500, whiteSpace: 'nowrap' },
  filters: { display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' },
  searchWrap: {
    display: 'flex', alignItems: 'center', gap: 7,
    background: 'var(--input-bg)', border: '1px solid var(--border-strong)',
    borderRadius: 8, padding: '6px 10px', minWidth: 200,
  },
  searchInput: {
    background: 'transparent', border: 'none', outline: 'none',
    color: 'var(--text-1)', fontSize: 13, width: '100%', fontFamily: 'inherit',
  },
  clearBtn: {
    background: 'transparent', border: 'none', cursor: 'pointer',
    color: 'var(--text-3)', display: 'flex', padding: 0,
  },
  filterSelect: {
    background: 'var(--input-bg)', border: '1px solid var(--border-strong)',
    borderRadius: 8, padding: '6px 10px', color: 'var(--text-2)',
    fontSize: 12, cursor: 'pointer', fontFamily: 'inherit', outline: 'none',
  },
  exportBtn: {
    display: 'inline-flex', alignItems: 'center', gap: 6,
    padding: '6px 12px', borderRadius: 7,
    background: 'var(--input-bg)', border: '1px solid var(--border-strong)',
    color: 'var(--text-2)', fontSize: 12, fontWeight: 500, cursor: 'pointer',
  },
  table: { width: '100%', borderCollapse: 'collapse' },
  th: {
    textAlign: 'left', padding: '10px 20px', fontSize: 11, fontWeight: 600,
    color: 'var(--text-3)', borderBottom: '1px solid var(--border)',
    textTransform: 'uppercase', letterSpacing: '0.06em',
  },
  tr: { borderBottom: '1px solid var(--surface-1)', transition: 'background 0.1s' },
  td: { padding: '14px 20px', fontSize: 13, color: 'var(--text-2)' },
  txIcon: {
    borderRadius: 10, flexShrink: 0,
    display: 'flex', alignItems: 'center', justifyContent: 'center',
  },
  catBadge: {
    background: 'rgba(99,102,241,0.12)', color: '#a5b4fc',
    padding: '3px 10px', borderRadius: 6, fontSize: 12, fontWeight: 500,
  },
  typeBadge: { padding: '3px 10px', borderRadius: 6, fontSize: 12, fontWeight: 600 },
  deleteBtn: {
    background: 'transparent', border: 'none', cursor: 'pointer',
    color: 'var(--text-3)', display: 'flex', alignItems: 'center',
    padding: 6, borderRadius: 6, transition: 'color 0.15s',
  },
  spinnerSm: {
    width: 14, height: 14, borderRadius: '50%',
    border: '2px solid var(--border-strong)', borderTopColor: 'var(--text-2)',
    animation: 'spin 0.6s linear infinite',
  },
  loadMore: { padding: '12px 20px', borderTop: '1px solid var(--input-bg)', textAlign: 'center' },
  loadMoreBtn: {
    background: 'transparent', border: '1px solid var(--border-strong)',
    borderRadius: 8, color: 'var(--text-2)', fontSize: 13,
    fontWeight: 500, cursor: 'pointer', padding: '8px 18px',
  },
  loadMoreSub: { color: 'var(--text-3)', fontSize: 12 },
};
