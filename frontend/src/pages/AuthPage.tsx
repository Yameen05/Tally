import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../services/api';
import { useAuth } from '../hooks/useAuth';
import { TrendingUp, Shield, Zap, PieChart, Eye, EyeOff } from 'lucide-react';

export default function AuthPage() {
  const [isLogin, setIsLogin] = useState(true);
  const [form, setForm] = useState({ name: '', email: '', password: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const res = isLogin
        ? await authApi.login({ email: form.email, password: form.password })
        : await authApi.register(form);
      login(res.data);
      navigate('/dashboard');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Something went wrong. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const features = [
    { icon: TrendingUp, title: 'Track Spending', desc: 'Categorize every expense automatically' },
    { icon: PieChart, title: 'Visual Reports', desc: 'Understand your money at a glance' },
    { icon: Zap, title: 'AI Insights', desc: 'Personalized advice powered by GPT-4' },
    { icon: Shield, title: 'Bank-grade Security', desc: 'Your data is always encrypted' },
  ];

  return (
    <div style={s.root}>
      {/* Left panel */}
      <div style={s.left}>
        <div style={s.leftInner}>
          <div style={s.brand}>
            <div style={s.brandIcon}>
              <TrendingUp size={22} color="#fff" />
            </div>
            <span style={s.brandName}>FinTrack</span>
          </div>

          <div style={s.heroText}>
            <h1 style={s.heroH1}>Your money,<br />under control.</h1>
            <p style={s.heroSub}>
              Join thousands managing budgets, tracking expenses, and reaching financial goals.
            </p>
          </div>

          <div style={s.features}>
            {features.map(({ icon: Icon, title, desc }) => (
              <div key={title} style={s.feature}>
                <div style={s.featureIcon}>
                  <Icon size={16} color="#a5b4fc" />
                </div>
                <div>
                  <div style={s.featureTitle}>{title}</div>
                  <div style={s.featureDesc}>{desc}</div>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Decorative orbs */}
        <div style={{ ...s.orb, top: '-80px', right: '-80px', width: 320, height: 320, background: 'radial-gradient(circle, rgba(99,102,241,0.35) 0%, transparent 70%)' }} />
        <div style={{ ...s.orb, bottom: '-60px', left: '-60px', width: 260, height: 260, background: 'radial-gradient(circle, rgba(139,92,246,0.25) 0%, transparent 70%)' }} />
      </div>

      {/* Right panel */}
      <div style={s.right}>
        <div style={s.card}>
          <div style={s.cardHeader}>
            <h2 style={s.cardTitle}>{isLogin ? 'Welcome back' : 'Create account'}</h2>
            <p style={s.cardSub}>
              {isLogin ? "Don't have an account? " : 'Already have one? '}
              <button style={s.switchBtn} onClick={() => { setIsLogin(!isLogin); setError(''); }}>
                {isLogin ? 'Sign up' : 'Sign in'}
              </button>
            </p>
          </div>

          <form onSubmit={handleSubmit} style={s.form}>
            {!isLogin && (
              <div style={s.field}>
                <label style={s.label}>Full Name</label>
                <input
                  style={s.input}
                  placeholder="Jane Doe"
                  value={form.name}
                  onChange={e => setForm({ ...form, name: e.target.value })}
                  required
                  onFocus={e => Object.assign(e.target.style, inputFocusStyle)}
                  onBlur={e => Object.assign(e.target.style, inputBlurStyle)}
                />
              </div>
            )}

            <div style={s.field}>
              <label style={s.label}>Email</label>
              <input
                style={s.input}
                type="email"
                placeholder="jane@example.com"
                value={form.email}
                onChange={e => setForm({ ...form, email: e.target.value })}
                required
                onFocus={e => Object.assign(e.target.style, inputFocusStyle)}
                onBlur={e => Object.assign(e.target.style, inputBlurStyle)}
              />
            </div>

            <div style={s.field}>
              <label style={s.label}>Password</label>
              <div style={s.passwordWrap}>
                <input
                  style={{ ...s.input, paddingRight: 44 }}
                  type={showPassword ? 'text' : 'password'}
                  placeholder="••••••••"
                  value={form.password}
                  onChange={e => setForm({ ...form, password: e.target.value })}
                  required
                  onFocus={e => Object.assign(e.target.style, inputFocusStyle)}
                  onBlur={e => Object.assign(e.target.style, inputBlurStyle)}
                />
                <button
                  type="button"
                  style={s.eyeBtn}
                  onClick={() => setShowPassword(v => !v)}
                >
                  {showPassword ? <EyeOff size={16} color="rgba(255,255,255,0.4)" /> : <Eye size={16} color="rgba(255,255,255,0.4)" />}
                </button>
              </div>
            </div>

            {error && (
              <div style={s.errorBox}>
                <span style={s.errorDot} />
                {error}
              </div>
            )}

            <button style={{ ...s.submitBtn, opacity: loading ? 0.7 : 1 }} type="submit" disabled={loading}>
              {loading ? (
                <span style={s.loadingRow}>
                  <span style={s.spinner} />
                  {isLogin ? 'Signing in…' : 'Creating account…'}
                </span>
              ) : (
                isLogin ? 'Sign in' : 'Create account'
              )}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}

const inputFocusStyle = { borderColor: 'rgba(99,102,241,0.7)', boxShadow: '0 0 0 3px rgba(99,102,241,0.15)' };
const inputBlurStyle = { borderColor: 'rgba(255,255,255,0.1)', boxShadow: 'none' };

const s: Record<string, React.CSSProperties> = {
  root: { display: 'flex', minHeight: '100vh', background: '#070714' },
  left: {
    flex: '0 0 480px', background: 'linear-gradient(145deg, #0f0c2e 0%, #1a1040 50%, #0d1a3a 100%)',
    display: 'flex', flexDirection: 'column', justifyContent: 'center',
    padding: '60px 56px', position: 'relative', overflow: 'hidden',
    borderRight: '1px solid rgba(255,255,255,0.06)',
  },
  leftInner: { position: 'relative', zIndex: 1 },
  brand: { display: 'flex', alignItems: 'center', gap: 12, marginBottom: 56 },
  brandIcon: {
    width: 40, height: 40, borderRadius: 10,
    background: 'linear-gradient(135deg, #6366f1, #8b5cf6)',
    display: 'flex', alignItems: 'center', justifyContent: 'center',
  },
  brandName: { fontSize: 20, fontWeight: 700, letterSpacing: '-0.3px', color: '#fff' },
  heroText: { marginBottom: 48 },
  heroH1: { fontSize: 42, fontWeight: 800, lineHeight: 1.15, letterSpacing: '-1.5px', color: '#fff', marginBottom: 16 },
  heroSub: { fontSize: 15, color: 'rgba(255,255,255,0.5)', lineHeight: 1.6, maxWidth: 320 },
  features: { display: 'flex', flexDirection: 'column', gap: 20 },
  feature: { display: 'flex', alignItems: 'flex-start', gap: 14 },
  featureIcon: {
    width: 32, height: 32, borderRadius: 8, flexShrink: 0,
    background: 'rgba(99,102,241,0.15)', border: '1px solid rgba(99,102,241,0.25)',
    display: 'flex', alignItems: 'center', justifyContent: 'center',
  },
  featureTitle: { fontSize: 14, fontWeight: 600, color: '#e2e8f0', marginBottom: 2 },
  featureDesc: { fontSize: 13, color: 'rgba(255,255,255,0.4)' },
  orb: { position: 'absolute', borderRadius: '50%', pointerEvents: 'none' },

  right: {
    flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center',
    padding: '40px 24px',
  },
  card: { width: '100%', maxWidth: 400 },
  cardHeader: { marginBottom: 32 },
  cardTitle: { fontSize: 26, fontWeight: 700, color: '#fff', letterSpacing: '-0.5px', marginBottom: 8 },
  cardSub: { fontSize: 14, color: 'rgba(255,255,255,0.4)' },
  switchBtn: {
    background: 'none', border: 'none', color: '#818cf8',
    cursor: 'pointer', fontSize: 14, fontWeight: 600, padding: 0,
    textDecoration: 'underline', textDecorationColor: 'rgba(129,140,248,0.4)',
  },
  form: { display: 'flex', flexDirection: 'column', gap: 20 },
  field: { display: 'flex', flexDirection: 'column', gap: 8 },
  label: { fontSize: 13, fontWeight: 500, color: 'rgba(255,255,255,0.6)', letterSpacing: '0.02em' },
  input: {
    width: '100%', padding: '12px 16px', borderRadius: 10,
    border: '1px solid rgba(255,255,255,0.1)',
    background: 'rgba(255,255,255,0.05)',
    color: '#fff', fontSize: 14, outline: 'none',
    transition: 'border-color 0.15s, box-shadow 0.15s',
  },
  passwordWrap: { position: 'relative' },
  eyeBtn: {
    position: 'absolute', right: 12, top: '50%', transform: 'translateY(-50%)',
    background: 'none', border: 'none', cursor: 'pointer', display: 'flex',
    alignItems: 'center', padding: 4,
  },
  errorBox: {
    display: 'flex', alignItems: 'center', gap: 8,
    padding: '10px 14px', borderRadius: 8,
    background: 'rgba(239,68,68,0.1)', border: '1px solid rgba(239,68,68,0.25)',
    color: '#fca5a5', fontSize: 13,
  },
  errorDot: { width: 6, height: 6, borderRadius: '50%', background: '#ef4444', flexShrink: 0 },
  submitBtn: {
    width: '100%', padding: '13px', borderRadius: 10, border: 'none',
    background: 'linear-gradient(135deg, #6366f1, #8b5cf6)',
    color: '#fff', fontWeight: 600, fontSize: 15, cursor: 'pointer',
    transition: 'opacity 0.15s, transform 0.1s',
    boxShadow: '0 4px 20px rgba(99,102,241,0.35)',
    marginTop: 4,
  },
  loadingRow: { display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8 },
  spinner: {
    width: 16, height: 16, borderRadius: '50%',
    border: '2px solid rgba(255,255,255,0.3)',
    borderTopColor: '#fff',
    animation: 'spin 0.6s linear infinite',
    display: 'inline-block',
  },
};
