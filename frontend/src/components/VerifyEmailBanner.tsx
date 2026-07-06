import { useState } from 'react';
import { MailWarning } from 'lucide-react';
import { authApi } from '../services/api';
import { apiErrorMessage, useToast } from './Toast';
import { d } from '../pages/dashboard.styles';

/** Nag (gently) until the user clicks the link in their verification email. */
export default function VerifyEmailBanner() {
  const toast = useToast();
  const [sending, setSending] = useState(false);

  const resend = async () => {
    setSending(true);
    try {
      await authApi.resendVerification();
      toast.success('Verification email sent — check your inbox.');
    } catch (err) {
      toast.error(apiErrorMessage(err, 'Could not send the email. Try again in a minute.'));
    } finally {
      setSending(false);
    }
  };

  return (
    <div style={d.warnBanner}>
      <MailWarning size={16} color="#f59e0b" style={{ flexShrink: 0 }} />
      <span style={{ flex: 1 }}>
        Please verify your email address — we sent you a confirmation link.
      </span>
      <button
        onClick={resend}
        disabled={sending}
        style={{
          background: 'rgba(245,158,11,0.15)', border: '1px solid rgba(245,158,11,0.35)',
          color: '#fbbf24', borderRadius: 8, padding: '6px 14px', fontSize: 12,
          fontWeight: 600, cursor: 'pointer', whiteSpace: 'nowrap', opacity: sending ? 0.7 : 1,
        }}>
        {sending ? 'Sending…' : 'Resend email'}
      </button>
    </div>
  );
}
