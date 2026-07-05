import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import { ToastProvider, useToast, apiErrorMessage } from '../Toast';

function Trigger({ kind, message }: { kind: 'success' | 'error' | 'warning'; message: string }) {
  const toast = useToast();
  return <button onClick={() => toast[kind](message)}>fire</button>;
}

describe('apiErrorMessage', () => {
  it('prefers field errors over the generic message', () => {
    const err = { response: { data: { message: 'Validation failed', fieldErrors: { password: 'Too short' } } } };
    expect(apiErrorMessage(err)).toBe('Too short');
  });

  it('falls back to the message field', () => {
    const err = { response: { data: { message: 'Email already registered' } } };
    expect(apiErrorMessage(err)).toBe('Email already registered');
  });

  it('uses the fallback for network errors', () => {
    expect(apiErrorMessage(new Error('Network Error'), 'Oops')).toBe('Oops');
  });
});

describe('ToastProvider', () => {
  afterEach(() => vi.useRealTimers());

  it('shows a toast and auto-dismisses it', async () => {
    vi.useFakeTimers();
    render(
      <ToastProvider>
        <Trigger kind="success" message="Saved!" />
      </ToastProvider>
    );

    act(() => { screen.getByText('fire').click(); });
    expect(screen.getByText('Saved!')).toBeInTheDocument();

    act(() => { vi.advanceTimersByTime(5000); });
    expect(screen.queryByText('Saved!')).not.toBeInTheDocument();
  });

  it('renders warning toasts', () => {
    vi.useFakeTimers();
    render(
      <ToastProvider>
        <Trigger kind="warning" message="Budget almost used" />
      </ToastProvider>
    );
    act(() => { screen.getByText('fire').click(); });
    expect(screen.getByText('Budget almost used')).toBeInTheDocument();
  });
});
