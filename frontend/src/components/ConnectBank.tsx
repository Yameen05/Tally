import { useCallback, useEffect, useState } from 'react';
import { usePlaidLink } from 'react-plaid-link';
import { plaidApi } from '../services/api';
import { Landmark, Loader2 } from 'lucide-react';

interface Props {
  onConnected: () => void;
  variant?: 'primary' | 'secondary';
  label?: string;
}

/**
 * Button that opens Plaid Link. On success, exchanges the public token
 * for a permanent connection and triggers `onConnected` so the dashboard refreshes.
 */
export default function ConnectBank({ onConnected, variant = 'primary', label }: Props) {
  const [linkToken, setLinkToken] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Fetch a fresh link token whenever the user wants to connect
  const fetchLinkToken = useCallback(async () => {
    setError(null);
    setLoading(true);
    try {
      const res = await plaidApi.createLinkToken();
      setLinkToken(res.data.linkToken);
    } catch (e: any) {
      setError(e.response?.data?.message || 'Plaid is not configured on the server.');
      setLoading(false);
    }
  }, []);

  const onSuccess = useCallback(async (publicToken: string, metadata: any) => {
    setLoading(true);
    try {
      await plaidApi.exchange(
        publicToken,
        metadata?.institution?.institution_id,
        metadata?.institution?.name
      );
      onConnected();
    } catch (e: any) {
      setError(e.response?.data?.message || 'Failed to link account. Please try again.');
    } finally {
      setLoading(false);
      setLinkToken(null);
    }
  }, [onConnected]);

  const { open, ready } = usePlaidLink({
    token: linkToken ?? '',
    onSuccess,
    onExit: () => { setLoading(false); setLinkToken(null); },
  });

  // Auto-open Plaid Link the moment the link token arrives and SDK is ready
  useEffect(() => {
    if (linkToken && ready) {
      open();
      setLoading(false);
    }
  }, [linkToken, ready, open]);

  const isPrimary = variant === 'primary';
  const style: React.CSSProperties = isPrimary ? s.primary : s.secondary;

  return (
    <div>
      <button
        style={{ ...style, opacity: loading ? 0.7 : 1 }}
        onClick={fetchLinkToken}
        disabled={loading}
      >
        {loading
          ? <><Loader2 size={16} style={{ animation: 'spin 0.8s linear infinite' }} /> Connecting…</>
          : <><Landmark size={16} /> {label ?? 'Connect Bank Account'}</>
        }
      </button>
      {error && <p style={s.errorText}>{error}</p>}
    </div>
  );
}

const s: Record<string, React.CSSProperties> = {
  primary: {
    display: 'inline-flex', alignItems: 'center', gap: 8,
    padding: '12px 22px', borderRadius: 10, border: 'none',
    background: 'linear-gradient(135deg, #6366f1, #8b5cf6)',
    color: '#fff', fontWeight: 600, fontSize: 14, cursor: 'pointer',
    boxShadow: '0 4px 18px rgba(99,102,241,0.35)',
  },
  secondary: {
    display: 'inline-flex', alignItems: 'center', gap: 8,
    padding: '9px 16px', borderRadius: 9,
    background: 'rgba(255,255,255,0.05)',
    border: '1px solid rgba(255,255,255,0.1)',
    color: '#e2e8f0', fontWeight: 500, fontSize: 13, cursor: 'pointer',
  },
  errorText: {
    color: '#fca5a5', fontSize: 13, marginTop: 10,
    background: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.2)',
    borderRadius: 8, padding: '8px 12px',
  },
};
