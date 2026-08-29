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

/** Submits the sign-in form and reports whether the account was accepted. */
async function attemptSignIn(page: Page, password: string): Promise<boolean> {
  await page.goto('/admin/login');
  await page.getByLabel('Email address').fill(ADMIN_EMAIL);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();

  // Wait for the outcome rather than reading the URL straight after the click.
  // The click resolves when the event is dispatched, not when the new URL is in
  // place, so an immediate read still sees /admin/login and every branch taken
  // from it is a coin toss. Rejected credentials leave the page where it is,
  // which is why this resolves false on timeout instead of throwing.
  return page
    .waitForURL(/\/admin(\/change-password)?(\?|$)/, { timeout: 10_000 })
    .then(() => true)
    .catch(() => false);
}

/**
 * Signs in to the back office, replacing the bootstrap password if the account
 * is still carrying it.
 *
 * <p>On a fresh deployment - which is exactly what CI builds - the first
 * administrator is created with mustChangePassword set, so signing in lands on
 * the change screen rather than the dashboard.
 *
 * <p>Which means this helper rotates the password the first time it runs, and
 * the bootstrap one stops working from then on. CI never noticed because every
 * run gets an empty database; locally it broke every run after the first. So
 * both are tried, and the run is not required to know which state it inherited.
 */
async function signInAsAdmin(page: Page) {
  let current = ADMIN_PASSWORD;
  let signedIn = await attemptSignIn(page, current);

  if (!signedIn) {
    // a previous run against this same database already rotated it
    current = REPLACEMENT_PASSWORD;
    signedIn = await attemptSignIn(page, current);
  }

  expect(
    signedIn,
    `neither the bootstrap password nor the replacement was accepted for ${ADMIN_EMAIL}. ` +
      'Set E2E_ADMIN_PASSWORD to whatever this environment actually uses.',
  ).toBe(true);

  // Wait for a screen, not for a URL. Sign-in lands on /admin and the guard
  // then redirects to /admin/change-password when one is owed, so the URL is
  // briefly the dashboard's on the way to the change screen. Branching on a URL
  // snapshot catches that intermediate value and skips the change it was meant
  // to detect. Whichever heading appears settles it, with no fixed wait on
  // either path.
  await expect(
    page.getByRole('heading', { name: /choose a new password|dashboard/i }),
  ).toBeVisible();

  if (/\/admin\/change-password/.test(page.url())) {
    // Whichever of the two got us in is the one the account holds, and the new
    // one is the other. Assuming the bootstrap password here would fail on any
    // account that owes a change while already carrying the replacement.
    const next = current === ADMIN_PASSWORD ? REPLACEMENT_PASSWORD : ADMIN_PASSWORD;

    await page.getByLabel('Current password').fill(current);
    await page.getByLabel('New password', { exact: false }).first().fill(next);
    await page.getByLabel('Confirm new password').fill(next);
    await page.getByRole('button', { name: /save and sign in again/i }).click();

    await expect(page).toHaveURL(/\/admin\/login/);
    expect(await attemptSignIn(page, next)).toBe(true);
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
