import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { AuthProvider, useAuth } from '../useAuth';

const mocks = vi.hoisted(() => ({
  refreshSession: vi.fn(),
  setAccessToken: vi.fn(),
  setOnSessionExpired: vi.fn(),
  logout: vi.fn().mockResolvedValue(undefined),
}));

vi.mock('../../services/api', () => ({
  refreshSession: mocks.refreshSession,
  setAccessToken: mocks.setAccessToken,
  setOnSessionExpired: mocks.setOnSessionExpired,
  authApi: { logout: mocks.logout },
}));

function Probe() {
  const { user, isAuthenticated, initializing } = useAuth();
  if (initializing) return <span>initializing</span>;
  return <span>{isAuthenticated ? `hello ${user?.name}` : 'anonymous'}</span>;
}

describe('AuthProvider silent refresh', () => {
  beforeEach(() => vi.clearAllMocks());

  it('restores the session when the refresh cookie is valid', async () => {
    mocks.refreshSession.mockResolvedValue({ token: 't', name: 'Yameen', email: 'y@e.com', userId: 1 });

    render(<AuthProvider><Probe /></AuthProvider>);

    expect(screen.getByText('initializing')).toBeInTheDocument();
    expect(await screen.findByText('hello Yameen')).toBeInTheDocument();
  });

  it('stays anonymous when the refresh fails', async () => {
    mocks.refreshSession.mockResolvedValue(null);

    render(<AuthProvider><Probe /></AuthProvider>);

    expect(await screen.findByText('anonymous')).toBeInTheDocument();
  });

  it('registers a session-expiry handler that clears auth state', async () => {
    mocks.refreshSession.mockResolvedValue({ token: 't', name: 'Yameen', email: 'y@e.com', userId: 1 });

    render(<AuthProvider><Probe /></AuthProvider>);
    await screen.findByText('hello Yameen');

    expect(mocks.setOnSessionExpired).toHaveBeenCalledWith(expect.any(Function));
  });
});
