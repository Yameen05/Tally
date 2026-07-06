import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { KeyRound, CheckCircle2 } from 'lucide-react';
import { authApi } from '../services/api';
import { apiErrorMessage } from '../components/Toast';
import { tokenPageStyles as s } from './tokenPage.styles';

/** Landing page for the link in the reset email (/reset-password?token=…). */
export default function ResetPasswordPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const token = params.get('token') ?? '';
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [done, setDone] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    if (password !== confirm) {
      setError('Passwords do not match.');
      return;
    }
    setLoading(true);
    try {
      await authApi.resetPassword(token, password);
      setDone(true);
      setTimeout(() => navigate('/login'), 2500);
    } catch (err) {
      setError(apiErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  if (!token) {
    return (
      <div style={s.root}>
        <div style={s.card}>
          <h2 style={s.title}>Missing reset token</h2>
          <p style={s.sub}>Use the link from your reset email, or request a new one from the sign-in page.</p>
          <Link to="/login" style={s.button}>Back to sign in</Link>
        </div>
      </div>
    );
  }

  return (
    <div style={s.root}>
      <div style={s.card}>
        {done ? (
          <>
            <CheckCircle2 size={36} color="#34d399" />
            <h2 style={s.title}>Password updated</h2>
            <p style={s.sub}>All existing sessions were signed out. Taking you to sign in…</p>
          </>
        ) : (
          <>
            <KeyRound size={32} color="#818cf8" />
            <h2 style={s.title}>Choose a new password</h2>
            <p style={s.sub}>At least 8 characters, with a letter and a number.</p>
            <form onSubmit={handleSubmit} style={s.form}>
              <input style={s.input} type="password" placeholder="New password" required
                value={password} onChange={e => setPassword(e.target.value)} />
              <input style={s.input} type="password" placeholder="Confirm new password" required
                value={confirm} onChange={e => setConfirm(e.target.value)} />
              {error && <div style={s.error}>{error}</div>}
              <button type="submit" style={{ ...s.button, opacity: loading ? 0.7 : 1 }} disabled={loading}>
                {loading ? 'Saving…' : 'Reset password'}
              </button>
            </form>
          </>
        )}
      </div>
    </div>
  );
}
