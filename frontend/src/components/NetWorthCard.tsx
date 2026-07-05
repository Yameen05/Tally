import { AreaChart, Area, XAxis, YAxis, Tooltip, ResponsiveContainer } from 'recharts';
import { formatCurrency } from '../utils/dashboard';
import { d } from '../pages/dashboard.styles';
import { useNetWorth } from '../hooks/useDashboardData';

function NetWorthTooltip({ active, payload }: any) {
  if (!active || !payload?.length) return null;
  const point = payload[0].payload;
  return (
    <div style={{ background: '#1a1a35', border: '1px solid rgba(255,255,255,0.12)', borderRadius: 10, padding: '10px 14px' }}>
      <p style={{ color: 'rgba(255,255,255,0.6)', fontSize: 12, marginBottom: 4 }}>{point.date}</p>
      <p style={{ color: '#fff', fontWeight: 700, fontSize: 15 }}>{formatCurrency(point.netWorth)}</p>
      <p style={{ color: 'rgba(255,255,255,0.45)', fontSize: 11, marginTop: 4 }}>
        Assets {formatCurrency(point.assets)} · Debts {formatCurrency(point.liabilities)}
      </p>
    </div>
  );
}

/**
 * Net worth trend from daily balance snapshots. Renders nothing until the user
 * has connected accounts and at least one snapshot exists.
 */
export default function NetWorthCard() {
  const { data: points } = useNetWorth(90);

  if (!points || points.length === 0) return null;

  const current = points[points.length - 1];
  const first = points[0];
  const change = current.netWorth - first.netWorth;
  const positive = change >= 0;

  return (
    <div style={{ ...d.chartCard, marginBottom: 20 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 8 }}>
        <h3 style={{ ...d.chartTitle, marginBottom: 0 }}>Net Worth</h3>
        <div style={{ textAlign: 'right' }}>
          <span style={{ fontSize: 20, fontWeight: 700, color: '#f1f5f9', fontVariantNumeric: 'tabular-nums' }}>
            {formatCurrency(current.netWorth)}
          </span>
          {points.length > 1 && (
            <span style={{ marginLeft: 10, fontSize: 12, fontWeight: 600, color: positive ? '#34d399' : '#f87171' }}>
              {positive ? '+' : ''}{formatCurrency(change)} in {points.length} days
            </span>
          )}
        </div>
      </div>
      <ResponsiveContainer width="100%" height={180}>
        <AreaChart data={points} margin={{ top: 8, left: 0, right: 0, bottom: 0 }}>
          <defs>
            <linearGradient id="netWorthFill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#6366f1" stopOpacity={0.35} />
              <stop offset="100%" stopColor="#6366f1" stopOpacity={0} />
            </linearGradient>
          </defs>
          <XAxis dataKey="date" tick={{ fill: 'rgba(255,255,255,0.3)', fontSize: 11 }}
            axisLine={false} tickLine={false} minTickGap={40} />
          <YAxis tick={{ fill: 'rgba(255,255,255,0.3)', fontSize: 11 }} axisLine={false} tickLine={false}
            tickFormatter={(v: number) => `$${Math.round(v / 1000)}k`} width={44} domain={['auto', 'auto']} />
          <Tooltip content={<NetWorthTooltip />} />
          <Area type="monotone" dataKey="netWorth" stroke="#818cf8" strokeWidth={2}
            fill="url(#netWorthFill)" dot={false} />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}
