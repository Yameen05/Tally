import { Skeleton } from './common';

interface Props {
  label: string;
  value: string;
  icon: React.ReactNode;
  color: string;
  loading: boolean;
}

export default function StatCard({ label, value, icon, color, loading }: Props) {
  return (
    <div style={s.card}>
      <div style={{ ...s.iconWrap, background: color + '18', border: `1px solid ${color}28` }}>
        <span style={{ color }}>{icon}</span>
      </div>
      <p style={s.label}>{label}</p>
      {loading ? <Skeleton h={28} w="70%" /> : <p style={s.value}>{value}</p>}
    </div>
  );
}

const s: Record<string, React.CSSProperties> = {
  card: {
    background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.08)',
    borderRadius: 16, padding: '20px 22px',
  },
  iconWrap: {
    width: 38, height: 38, borderRadius: 10,
    display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 14,
  },
  label: {
    fontSize: 12, color: 'rgba(255,255,255,0.45)', fontWeight: 500,
    marginBottom: 6, letterSpacing: '0.03em', textTransform: 'uppercase',
  },
  value: { fontSize: 24, fontWeight: 700, color: '#f1f5f9', letterSpacing: '-0.5px' },
};
