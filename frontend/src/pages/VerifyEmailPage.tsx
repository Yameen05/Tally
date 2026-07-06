import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { CheckCircle2, AlertTriangle, Loader2 } from 'lucide-react';
import { authApi } from '../services/api';
import { apiErrorMessage } from '../components/Toast';
import { tokenPageStyles as s } from './tokenPage.styles';

/** Landing page for the link in the verification email (/verify-email?token=…). */
export default function VerifyEmailPage() {
  const [params] = useSearchParams();
  const [status, setStatus] = useState<'working' | 'done' | 'failed'>('working');
  const [message, setMessage] = useState('');
  const fired = useRef(false);

  useEffect(() => {
    if (fired.current) return; // StrictMode double-mount would burn the token twice
    fired.current = true;
    const token = params.get('token');
    if (!token) {
      setStatus('failed');
      setMessage('This link is missing its token. Use the link from your email.');
      return;
    }
    authApi.verifyEmail(token)
      .then(() => setStatus('done'))
      .catch(err => { setStatus('failed'); setMessage(apiErrorMessage(err)); });
  }, [params]);

  return (
    <div style={s.root}>
      <div style={s.card}>
        {status === 'working' && (
          <>
            <Loader2 size={36} color="#818cf8" style={{ animation: 'spin 0.8s linear infinite' }} />
            <h2 style={s.title}>Verifying your email…</h2>
          </>
        )}
        {status === 'done' && (
          <>
            <CheckCircle2 size={36} color="#34d399" />
            <h2 style={s.title}>Email verified</h2>
            <p style={s.sub}>You're all set. Your account is fully activated.</p>
            <Link to="/dashboard" style={s.button}>Go to dashboard</Link>
          </>
        )}
        {status === 'failed' && (
          <>
            <AlertTriangle size={36} color="#f87171" />
            <h2 style={s.title}>Verification failed</h2>
            <p style={s.sub}>{message}</p>
            <Link to="/dashboard" style={s.button}>Back to Tally</Link>
          </>
        )}
      </div>
    </div>
  );
}
