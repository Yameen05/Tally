export type TransactionType = 'INCOME' | 'EXPENSE';

export interface Transaction {
  id: number;
  description: string;
  amount: number;
  type: TransactionType;
  category: string;
  date: string;
  notes?: string;
  createdAt: string;
  merchantName?: string;
  pending?: boolean;
  plaidTransactionId?: string;
  plaidAccountId?: string;
}

export interface Budget {
  id: number;
  category: string;
  limitAmount: number;
  spentAmount: number;
  remaining: number;
  percentageUsed: number;
  month: number;
  year: number;
}

export interface MonthlySummary {
  month: number;
  year: number;
  totalIncome: number;
  totalExpenses: number;
  netBalance: number;
  expensesByCategory: Record<string, number>;
  budgets: Budget[];
}

export interface User {
  userId: number;
  name: string;
  email: string;
}

/** Response from login/register/refresh; token is the short-lived access token. */
export interface AuthResponse {
  token: string;
  name: string;
  email: string;
  userId: number;
}

export type Cadence = 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY';

export interface RecurringItem {
  name: string;
  category: string;
  cadence: Cadence;
  lastAmount: number;
  averageAmount: number;
  monthlyEstimate: number;
  lastDate: string;
  nextDueDate: string;
  occurrences: number;
  priceChangePct: number | null;
}

export interface NetWorthPoint {
  date: string;
  assets: number;
  liabilities: number;
  netWorth: number;
}

export interface ConnectedAccount {
  id: number;
  accountId: string;
  name: string;
  mask: string | null;
  type: string | null;
  subtype: string | null;
  currentBalance: number | null;
  availableBalance: number | null;
  currencyCode: string | null;
}

export interface ConnectedItem {
  id: number;
  institutionName: string | null;
  lastSyncedAt: string | null;
  syncError: string | null;
  accounts: ConnectedAccount[];
}

export interface PagedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

export interface TransactionRequest {
  description: string;
  amount: number;
  type: TransactionType;
  category: string;
  date: string;
  notes?: string;
}

export interface BudgetRequest {
  category: string;
  limitAmount: number;
  month: number;
  year: number;
}

export const CATEGORIES = [
  'Food', 'Housing', 'Transport', 'Entertainment',
  'Healthcare', 'Shopping', 'Utilities', 'Education',
  'Savings', 'Salary', 'Freelance', 'Other'
];
