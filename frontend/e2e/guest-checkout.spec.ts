import { test, expect, type Page } from '@playwright/test';

/**
 * Browse → cart → checkout → the order appearing in the back office.
 *
 * <p>The whole point of an end-to-end test is that nothing is stubbed, so this
 * drives the real storefront against the real API and then signs in as an
 * administrator to confirm the order genuinely landed.
 */

const ADMIN_EMAIL = process.env.E2E_ADMIN_EMAIL ?? 'admin@karvya.local';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? 'Karvya_admin_2026';
/** Used only when the account still owes a password change. */
const REPLACEMENT_PASSWORD = process.env.E2E_ADMIN_NEW_PASSWORD ?? 'E2e_replacement_2026';

/**
 * Signs in to the back office, replacing the bootstrap password if the account
 * is still carrying it.
 *
 * <p>On a fresh deployment - which is exactly what CI builds - the first
 * administrator is created with mustChangePassword set, so signing in lands on
 * the change screen rather than the dashboard. A helper that assumed otherwise
 * would pass locally and fail on every clean environment.
 */
async function signInAsAdmin(page: Page) {
  await page.goto('/admin/login');
  await page.getByLabel('Email address').fill(ADMIN_EMAIL);
  await page.getByLabel('Password').fill(ADMIN_PASSWORD);
  await page.getByRole('button', { name: 'Sign in' }).click();

  if (/\/admin\/change-password/.test(page.url())) {
    await page.getByLabel('Current password').fill(ADMIN_PASSWORD);
    await page.getByLabel('New password', { exact: false }).first().fill(REPLACEMENT_PASSWORD);
    await page.getByLabel('Confirm new password').fill(REPLACEMENT_PASSWORD);
    await page.getByRole('button', { name: /save and sign in again/i }).click();

    await expect(page).toHaveURL(/\/admin\/login/);
    await page.getByLabel('Email address').fill(ADMIN_EMAIL);
    await page.getByLabel('Password').fill(REPLACEMENT_PASSWORD);
    await page.getByRole('button', { name: 'Sign in' }).click();
  }

  await expect(page).toHaveURL(/\/admin(\?|$)/);
}

test.describe('guest checkout', () => {
  test('a visitor can browse, add to cart, order, and the team sees it', async ({ page }) => {
    // ---- browse ----
    await page.goto('/');
    await expect(page.getByRole('heading', { name: /handwoven coir bird houses/i })).toBeVisible();

    await page.getByRole('link', { name: 'Shop', exact: true }).first().click();
    await expect(page).toHaveURL(/\/shop/);

    // the seeded catalogue should be on screen
    // href-based: a role/heading filter also matches the header and footer nav
    const firstCard = page.locator('a[href^="/product/"]').first();
    await expect(firstCard).toBeVisible();
    // the href is the stable identity; link text varies with layout
    const productHref = await firstCard.getAttribute('href');
    await firstCard.click();

    // ---- product detail ----
    await expect(page).toHaveURL(/\/product\//);
    const productName = (await page.locator('h1').first().innerText()).trim();
    expect(productName.length).toBeGreaterThan(0);

    await page.getByRole('button', { name: /add to cart/i }).click();
    await expect(page.getByText(/added to your cart/i)).toBeVisible();

    // ---- cart ----
    await page.goto('/cart');
    await expect(page.getByRole('heading', { name: 'Your cart' })).toBeVisible();
    await expect(page.locator(`a[href="${productHref}"]`).first()).toBeVisible();

    // the total must come from the server, so simply assert one is rendered
    await expect(page.getByText('Total', { exact: true }).first()).toBeVisible();

    await page.getByRole('link', { name: /proceed to checkout/i }).click();

    // ---- checkout ----
    await expect(page).toHaveURL(/\/checkout/);
    await expect(
      page.getByText(/your order will be confirmed by our team/i),
    ).toBeVisible();

    const stamp = Date.now();
    const buyerName = `E2E Guest ${stamp}`;

    await page.getByLabel('Full name').fill(buyerName);
    await page.getByLabel(/mobile \/ whatsapp number/i).fill(`98765${String(Date.now()).slice(-5)}`);
    await page.getByLabel(/email \(optional\)/i).fill(`e2e-guest-${stamp}@example.test`);
    await page.getByLabel(/^Address/).fill('42 Coir Lane');
    await page.getByLabel('City').fill('Kochi');
    await page.getByLabel('State').fill('Kerala');
    await page.getByLabel('Postal code').fill('682001');

    await page.getByRole('button', { name: /place order/i }).click();

    // ---- confirmation ----
    await expect(page).toHaveURL(/\/order\/KV-/, { timeout: 20_000 });
    await expect(page.getByRole('heading', { name: /thank you/i })).toBeVisible();

    const orderNumber = (await page.getByText(/^KV-\d{6}-[0-9A-Z]{4}$/).innerText()).trim();
    expect(orderNumber).toMatch(/^KV-\d{6}-[0-9A-Z]{4}$/);

    // the cart is emptied only after the order is confirmed to exist
    await page.goto('/cart');
    await expect(page.getByText(/your cart is empty/i)).toBeVisible();

    // ---- the team sees it ----
    await signInAsAdmin(page);
    await page.goto(`/admin/orders/${orderNumber}`);

    await expect(page.getByRole('heading', { name: orderNumber })).toBeVisible();
    await expect(page.getByText(buyerName)).toBeVisible();
    await expect(page.getByText('New', { exact: true }).first()).toBeVisible();
  });

  test('the confirmation cannot be opened without its token', async ({ page }) => {
    // an order number appears in emails and gets read aloud; it is not a key
    await page.goto('/order/KV-260101-AAAA');
    await expect(page.getByRole('heading', { name: /could not find that page/i })).toBeVisible();
  });
});
