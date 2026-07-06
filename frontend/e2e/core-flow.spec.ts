import { test, expect, Page } from '@playwright/test';

/**
 * The critical path: register → add transaction → set budget → verify summary
 * → logout → login → data persisted. Each run registers a fresh user so the
 * suite is rerunnable against a dirty database.
 */
const email = `e2e-${Date.now()}@example.com`;
const password = 'e2epass123';

async function addTransaction(page: Page, description: string, amount: string, type: 'EXPENSE' | 'INCOME') {
  await page.getByRole('button', { name: 'Add Transaction' }).first().click();
  const modal = page.locator('form');
  await modal.getByPlaceholder('e.g. Grocery run').fill(description);
  await modal.getByPlaceholder('0.00').fill(amount);
  await modal.locator('select').first().selectOption(type);
  await modal.getByRole('button', { name: 'Add Transaction' }).click();
  await expect(page.getByText('Transaction added')).toBeVisible();
}

test.describe.serial('core flow', () => {
  test('register lands on the dashboard', async ({ page }) => {
    await page.goto('/login');
    await page.getByRole('button', { name: 'Sign up' }).click();
    await page.getByPlaceholder('Jane Doe').fill('E2E Tester');
    await page.getByPlaceholder('jane@example.com').fill(email);
    await page.getByPlaceholder('••••••••').fill(password);
    await page.getByRole('button', { name: 'Create account' }).click();

    await expect(page).toHaveURL(/\/dashboard/);
    await expect(page.getByText('E2E Tester')).toBeVisible();
    // New accounts start unverified
    await expect(page.getByText('Please verify your email address')).toBeVisible();
  });

  test('transactions update the summary', async ({ page }) => {
    await login(page);
    await addTransaction(page, 'E2E Salary', '2000', 'INCOME');
    await addTransaction(page, 'E2E Groceries', '150.25', 'EXPENSE');

    await expect(page.getByText('$2,000.00').first()).toBeVisible();
    await expect(page.getByText('$150.25').first()).toBeVisible();
  });

  test('budgets track spending', async ({ page }) => {
    await login(page);
    await page.getByRole('button', { name: 'Budgets' }).click();
    await page.getByRole('button', { name: 'Set Budget' }).click();
    const modal = page.locator('form');
    await modal.locator('select').selectOption('Food');
    await modal.getByPlaceholder('0.00').fill('500');
    await modal.getByRole('button', { name: 'Save Budget' }).click();
    await expect(page.getByText('Budget saved')).toBeVisible();

    // The earlier Food expense counts against the new budget
    await expect(page.getByText('$150.25').first()).toBeVisible();
    await expect(page.getByText('30%').first()).toBeVisible();
  });

  test('search filters the transaction table', async ({ page }) => {
    await login(page);
    await page.getByRole('button', { name: 'Transactions' }).click();
    await page.getByPlaceholder('Search transactions…').fill('salary');
    await expect(page.getByText('E2E Salary')).toBeVisible();
    await expect(page.getByText('E2E Groceries')).not.toBeVisible();
  });

  test('logout ends the session and login restores it', async ({ page }) => {
    await login(page);
    await page.getByTitle('Sign out').click();
    await expect(page).toHaveURL(/\/login/);

    // Reload proves the refresh cookie is gone, not just React state
    await page.reload();
    await expect(page).toHaveURL(/\/login/);

    await login(page);
    await expect(page.getByText('E2E Tester')).toBeVisible();
    await expect(page.getByText('$2,000.00').first()).toBeVisible();
  });
});

async function login(page: Page) {
  await page.goto('/login');
  // A live refresh cookie skips the form entirely
  if (page.url().includes('/dashboard')) return;
  try {
    await page.getByPlaceholder('jane@example.com').fill(email, { timeout: 3000 });
  } catch {
    await expect(page).toHaveURL(/\/dashboard/);
    return;
  }
  await page.getByPlaceholder('••••••••').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL(/\/dashboard/);
}
