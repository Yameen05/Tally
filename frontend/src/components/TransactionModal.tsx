import { useState } from 'react';
import { X } from 'lucide-react';
import { TransactionRequest, TransactionType, CATEGORIES } from '../types';
import { d, formInputFocus, formInputBlur } from '../pages/dashboard.styles';

interface Props {
  onClose: () => void;
  onSubmit: (data: TransactionRequest) => void;
  submitting: boolean;
}

export default function TransactionModal({ onClose, onSubmit, submitting }: Props) {
  const [form, setForm] = useState({
    description: '', amount: '', type: 'EXPENSE' as TransactionType,
    category: 'Food', date: new Date().toISOString().split('T')[0], notes: '',
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const amount = parseFloat(form.amount);
    if (isNaN(amount) || amount <= 0 || submitting) return;
    onSubmit({ ...form, amount });
  };

  return (
    <div style={d.overlay} onClick={e => { if (e.target === e.currentTarget) onClose(); }}>
      <div style={d.modal}>
        <div style={d.modalHeader}>
          <h3 style={d.modalTitle}>New Transaction</h3>
          <button style={d.closeBtn} onClick={onClose}><X size={18} /></button>
        </div>
        <form onSubmit={handleSubmit} style={d.modalForm}>
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
            <label style={d.formLabel}>Notes <span style={{ color: 'var(--text-3)' }}>(optional)</span></label>
            <textarea style={{ ...d.formInput, resize: 'none' }} placeholder="Any extra details…" rows={2}
              value={form.notes} onChange={e => setForm({ ...form, notes: e.target.value })} />
          </div>
          <div style={d.modalActions}>
            <button type="button" style={d.cancelBtn} onClick={onClose}>Cancel</button>
            <button type="submit" style={{ ...d.confirmBtn, opacity: submitting ? 0.7 : 1 }} disabled={submitting}>
              {submitting ? 'Adding…' : 'Add Transaction'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
