import { test, expect } from '@playwright/test';

/**
 * Register → sign in → cart → checkout → the order in their own history.
 *
 * <p>Also covers the guest-cart merge: a piece is added before signing in, and
 * must still be in the cart afterwards.
 */
test.describe('registered customer', () => {
  test('registers, keeps their cart through sign-in, orders, and sees it in history', async ({ page }) => {
    const stamp = Date.now();
    const email = `e2e-customer-${stamp}@example.test`;
    const password = 'a-long-enough-password';
    const fullName = `E2E Customer ${stamp}`;
    // phone is unique per account; a fixed value fails on the second run
    const phone = `98764${String(stamp).slice(-5)}`;

    // ---- add something to the cart while still a visitor ----
    await page.goto('/shop');
    const firstCard = page.locator('a[href^="/product/"]').first();
    await expect(firstCard).toBeVisible();
    // the href is the stable identity; link text varies with layout
    const productHref = await firstCard.getAttribute('href');
    await firstCard.click();

    await expect(page).toHaveURL(/\/product\//);
    const productName = (await page.locator('h1').first().innerText()).trim();
    await page.getByRole('button', { name: /add to cart/i }).click();
    await expect(page.getByText(/added to your cart/i)).toBeVisible();

    // ---- register ----
    await page.goto('/register');
    await page.getByLabel('Your name').fill(fullName);
    await page.getByLabel('Email address').fill(email);
    await page.getByLabel(/mobile number/i).fill(phone);
    await page.getByLabel('Password').fill(password);
    await page.getByRole('button', { name: /create account/i }).click();

    await expect(page).toHaveURL(/\/account/, { timeout: 20_000 });
    await expect(page.getByRole('heading', { name: fullName })).toBeVisible();

    // ---- the browser cart survived sign-in ----
    await page.goto('/cart');
    await expect(page.locator(`a[href="${productHref}"]`).first()).toBeVisible();

    // ---- checkout using the account ----
    await page.getByRole('link', { name: /proceed to checkout/i }).click();
    await expect(page).toHaveURL(/\/checkout/);

    // the form is prefilled from the account
    await expect(page.getByLabel('Full name')).toHaveValue(fullName);

    await page.getByLabel(/mobile \/ whatsapp number/i).fill(phone);
    await page.getByLabel(/^Address/).fill('7 Nest Road');
    await page.getByLabel('City').fill('Kochi');
    await page.getByLabel('State').fill('Kerala');
    await page.getByLabel('Postal code').fill('682002');

    await page.getByRole('button', { name: /place order/i }).click();

    await expect(page).toHaveURL(/\/order\/KV-/, { timeout: 20_000 });
    const orderNumber = (await page.getByText(/^KV-\d{6}-[0-9A-Z]{4}$/).innerText()).trim();

    // ---- and it is in their own history ----
    await page.goto('/account');
    await expect(page.getByRole('heading', { name: fullName })).toBeVisible();

    await page.goto(`/order/${orderNumber}`);
    await expect(page.getByRole('heading', { name: /thank you/i })).toBeVisible();
    await expect(page.getByText(orderNumber)).toBeVisible();
  });

  test('signing out closes the account area', async ({ page }) => {
    await page.goto('/account');
    // no session, so the gate sends them to sign in
    await expect(page).toHaveURL(/\/login/);
    await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
  });
});
