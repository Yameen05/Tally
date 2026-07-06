export function Skeleton({
  w = '100%', h = 16, radius = 6,
}: { w?: string | number; h?: number; radius?: number }) {
  return (
    <div style={{
      width: w, height: h, borderRadius: radius,
      background: 'var(--border)',
      animation: 'pulse 1.5s ease-in-out infinite',
    }} />
  );
}

export function EmptyState({
  icon, text, sub,
}: { icon: React.ReactNode; text: string; sub?: string }) {
  return (
    <div style={{ textAlign: 'center', padding: '48px 24px', color: 'var(--text-3)' }}>
      <div style={{ marginBottom: 12 }}>{icon}</div>
      <p style={{ fontSize: 14, fontWeight: 500, color: 'var(--text-3)', marginBottom: 4 }}>{text}</p>
      {sub && <p style={{ fontSize: 13, color: 'var(--text-3)' }}>{sub}</p>}
    </div>
  );
}
