import { Sparkles } from 'lucide-react';
import { MONTH_NAMES } from '../utils/dashboard';

interface Props {
  insights: string;
  loading: boolean;
  month: number;
  year: number;
  onGenerate: () => void;
}

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
          <h3 style={{ color: '#fff', fontWeight: 700, fontSize: 17, marginBottom: 4 }}>
            AI Financial Advisor
          </h3>
          <p style={{ color: 'rgba(255,255,255,0.4)', fontSize: 13 }}>
            Powered by GPT-4 · Analysis for {MONTH_NAMES[month - 1]} {year}
          </p>
        </div>
      </div>

      {loading ? (
        <div style={s.loading}>
          <div style={s.spinner} />
          <p style={{ color: 'rgba(255,255,255,0.5)', fontSize: 14, marginTop: 16 }}>
            Analyzing your finances…
          </p>
        </div>
      ) : insights ? (
        <div>
          <pre style={s.text}>{insights}</pre>
          <button style={{ ...insightBtn, marginTop: 24 }} onClick={onGenerate}>
            <Sparkles size={16} />
            Regenerate
          </button>
        </div>
      ) : (
        <div style={s.empty}>
          <p style={{ color: 'rgba(255,255,255,0.4)', fontSize: 14, marginBottom: 24 }}>
            Get personalized advice based on your spending patterns.
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
    background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.07)',
    borderRadius: 16, padding: '28px 32px', maxWidth: 720,
  },
  meta: {
    display: 'flex', alignItems: 'center', gap: 14, marginBottom: 24,
    paddingBottom: 20, borderBottom: '1px solid rgba(255,255,255,0.07)',
  },
  icon: {
    width: 44, height: 44, borderRadius: 12, flexShrink: 0,
    background: 'rgba(99,102,241,0.15)', border: '1px solid rgba(99,102,241,0.25)',
    display: 'flex', alignItems: 'center', justifyContent: 'center',
  },
  loading: { textAlign: 'center', padding: '48px 0' },
  spinner: {
    width: 36, height: 36, borderRadius: '50%',
    border: '3px solid rgba(255,255,255,0.1)', borderTopColor: '#6366f1',
    animation: 'spin 0.8s linear infinite', margin: '0 auto',
  },
  text: {
    color: 'rgba(255,255,255,0.75)', lineHeight: 1.85,
    whiteSpace: 'pre-wrap', fontFamily: 'inherit', fontSize: 14,
  },
  empty: { textAlign: 'center', padding: '40px 0' },
};
