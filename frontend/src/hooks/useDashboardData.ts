import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import api, { analyticsApi, budgetApi, plaidApi, transactionApi } from '../services/api';
import {
  MonthlySummary, Transaction, TransactionRequest, BudgetRequest,
  ConnectedItem, RecurringItem, NetWorthPoint,
} from '../types';
import { apiErrorMessage, useToast } from '../components/Toast';

export function useSummary(year: number, month: number) {
  return useQuery<MonthlySummary>({
    queryKey: ['summary', year, month],
    queryFn: () => budgetApi.getSummary(year, month).then(r => r.data),
  });
}

export function useMonthTransactions(year: number, month: number) {
  return useQuery<Transaction[]>({
    queryKey: ['transactions', year, month],
    queryFn: () => transactionApi.getByMonth(year, month).then(r => r.data),
  });
}

export function usePlaidStatus() {
  return useQuery<boolean | null>({
    queryKey: ['plaid-status'],
    queryFn: () => plaidApi.status().then(r =>
      typeof r.data?.configured === 'boolean' ? r.data.configured : null),
    staleTime: Infinity,
  });
}

export function usePlaidItems() {
  return useQuery<ConnectedItem[]>({
    queryKey: ['plaid-items'],
    queryFn: () => plaidApi.listItems().then(r => r.data),
  });
}

export function useRecurring() {
  return useQuery<RecurringItem[]>({
    queryKey: ['recurring'],
    queryFn: () => analyticsApi.recurring().then(r => r.data),
    staleTime: 5 * 60_000,
  });
}

export function useNetWorth(days = 90) {
  return useQuery<NetWorthPoint[]>({
    queryKey: ['networth', days],
    queryFn: () => analyticsApi.netWorth(days).then(r => r.data),
    staleTime: 5 * 60_000,
  });
}

/** Anything that changes transactions also changes the summary and budgets. */
function useInvalidateMonthData() {
  const queryClient = useQueryClient();
  return () => {
    queryClient.invalidateQueries({ queryKey: ['summary'] });
    queryClient.invalidateQueries({ queryKey: ['transactions'] });
    queryClient.invalidateQueries({ queryKey: ['recurring'] });
  };
}

export function useCreateTransaction() {
  const invalidate = useInvalidateMonthData();
  const queryClient = useQueryClient();
  const toast = useToast();
  return useMutation({
    mutationFn: (data: TransactionRequest) => transactionApi.create(data),
    onSuccess: (_res, variables) => {
      // Warn when this expense pushes its category budget over 80% or 100%.
      // Uses the cached summary from before the invalidation refetch lands.
      if (variables.type === 'EXPENSE') {
        const txDate = new Date(variables.date + 'T00:00:00');
        const summary = queryClient.getQueryData<MonthlySummary>(
          ['summary', txDate.getFullYear(), txDate.getMonth() + 1]);
        const budget = summary?.budgets.find(b => b.category === variables.category);
        if (budget && budget.limitAmount > 0) {
          const oldPct = (budget.spentAmount / budget.limitAmount) * 100;
          const newPct = ((budget.spentAmount + variables.amount) / budget.limitAmount) * 100;
          if (oldPct < 100 && newPct >= 100) {
            toast.warning(`You're now over your ${variables.category} budget (${newPct.toFixed(0)}% used).`);
          } else if (oldPct < 80 && newPct >= 80) {
            toast.warning(`Heads up: ${variables.category} budget is ${newPct.toFixed(0)}% used.`);
          }
        }
      }
      invalidate();
      toast.success('Transaction added');
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Could not add the transaction.')),
  });
}

export function useDeleteTransaction() {
  const invalidate = useInvalidateMonthData();
  const toast = useToast();
  return useMutation({
    mutationFn: (id: number) => transactionApi.delete(id),
    onSuccess: () => { invalidate(); toast.success('Transaction deleted'); },
    onError: (err) => toast.error(apiErrorMessage(err, 'Could not delete the transaction.')),
  });
}

interface ImportResult {
  imported: number;
  duplicates: number;
  failed: number;
  errors: { line: number; message: string }[];
}

export function useImportTransactions() {
  const invalidate = useInvalidateMonthData();
  const toast = useToast();
  return useMutation({
    mutationFn: (file: File): Promise<ImportResult> => {
      const form = new FormData();
      form.append('file', file);
      return api.post<ImportResult>('/transactions/import', form).then(r => r.data);
    },
    onSuccess: (result) => {
      invalidate();
      const parts = [`${result.imported} imported`];
      if (result.duplicates) parts.push(`${result.duplicates} duplicate${result.duplicates === 1 ? '' : 's'} skipped`);
      if (result.failed) parts.push(`${result.failed} failed`);
      const message = `Import finished: ${parts.join(', ')}`;
      if (result.failed > 0) {
        const firstError = result.errors[0];
        toast.error(`${message}. First error (line ${firstError?.line}): ${firstError?.message}`);
      } else {
        toast.success(message);
      }
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Import failed. Check the file format.')),
  });
}

export function useSaveBudget() {
  const invalidate = useInvalidateMonthData();
  const toast = useToast();
  return useMutation({
    mutationFn: (data: BudgetRequest) => budgetApi.createOrUpdate(data),
    onSuccess: () => { invalidate(); toast.success('Budget saved'); },
    onError: (err) => toast.error(apiErrorMessage(err, 'Could not save the budget.')),
  });
}

export function useDeleteBudget() {
  const invalidate = useInvalidateMonthData();
  const toast = useToast();
  return useMutation({
    mutationFn: (id: number) => budgetApi.delete(id),
    onSuccess: () => { invalidate(); toast.success('Budget removed'); },
    onError: (err) => toast.error(apiErrorMessage(err, 'Could not remove the budget.')),
  });
}

export function usePlaidSync() {
  const invalidate = useInvalidateMonthData();
  const queryClient = useQueryClient();
  const toast = useToast();
  return useMutation({
    mutationFn: () => plaidApi.sync(),
    onSuccess: () => {
      invalidate();
      queryClient.invalidateQueries({ queryKey: ['plaid-items'] });
      toast.success('Bank sync complete');
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Bank sync failed. Please try again.')),
  });
}

export function usePlaidDisconnect() {
  const queryClient = useQueryClient();
  const toast = useToast();
  return useMutation({
    mutationFn: (itemId: number) => plaidApi.disconnect(itemId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['plaid-items'] });
      toast.success('Bank disconnected');
    },
    onError: (err) => toast.error(apiErrorMessage(err, 'Could not disconnect the bank.')),
  });
}
