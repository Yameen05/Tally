import { useState, useEffect } from 'react';
import { ArrowLeftRight, DollarSign, Trash2, Download } from 'lucide-react';
import { Transaction } from '../types';
import { CATEGORY_ICONS, formatDate, formatCurrency } from '../utils/dashboard';
import { Skeleton, EmptyState } from './common';

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

  // Reset pagination whenever the transaction list changes (e.g. month switch)
  useEffect(() => { setVisibleCount(PAGE_SIZE); }, [transactions]);

  if (loading) {
    return (
      <div style={s.tableCard}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14, padding: '8px 20px' }}>
          {[...Array(5)].map((_, i) => <Skeleton key={i} h={52} radius={10} />)}
        </div>
      </div>
    );
  }

  if (transactions.length === 0) {
    return (
      <div style={s.tableCard}>
        <EmptyState
          icon={<ArrowLeftRight size={36} />}
          text="No transactions for this month"
          sub="Click + Add Transaction to get started"
        />
      </div>
    );
  }

  const visible = transactions.slice(0, visibleCount);
  const remaining = transactions.length - visibleCount;

  return (
    <div style={s.tableCard}>
      {/* Toolbar */}
      <div style={s.toolbar}>
        <span style={s.count}>
          {visibleCount < transactions.length
            ? `Showing ${visibleCount} of ${transactions.length}`
            : `${transactions.length} transaction${transactions.length === 1 ? '' : 's'}`}
        </span>
        <button style={s.exportBtn} onClick={() => exportCsv(transactions)} title="Export all transactions as CSV">
          <Download size={13} />
          Export CSV
        </button>
      </div>

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
              <td style={{ ...s.td, color: 'rgba(255,255,255,0.4)', fontSize: 12, whiteSpace: 'nowrap' }}>
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
                    <div style={{ fontSize: 14, fontWeight: 500, color: '#e2e8f0' }}>
                      {t.merchantName ?? t.description}
                    </div>
                    {t.notes && <div style={{ fontSize: 12, color: 'rgba(255,255,255,0.3)', marginTop: 2 }}>{t.notes}</div>}
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
    background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.07)',
    borderRadius: 16, padding: '8px 0', overflowX: 'auto',
  },
  toolbar: {
    display: 'flex', justifyContent: 'space-between', alignItems: 'center',
    padding: '10px 20px 6px', borderBottom: '1px solid rgba(255,255,255,0.05)',
  },
  count: { fontSize: 12, color: 'rgba(255,255,255,0.3)', fontWeight: 500 },
  exportBtn: {
    display: 'inline-flex', alignItems: 'center', gap: 6,
    padding: '6px 12px', borderRadius: 7,
    background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.1)',
    color: 'rgba(255,255,255,0.6)', fontSize: 12, fontWeight: 500, cursor: 'pointer',
  },
  table: { width: '100%', borderCollapse: 'collapse' },
  th: {
    textAlign: 'left', padding: '10px 20px', fontSize: 11, fontWeight: 600,
    color: 'rgba(255,255,255,0.3)', borderBottom: '1px solid rgba(255,255,255,0.06)',
    textTransform: 'uppercase', letterSpacing: '0.06em',
  },
  tr: { borderBottom: '1px solid rgba(255,255,255,0.04)', transition: 'background 0.1s' },
  td: { padding: '14px 20px', fontSize: 13, color: 'rgba(255,255,255,0.75)' },
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
    color: 'rgba(255,255,255,0.2)', display: 'flex', alignItems: 'center',
    padding: 6, borderRadius: 6, transition: 'color 0.15s',
  },
  spinnerSm: {
    width: 14, height: 14, borderRadius: '50%',
    border: '2px solid rgba(255,255,255,0.15)', borderTopColor: 'rgba(255,255,255,0.5)',
    animation: 'spin 0.6s linear infinite',
  },
  loadMore: { padding: '12px 20px', borderTop: '1px solid rgba(255,255,255,0.05)', textAlign: 'center' },
  loadMoreBtn: {
    background: 'transparent', border: '1px solid rgba(255,255,255,0.1)',
    borderRadius: 8, color: 'rgba(255,255,255,0.55)', fontSize: 13,
    fontWeight: 500, cursor: 'pointer', padding: '8px 18px',
  },
  loadMoreSub: { color: 'rgba(255,255,255,0.3)', fontSize: 12 },
};
