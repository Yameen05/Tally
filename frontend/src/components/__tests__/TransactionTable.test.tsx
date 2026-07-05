import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import TransactionTable from '../TransactionTable';
import { ToastProvider } from '../Toast';
import { Transaction } from '../../types';

const transactions: Transaction[] = [
  { id: 1, description: 'Coffee beans', amount: 18.5, type: 'EXPENSE', category: 'Food', date: '2026-07-01', createdAt: '' },
  { id: 2, description: 'Salary', amount: 3000, type: 'INCOME', category: 'Salary', date: '2026-07-02', createdAt: '' },
  { id: 3, description: 'Bus pass', amount: 60, type: 'EXPENSE', category: 'Transport', date: '2026-07-03', createdAt: '' },
];

function renderTable(txs: Transaction[] = transactions) {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ToastProvider>
        <TransactionTable transactions={txs} loading={false} deletingId={null} onDelete={() => {}} />
      </ToastProvider>
    </QueryClientProvider>
  );
}

describe('TransactionTable filters', () => {
  it('shows all transactions by default', () => {
    renderTable();
    expect(screen.getByText('Coffee beans')).toBeInTheDocument();
    // 'Salary' appears as description, category badge, and filter option
    expect(screen.getAllByText('Salary').length).toBeGreaterThan(0);
    expect(screen.getByText('Bus pass')).toBeInTheDocument();
  });

  it('filters by search text', async () => {
    renderTable();
    await userEvent.type(screen.getByPlaceholderText('Search transactions…'), 'coffee');
    expect(screen.getByText('Coffee beans')).toBeInTheDocument();
    expect(screen.queryByText('Bus pass')).not.toBeInTheDocument();
    expect(screen.getByText('1 of 3')).toBeInTheDocument();
  });

  it('filters by type', async () => {
    renderTable();
    await userEvent.selectOptions(screen.getByDisplayValue('All types'), 'INCOME');
    expect(screen.getAllByText('Salary').length).toBeGreaterThan(0);
    expect(screen.queryByText('Coffee beans')).not.toBeInTheDocument();
    expect(screen.queryByText('Bus pass')).not.toBeInTheDocument();
  });

  it('shows an empty state when nothing matches', async () => {
    renderTable();
    await userEvent.type(screen.getByPlaceholderText('Search transactions…'), 'zzzzz');
    expect(screen.getByText('No transactions match your filters')).toBeInTheDocument();
  });

  it('offers CSV import even when the month is empty', () => {
    renderTable([]);
    expect(screen.getByRole('button', { name: /import csv/i })).toBeInTheDocument();
  });
});
