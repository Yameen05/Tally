import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { TransactionRequest, BudgetRequest, PagedResponse, Transaction, AuthResponse } from '../types';

// The access token lives only in memory: XSS can't lift it from storage, and
// page reloads recover the session via the httpOnly refresh cookie.
let accessToken: string | null = null;

export const setAccessToken = (token: string | null) => {
  accessToken = token;
};

// Registered by AuthProvider so an unrecoverable 401 clears React auth state
// (which routes back to /login) instead of hard-reloading the page.
let onSessionExpired: (() => void) | null = null;
export const setOnSessionExpired = (handler: (() => void) | null) => {
  onSessionExpired = handler;
};

const api = axios.create({ baseURL: '/api' });

api.interceptors.request.use((config) => {
  if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`;
  return config;
});

// All concurrent 401s share one refresh call; the cookie rotates on every use,
// so parallel refreshes would revoke each other.
let refreshPromise: Promise<AuthResponse | null> | null = null;

export function refreshSession(): Promise<AuthResponse | null> {
  if (!refreshPromise) {
    refreshPromise = axios
      .post<AuthResponse>('/api/auth/refresh')
      .then((res) => {
        accessToken = res.data.token;
        return res.data;
      })
      .catch(() => null)
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

interface RetriableConfig extends InternalAxiosRequestConfig {
  _retry?: boolean;
}

api.interceptors.response.use(
  (res) => res,
  async (err: AxiosError) => {
    const original = err.config as RetriableConfig | undefined;
    const isAuthCall = original?.url?.startsWith('/auth');
    if (err.response?.status === 401 && original && !original._retry && !isAuthCall) {
      original._retry = true;
      const session = await refreshSession();
      if (session) {
        original.headers.Authorization = `Bearer ${session.token}`;
        return api(original);
      }
      onSessionExpired?.();
    }
    return Promise.reject(err);
  }
);

export const authApi = {
  register: (data: { name: string; email: string; password: string }) =>
    api.post<AuthResponse>('/auth/register', data),
  login: (data: { email: string; password: string }) =>
    api.post<AuthResponse>('/auth/login', data),
  logout: () => api.post('/auth/logout'),
};

export const transactionApi = {
  getAll: (page = 0, size = 20) =>
    api.get<PagedResponse<Transaction>>('/transactions', { params: { page, size } }),
  getByMonth: (year: number, month: number) =>
    api.get(`/transactions/month/${year}/${month}`),
  create: (data: TransactionRequest) => api.post('/transactions', data),
  update: (id: number, data: TransactionRequest) => api.put(`/transactions/${id}`, data),
  delete: (id: number) => api.delete(`/transactions/${id}`),
};

export const budgetApi = {
  getByMonth: (year: number, month: number) =>
    api.get(`/budgets/month/${year}/${month}`),
  getSummary: (year: number, month: number) =>
    api.get(`/budgets/summary/${year}/${month}`),
  createOrUpdate: (data: BudgetRequest) => api.post('/budgets', data),
  delete: (id: number) => api.delete(`/budgets/${id}`),
};

export const insightApi = {
  getInsights: (year: number, month: number) =>
    api.get(`/insights/${year}/${month}`),
};

export const analyticsApi = {
  recurring: () => api.get('/recurring'),
  netWorth: (days = 90) => api.get('/networth', { params: { days } }),
};

export const plaidApi = {
  status: () => api.get('/plaid/status'),
  createLinkToken: () => api.post('/plaid/link-token'),
  exchange: (publicToken: string, institutionId?: string, institutionName?: string) =>
    api.post('/plaid/exchange', { publicToken, institutionId, institutionName }),
  listItems: () => api.get('/plaid/items'),
  sync: () => api.post('/plaid/sync'),
  disconnect: (id: number) => api.delete(`/plaid/items/${id}`),
};

export default api;
