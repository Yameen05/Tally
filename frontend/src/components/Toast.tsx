import { createContext, useCallback, useContext, useMemo, useRef, useState, ReactNode } from 'react';
import { CheckCircle2, AlertTriangle } from 'lucide-react';

type ToastKind = 'success' | 'error' | 'warning';

interface ToastItem {
  id: number;
  kind: ToastKind;
  message: string;
}

interface ToastApi {
  success: (message: string) => void;
  error: (message: string) => void;
  warning: (message: string) => void;
}

const ToastContext = createContext<ToastApi | null>(null);

/** Pulls a human-readable message out of the backend's ApiError shape. */
export function apiErrorMessage(err: unknown, fallback = 'Something went wrong. Please try again.'): string {
  const data = (err as { response?: { data?: { message?: unknown; fieldErrors?: Record<string, unknown> } } })
    ?.response?.data;
  if (data?.fieldErrors) {
    const first = Object.values(data.fieldErrors)[0];
    if (typeof first === 'string' && first) return first;
  }
  if (typeof data?.message === 'string' && data.message) return data.message;
  return fallback;
}

const DISMISS_MS = 4500;

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const nextId = useRef(1);

  const push = useCallback((kind: ToastKind, message: string) => {
    const id = nextId.current++;
    setToasts(prev => [...prev.slice(-3), { id, kind, message }]);
    setTimeout(() => setToasts(prev => prev.filter(t => t.id !== id)), DISMISS_MS);
  }, []);

  const api = useMemo<ToastApi>(() => ({
    success: (message) => push('success', message),
    error: (message) => push('error', message),
    warning: (message) => push('warning', message),
  }), [push]);

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div style={s.stack} aria-live="polite">
        {toasts.map(t => (
          <div key={t.id} style={{ ...s.toast, ...s[t.kind] }} role="status">
            {t.kind === 'success'
              ? <CheckCircle2 size={15} color="#34d399" style={{ flexShrink: 0 }} />
              : <AlertTriangle size={15} color={t.kind === 'error' ? '#f87171' : '#fbbf24'} style={{ flexShrink: 0 }} />}
            <span>{t.message}</span>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastApi {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used within ToastProvider');
  return ctx;
}

const s: Record<string, React.CSSProperties> = {
  stack: {
    position: 'fixed', bottom: 24, right: 24, zIndex: 400,
    display: 'flex', flexDirection: 'column', gap: 10, maxWidth: 380,
  },
  toast: {
    display: 'flex', alignItems: 'flex-start', gap: 10,
    padding: '12px 16px', borderRadius: 12,
    background: '#14142c', border: '1px solid rgba(255,255,255,0.12)',
    color: '#e2e8f0', fontSize: 13, lineHeight: 1.45,
    boxShadow: '0 12px 40px rgba(0,0,0,0.5)',
    animation: 'fade-in 0.2s ease',
  },
  success: { borderColor: 'rgba(16,185,129,0.35)' },
  error: { borderColor: 'rgba(239,68,68,0.35)' },
  warning: { borderColor: 'rgba(245,158,11,0.4)' },
};
