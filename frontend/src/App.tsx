import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './hooks/useAuth';
import AuthPage from './pages/AuthPage';
import Dashboard from './pages/Dashboard';
import ErrorBoundary from './components/ErrorBoundary';

function PrivateRoute({ children }: { children: JSX.Element }) {
  const { isAuthenticated } = useAuth();
  return isAuthenticated ? children : <Navigate to="/login" replace />;
}

function AppRoutes() {
  const { isAuthenticated } = useAuth();
  return (
    <Routes>
      <Route path="/login" element={isAuthenticated ? <Navigate to="/dashboard" /> : <AuthPage />} />
      <Route path="/dashboard" element={<PrivateRoute><Dashboard /></PrivateRoute>} />
      <Route path="*" element={<Navigate to={isAuthenticated ? '/dashboard' : '/login'} />} />
    </Routes>
  );
}

export default function App() {
  return (
    <ErrorBoundary>
      <AuthProvider>
        <BrowserRouter>
          <AppRoutes />
        </BrowserRouter>
      </AuthProvider>
    </ErrorBoundary>
  );
}
