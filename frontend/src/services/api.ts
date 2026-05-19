import axios from 'axios';

const api = axios.create({ baseURL: '/api' });

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem('user');
      localStorage.removeItem('token');
      window.location.href = '/login';
    }
    return Promise.reject(err);
  }
);

export const authApi = {
  register: (data: { name: string; email: string; password: string }) =>
    api.post('/auth/register', data),
  login: (data: { email: string; password: string }) =>
    api.post('/auth/login', data),
};

export const transactionApi = {
  getAll: () => api.get('/transactions'),
  getByMonth: (year: number, month: number) =>
    api.get(`/transactions/month/${year}/${month}`),
  create: (data: any) => api.post('/transactions', data),
  update: (id: number, data: any) => api.put(`/transactions/${id}`, data),
  delete: (id: number) => api.delete(`/transactions/${id}`),
};

export const budgetApi = {
  getByMonth: (year: number, month: number) =>
    api.get(`/budgets/month/${year}/${month}`),
  getSummary: (year: number, month: number) =>
    api.get(`/budgets/summary/${year}/${month}`),
  createOrUpdate: (data: any) => api.post('/budgets', data),
  delete: (id: number) => api.delete(`/budgets/${id}`),
};

export const insightApi = {
  getInsights: (year: number, month: number) =>
    api.get(`/insights/${year}/${month}`),
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
