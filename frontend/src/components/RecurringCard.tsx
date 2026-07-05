import { Repeat, ArrowUpRight, ArrowDownRight, DollarSign } from 'lucide-react';
import { RecurringItem } from '../types';
import { CATEGORY_ICONS, formatCurrency, formatDate } from '../utils/dashboard';
import { d } from '../pages/dashboard.styles';
import { useRecurring } from '../hooks/useDashboardData';

const CADENCE_LABEL: Record<string, string> = {
  WEEKLY: 'Weekly',
  BIWEEKLY: 'Every 2 weeks',
  MONTHLY: 'Monthly',
};

/**
 * Subscriptions and regular bills detected from transaction history, with the
 * next expected charge and any recent price change.
 */
export default function RecurringCard() {
  const { data: items, isPending } = useRecurring();

  if (isPending || !items || items.length === 0) return null;

  const monthlyTotal = items.reduce((sum, i) => sum + i.monthlyEstimate, 0);

  return (
    <div style={d.recentCard}>
      <div style={d.recentHeader}>
        <h3 style={d.chartTitle}>
          <Repeat size={13} style={{ marginRight: 6, verticalAlign: '-2px' }} />
          Recurring & Subscriptions
        </h3>
        <span style={{ fontSize: 13, color: 'rgba(255,255,255,0.5)' }}>
          ≈ <strong style={{ color: '#e2e8f0' }}>{formatCurrency(monthlyTotal)}</strong>/mo
        </span>
      </div>
      <div style={d.recentList}>
        {items.map((item: RecurringItem) => (
          <div key={item.name + item.cadence} style={d.recentRow}>
            <div style={{ ...d.txIcon, background: 'rgba(99,102,241,0.12)' }}>
              <span style={{ color: '#818cf8' }}>
                {CATEGORY_ICONS[item.category] ?? <DollarSign size={14} />}
              </span>
            </div>
            <div style={d.txMeta}>
              <span style={d.txDesc}>
                {item.name}
                {item.priceChangePct != null && (
                  <span style={{
                    marginLeft: 8, fontSize: 11, fontWeight: 600,
                    color: item.priceChangePct > 0 ? '#f87171' : '#34d399',
                  }}>
                    {item.priceChangePct > 0
                      ? <><ArrowUpRight size={11} style={{ verticalAlign: '-1px' }} /> +{item.priceChangePct}%</>
                      : <><ArrowDownRight size={11} style={{ verticalAlign: '-1px' }} /> {item.priceChangePct}%</>}
                  </span>
                )}
              </span>
              <span style={d.txCat}>
                {CADENCE_LABEL[item.cadence]} · next {formatDate(item.nextDueDate)}
              </span>
            </div>
            <span style={{ ...d.txAmt, color: '#e2e8f0' }}>{formatCurrency(item.lastAmount)}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
