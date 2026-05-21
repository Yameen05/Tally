import { Component, ReactNode } from 'react';

interface Props {
  children: ReactNode;
}

interface State {
  error: Error | null;
}

export default class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  render() {
    if (this.state.error) {
      return (
        <div style={{
          display: 'flex', flexDirection: 'column', alignItems: 'center',
          justifyContent: 'center', height: '100vh',
          background: '#0f0f23', color: '#f1f5f9',
          fontFamily: 'Inter, sans-serif', gap: 12,
        }}>
          <p style={{ fontSize: 20, fontWeight: 700 }}>Something went wrong</p>
          <p style={{ color: 'rgba(255,255,255,0.45)', fontSize: 14 }}>
            Please refresh the page to try again.
          </p>
          <button
            onClick={() => window.location.reload()}
            style={{
              marginTop: 8, background: '#6366f1', color: '#fff',
              border: 'none', borderRadius: 8, padding: '10px 22px',
              cursor: 'pointer', fontSize: 14, fontWeight: 600,
            }}
          >
            Refresh
          </button>
        </div>
      );
    }
    return this.props.children;
  }
}
