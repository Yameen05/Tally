import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { analyticsApi, budgetApi, plaidApi, transactionApi } from '../services/api';
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
  const toast = useToast();
  return useMutation({
    mutationFn: (data: TransactionRequest) => transactionApi.create(data),
    onSuccess: () => { invalidate(); toast.success('Transaction added'); },
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
