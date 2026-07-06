import { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { User, AuthResponse } from '../types';
import { setAccessToken, setOnSessionExpired, refreshSession, authApi } from '../services/api';

interface AuthContextType {
  user: User | null;
  login: (session: AuthResponse) => void;
  logout: () => void;
  isAuthenticated: boolean;
  /** True while the silent refresh on page load is still in flight. */
  initializing: boolean;
}

const AuthContext = createContext<AuthContextType | null>(null);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [user, setUser] = useState<User | null>(null);
  const [initializing, setInitializing] = useState(true);

  // Nothing sensitive persists in the browser; the httpOnly refresh cookie
  // silently restores the session after a reload.
  useEffect(() => {
    let cancelled = false;
    refreshSession().then((session) => {
      if (cancelled) return;
      if (session) {
        setUser({
          userId: session.userId, name: session.name,
          email: session.email, emailVerified: session.emailVerified,
        });
      }
      setInitializing(false);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    setOnSessionExpired(() => {
      setAccessToken(null);
      setUser(null);
    });
    return () => setOnSessionExpired(null);
  }, []);

  const login = (session: AuthResponse) => {
    setAccessToken(session.token);
    setUser({
      userId: session.userId, name: session.name,
      email: session.email, emailVerified: session.emailVerified,
    });
  };

  const logout = () => {
    authApi.logout().catch(() => {
      /* cookie may already be gone; local state is cleared regardless */
    });
    setAccessToken(null);
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, login, logout, isAuthenticated: !!user, initializing }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
};
