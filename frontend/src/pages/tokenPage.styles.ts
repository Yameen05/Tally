import type React from 'react';

/** Shared styling for the small standalone token pages (verify email, reset password). */
export const tokenPageStyles: Record<string, React.CSSProperties> = {
  root: {
    minHeight: '100vh', background: 'var(--bg)',
    display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 24,
  },
  card: {
    background: 'var(--surface-1)', border: '1px solid var(--border)',
    borderRadius: 18, padding: '48px 40px', maxWidth: 420, width: '100%',
    display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 14,
    textAlign: 'center',
  },
  title: { fontSize: 20, fontWeight: 700, color: 'var(--text-1)', letterSpacing: '-0.3px' },
  sub: { fontSize: 14, color: 'var(--text-2)', lineHeight: 1.6 },
  button: {
    marginTop: 10, padding: '11px 24px', borderRadius: 10, border: 'none',
    background: 'linear-gradient(135deg, #6366f1, #8b5cf6)',
    color: '#fff', fontWeight: 600, fontSize: 14, cursor: 'pointer',
    textDecoration: 'none', display: 'inline-block',
  },
  form: { display: 'flex', flexDirection: 'column', gap: 14, width: '100%', marginTop: 8 },
  input: {
    width: '100%', padding: '12px 16px', borderRadius: 10,
    border: '1px solid var(--border-strong)', background: 'var(--input-bg)',
    color: 'var(--text-1)', fontSize: 14, outline: 'none', boxSizing: 'border-box',
  },
  error: {
    padding: '10px 14px', borderRadius: 8, width: '100%', boxSizing: 'border-box',
    background: 'rgba(239,68,68,0.1)', border: '1px solid rgba(239,68,68,0.25)',
    color: '#fca5a5', fontSize: 13, textAlign: 'left',
  },
};
