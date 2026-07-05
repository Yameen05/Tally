import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import TransactionModal from '../TransactionModal';

describe('TransactionModal', () => {
  it('submits the parsed amount and form fields', async () => {
    const onSubmit = vi.fn();
    render(<TransactionModal onClose={() => {}} onSubmit={onSubmit} submitting={false} />);

    await userEvent.type(screen.getByPlaceholderText('e.g. Grocery run'), 'Coffee');
    await userEvent.type(screen.getByPlaceholderText('0.00'), '4.50');
    await userEvent.click(screen.getByRole('button', { name: /add transaction/i }));

    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({
      description: 'Coffee',
      amount: 4.5,
      type: 'EXPENSE',
    }));
  });

  it('does not submit a zero or missing amount', async () => {
    const onSubmit = vi.fn();
    render(<TransactionModal onClose={() => {}} onSubmit={onSubmit} submitting={false} />);

    await userEvent.type(screen.getByPlaceholderText('e.g. Grocery run'), 'Coffee');
    await userEvent.click(screen.getByRole('button', { name: /add transaction/i }));

    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('closes on the cancel button', async () => {
    const onClose = vi.fn();
    render(<TransactionModal onClose={onClose} onSubmit={() => {}} submitting={false} />);

    await userEvent.click(screen.getByRole('button', { name: /cancel/i }));
    expect(onClose).toHaveBeenCalled();
  });
});
