import { useState } from 'react';
import { Target, DollarSign, Flame, CheckCircle2, AlertTriangle, Plus, Trash2, X } from 'lucide-react';
import { MonthlySummary, CATEGORIES } from '../types';
import { CATEGORY_ICONS, formatCurrency } from '../utils/dashboard';
import { Skeleton, EmptyState } from './common';
import { budgetApi } from '../services/api';

interface Props {
  summary: MonthlySummary | null;
  loading: boolean;
  month: number;
  year: number;
  onRefresh: () => void;
}

export default function BudgetPanel({ summary, loading, month, year, onRefresh }: Props) {
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ category: CATEGORIES[0], limitAmount: '' });
  const [submitting, setSubmitting] = useState(false);
  const [deletingId, setDeletingId] = useState<number | null>(null);

  const existingCategories = new Set(summary?.budgets.map(b => b.category) ?? []);
  const availableCategories = CATEGORIES.filter(c => !existingCategories.has(c));

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    const limit = parseFloat(form.limitAmount);
    if (isNaN(limit) || limit <= 0 || submitting) return;
    setSubmitting(true);
    try {
      await budgetApi.createOrUpdate({ category: form.category, limitAmount: limit, month, year });
      setShowForm(false);
      setForm({ category: availableCategories[0] ?? CATEGORIES[0], limitAmount: '' });
      onRefresh();
    } catch (e) { console.error(e); }
    finally { setSubmitting(false); }
  };

  const handleDelete = async (id: number) => {
    setDeletingId(id);
    try { await budgetApi.delete(id); onRefresh(); }
    catch (e) { console.error(e); }
    finally { setDeletingId(null); }
  };

  if (loading) {
    return (
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 16 }}>
        {[...Array(4)].map((_, i) => <Skeleton key={i} h={130} radius={14} />)}
      </div>
    );
  }

  const hasBudgets = summary && summary.budgets.length > 0;

  return (
    <>
      {/* Header with Add button */}
      <div style={s.header}>
        <span style={s.headerLabel}>
          {hasBudgets ? `${summary.budgets.length} budget${summary.budgets.length === 1 ? '' : 's'} set` : 'No budgets yet'}
        </span>
        {availableCategories.length > 0 && (
          <button style={s.addBtn} onClick={() => {
            setForm(f => ({ ...f, category: availableCategories[0] }));
            setShowForm(true);
          }}>
            <Plus size={14} />
            Set Budget
          </button>
        )}
      </div>

      {!hasBudgets && (
        <EmptyState
          icon={<Target size={36} />}
          text="No budgets set for this month"
          sub="Click 'Set Budget' to start tracking your spending limits"
        />
      )}

      {hasBudgets && (
        <div style={s.grid}>
          {summary.budgets.map(b => {
            const pct = b.percentageUsed;
            const overBudget = pct >= 100;
            const warning = pct >= 80 && pct < 100;
            const barColor = overBudget ? '#ef4444' : warning ? '#f59e0b' : '#6366f1';
            return (
              <div key={b.id} style={s.card}>
                <div style={s.top}>
                  <div style={s.catRow}>
                    <div style={{ ...s.catIcon, background: barColor + '18', border: `1px solid ${barColor}28` }}>
                      <span style={{ color: barColor }}>
                        {CATEGORY_ICONS[b.category] ?? <DollarSign size={14} />}
                      </span>
                    </div>
                    <span style={s.catName}>{b.category}</span>
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    {overBudget && <Flame size={14} color="#ef4444" />}
                    {warning && <AlertTriangle size={14} color="#f59e0b" />}
                    {!overBudget && !warning && pct > 0 && <CheckCircle2 size={14} color="#10b981" />}
                    <span style={{ ...s.pctLabel, color: barColor }}>{pct.toFixed(0)}%</span>
                    <button
                      style={s.deleteBtn}
                      disabled={deletingId === b.id}
                      onClick={() => handleDelete(b.id)}
                      title="Remove budget"
                    >
                      {deletingId === b.id
                        ? <div style={s.spinnerSm} />
                        : <Trash2 size={12} />}
                    </button>
                  </div>
                </div>

                <div style={s.track}>
                  <div style={{ ...s.fill, width: `${Math.min(pct, 100)}%`, background: barColor, boxShadow: `0 0 8px ${barColor}55` }} />
                </div>

                <div style={s.amounts}>
                  <div>
                    <span style={s.amtLabel}>Spent</span>
                    <span style={{ ...s.amtVal, color: barColor }}>{formatCurrency(b.spentAmount)}</span>
                  </div>
                  <div style={{ textAlign: 'center' }}>
                    <span style={s.amtLabel}>Remaining</span>
                    <span style={{ ...s.amtVal, color: overBudget ? '#ef4444' : 'rgba(255,255,255,0.7)' }}>
                      {formatCurrency(Math.max(b.remaining, 0))}
                    </span>
                  </div>
                  <div style={{ textAlign: 'right' }}>
                    <span style={s.amtLabel}>Budget</span>
                    <span style={s.amtVal}>{formatCurrency(b.limitAmount)}</span>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Add Budget Modal */}
      {showForm && (
        <div style={s.overlay} onClick={e => { if (e.target === e.currentTarget) setShowForm(false); }}>
          <div style={s.modal}>
            <div style={s.modalHeader}>
              <h3 style={s.modalTitle}>Set Budget</h3>
              <button style={s.closeBtn} onClick={() => setShowForm(false)}><X size={18} /></button>
            </div>
            <form onSubmit={handleAdd} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              <div style={s.formField}>
                <label style={s.formLabel}>Category</label>
                <select style={s.formInput} value={form.category}
                  onChange={e => setForm(f => ({ ...f, category: e.target.value }))}>
                  {availableCategories.map(c => <option key={c}>{c}</option>)}
                </select>
              </div>
              <div style={s.formField}>
                <label style={s.formLabel}>Monthly Limit (USD)</label>
                <input style={s.formInput} type="number" placeholder="0.00" required min="0.01" step="0.01"
                  value={form.limitAmount}
                  onChange={e => setForm(f => ({ ...f, limitAmount: e.target.value }))}
                  onFocus={e => Object.assign(e.target.style, focusStyle)}
                  onBlur={e => Object.assign(e.target.style, blurStyle)} />
              </div>
              <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
                <button type="button" style={s.cancelBtn} onClick={() => setShowForm(false)}>Cancel</button>
                <button type="submit" style={{ ...s.confirmBtn, opacity: submitting ? 0.7 : 1 }} disabled={submitting}>
                  {submitting ? 'Saving…' : 'Save Budget'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </>
  );
}

const focusStyle = { borderColor: 'rgba(99,102,241,0.6)', boxShadow: '0 0 0 3px rgba(99,102,241,0.12)' };
const blurStyle = { borderColor: 'rgba(255,255,255,0.1)', boxShadow: 'none' };

const s: Record<string, React.CSSProperties> = {
  header: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 },
  headerLabel: { fontSize: 13, color: 'rgba(255,255,255,0.4)' },
  addBtn: {
    display: 'inline-flex', alignItems: 'center', gap: 6,
    padding: '8px 14px', borderRadius: 9, border: 'none',
    background: 'linear-gradient(135deg, #6366f1, #8b5cf6)',
    color: '#fff', fontWeight: 600, fontSize: 13, cursor: 'pointer',
  },
  grid: { display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 16 },
  card: {
    background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.08)',
    borderRadius: 16, padding: '20px 22px',
  },
  top: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 },
  catRow: { display: 'flex', alignItems: 'center', gap: 10 },
  catIcon: {
    width: 32, height: 32, borderRadius: 8,
    display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
  },
  catName: { fontSize: 15, fontWeight: 600, color: '#e2e8f0' },
  pctLabel: { fontSize: 15, fontWeight: 700 },
  deleteBtn: {
    background: 'transparent', border: 'none', cursor: 'pointer',
    color: 'rgba(255,255,255,0.25)', display: 'flex', alignItems: 'center',
    padding: 4, borderRadius: 5, marginLeft: 2,
  },
  spinnerSm: {
    width: 12, height: 12, borderRadius: '50%',
    border: '2px solid rgba(255,255,255,0.15)', borderTopColor: 'rgba(255,255,255,0.5)',
    animation: 'spin 0.6s linear infinite',
  },
  track: { height: 7, borderRadius: 4, background: 'rgba(255,255,255,0.07)', marginBottom: 14, overflow: 'hidden' },
  fill: { height: '100%', borderRadius: 4, transition: 'width 0.5s ease' },
  amounts: { display: 'flex', justifyContent: 'space-between' },
  amtLabel: {
    display: 'block', fontSize: 11, color: 'rgba(255,255,255,0.35)', fontWeight: 500,
    marginBottom: 3, textTransform: 'uppercase', letterSpacing: '0.05em',
  },
  amtVal: {
    display: 'block', fontSize: 14, fontWeight: 700,
    color: 'rgba(255,255,255,0.8)', fontVariantNumeric: 'tabular-nums',
  },
  overlay: {
    position: 'fixed', inset: 0, background: 'rgba(7,7,20,0.8)', backdropFilter: 'blur(4px)',
    display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 200, padding: 24,
  },
  modal: {
    background: '#0f0f24', border: '1px solid rgba(255,255,255,0.1)',
    borderRadius: 18, padding: '28px 32px', width: '100%', maxWidth: 400,
    boxShadow: '0 24px 80px rgba(0,0,0,0.6)',
  },
  modalHeader: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 },
  modalTitle: { fontSize: 18, fontWeight: 700, color: '#fff', letterSpacing: '-0.3px' },
  closeBtn: {
    background: 'rgba(255,255,255,0.07)', border: '1px solid rgba(255,255,255,0.1)',
    borderRadius: 8, cursor: 'pointer', color: 'rgba(255,255,255,0.5)', display: 'flex', padding: 7,
  },
  formField: { display: 'flex', flexDirection: 'column', gap: 7 },
  formLabel: { fontSize: 12, fontWeight: 600, color: 'rgba(255,255,255,0.5)', textTransform: 'uppercase', letterSpacing: '0.05em' },
  formInput: {
    padding: '11px 14px', borderRadius: 9, border: '1px solid rgba(255,255,255,0.1)',
    background: 'rgba(255,255,255,0.05)', color: '#fff', fontSize: 14,
    outline: 'none', transition: 'border-color 0.15s, box-shadow 0.15s', fontFamily: 'inherit',
  },
  cancelBtn: {
    padding: '10px 18px', borderRadius: 9, border: '1px solid rgba(255,255,255,0.1)',
    background: 'rgba(255,255,255,0.05)', color: 'rgba(255,255,255,0.6)', fontSize: 14, fontWeight: 500, cursor: 'pointer',
  },
  confirmBtn: {
    padding: '10px 20px', borderRadius: 9, border: 'none',
    background: 'linear-gradient(135deg, #6366f1, #8b5cf6)',
    color: '#fff', fontSize: 14, fontWeight: 600, cursor: 'pointer',
  },
};
