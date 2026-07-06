import { Sparkles, TrendingUp, Trophy, AlertTriangle, Info } from 'lucide-react';
import { MONTH_NAMES } from '../utils/dashboard';
import { InsightsResponse, InsightKind } from '../types';

interface Props {
  insights: InsightsResponse | null;
  loading: boolean;
  month: number;
  year: number;
  onGenerate: () => void;
}

const KIND_META: Record<InsightKind, { icon: React.ReactNode; color: string }> = {
  TREND: { icon: <TrendingUp size={15} />, color: '#818cf8' },
  WIN: { icon: <Trophy size={15} />, color: '#34d399' },
  WATCH: { icon: <AlertTriangle size={15} />, color: '#fbbf24' },
  INFO: { icon: <Info size={15} />, color: 'var(--text-2)' },
};

const insightBtn: React.CSSProperties = {
  display: 'inline-flex', alignItems: 'center', gap: 8,
  padding: '12px 22px', borderRadius: 10, border: 'none',
  background: 'linear-gradient(135deg, #6366f1, #8b5cf6)',
  color: '#fff', fontWeight: 600, fontSize: 14, cursor: 'pointer',
  boxShadow: '0 4px 16px rgba(99,102,241,0.3)',
};

export default function InsightsPanel({ insights, loading, month, year, onGenerate }: Props) {
  return (
    <div style={s.wrap}>
      <div style={s.meta}>
        <div style={s.icon}><Sparkles size={22} color="#818cf8" /></div>
        <div>
          <h3 style={{ color: 'var(--text-1)', fontWeight: 700, fontSize: 17, marginBottom: 4 }}>
            Financial Insights
          </h3>
          <p style={{ color: 'var(--text-3)', fontSize: 13 }}>
            Computed from your data, with AI commentary · {MONTH_NAMES[month - 1]} {year}
          </p>
        </div>
      </div>

      {loading ? (
        <div style={s.loading}>
          <div style={s.spinner} />
          <p style={{ color: 'var(--text-2)', fontSize: 14, marginTop: 16 }}>
            Analyzing your finances…
          </p>
        </div>
      ) : insights ? (
        <div>
          {insights.cards.length > 0 && (
            <div style={s.cardGrid}>
              {insights.cards.map((card, i) => {
                const meta = KIND_META[card.kind] ?? KIND_META.INFO;
                return (
                  <div key={i} style={s.card}>
                    <div style={{ ...s.cardHeader, color: meta.color }}>
                      {meta.icon}
                      <span style={s.cardTitle}>{card.title}</span>
                    </div>
                    <p style={s.cardDetail}>{card.detail}</p>
                  </div>
                );
              })}
            </div>
          )}

          {insights.narrative && (
            <div style={s.narrativeBox}>
              <div style={s.narrativeLabel}>
                <Sparkles size={12} /> AI advisor
              </div>
              <pre style={s.text}>{insights.narrative}</pre>
            </div>
          )}

          <button style={{ ...insightBtn, marginTop: 24 }} onClick={onGenerate}>
            <Sparkles size={16} />
            Regenerate
          </button>
        </div>
      ) : (
        <div style={s.empty}>
          <p style={{ color: 'var(--text-3)', fontSize: 14, marginBottom: 24 }}>
            Get instant stats plus personalized advice based on your spending patterns.
          </p>
          <button style={insightBtn} onClick={onGenerate}>
            <Sparkles size={16} />
            Generate Insights
          </button>
        </div>
      )}
    </div>
  );
}

const s: Record<string, React.CSSProperties> = {
  wrap: {
    background: 'var(--surface-1)', border: '1px solid var(--border)',
    borderRadius: 16, padding: '28px 32px', maxWidth: 720,
  },
  meta: {
    display: 'flex', alignItems: 'center', gap: 14, marginBottom: 24,
    paddingBottom: 20, borderBottom: '1px solid var(--border)',
  },
  icon: {
    width: 44, height: 44, borderRadius: 12, flexShrink: 0,
    background: 'rgba(99,102,241,0.15)', border: '1px solid rgba(99,102,241,0.25)',
    display: 'flex', alignItems: 'center', justifyContent: 'center',
  },
  cardGrid: { display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: 12 },
  card: {
    background: 'var(--surface-1)', border: '1px solid var(--border)',
    borderRadius: 12, padding: '14px 16px',
  },
  cardHeader: { display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 },
  cardTitle: { fontSize: 13, fontWeight: 600, color: 'var(--text-1)' },
  cardDetail: { fontSize: 13, color: 'var(--text-2)', lineHeight: 1.55 },
  narrativeBox: {
    marginTop: 18, padding: '16px 18px', borderRadius: 12,
    background: 'rgba(99,102,241,0.06)', border: '1px solid rgba(99,102,241,0.18)',
  },
  narrativeLabel: {
    display: 'inline-flex', alignItems: 'center', gap: 6,
    fontSize: 11, fontWeight: 700, color: '#a5b4fc',
    textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 8,
  },
  loading: { textAlign: 'center', padding: '48px 0' },
  spinner: {
    width: 36, height: 36, borderRadius: '50%',
    border: '3px solid var(--border-strong)', borderTopColor: '#6366f1',
    animation: 'spin 0.8s linear infinite', margin: '0 auto',
  },
  text: {
    color: 'var(--text-2)', lineHeight: 1.85, margin: 0,
    whiteSpace: 'pre-wrap', fontFamily: 'inherit', fontSize: 14,
  },
  empty: { textAlign: 'center', padding: '40px 0' },
};
