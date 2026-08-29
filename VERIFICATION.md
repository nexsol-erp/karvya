# Verification report

What was run, what it produced, and what is deliberately still unfinished.
Everything below was executed on this machine; nothing is asserted from memory.

Date: 2026-08-29 · Java 21.0.8 · Node 20.17.0 · Docker 28.3.2 · PostgreSQL 17.5

---

## Commands run

| Command | Result |
|---|---|
| `cd backend && mvn -B test` | **111 tests, 0 failures, 0 errors** · BUILD SUCCESS |
| `cd frontend && npm run build` | **exit 0** · `tsc -b` clean, 22 chunks emitted |
| `cd frontend && npm test` | **11 tests, 2 files, all passing** |
| `npx playwright test` (dev server) | **4 passed** |
| `E2E_BASE_URL=http://localhost npx playwright test` | **4 passed against the Docker stack** |
| `docker compose build` | **exit 0** · `karvya-backend` 553 MB, `karvya-web` 74.7 MB |
| `docker compose up -d` | 4 services, **all healthy** |

Code: 117 Java files, 56 TypeScript files, 3 Flyway migrations.

---

## What the tests actually assert

Not that endpoints respond — that the properties hold.

**Overselling.** Ten buyers race three units concurrently; exactly three orders
exist and stock lands on zero. The lock was removed and the test re-run to
confirm it detects the difference: it does, and the result was instructive
rather than obvious — stock is still never oversold, because the `@Version`
column fails the losing transactions, but only *two* of the three units sold.
The row lock exists for throughput, not safety of last resort. Both facts are
recorded in the README.

**Restocking exactly once.** Six simultaneous cancellations of one order:
exactly one accepted, stock returned once.

**Server-authoritative money.** A checkout posting `subtotal: 1`,
`total: 1`, `deliveryCharge: -500` produces an order at the catalogue price.
A cart posting `unitPrice: 1` is priced from the catalogue.

**Account isolation.** A second customer gets 404 — not 403 — on read, update
and delete of another's address and orders; the owner's data is verified intact
afterwards. No response anywhere contains `passwordHash` or a `$2a$` prefix.

**Enumeration resistance.** A wrong password and an unknown account produce
byte-identical 401 bodies. Forgot-password returns 202 for both, and only the
real address queues an email — asserted by row count.

**Orders survive mail failure.** SMTP is broken for an entire exchange; the
order commits, holds its stock, and its notifications sit `PENDING` with
backoff. SMTP is restored and they deliver.

**Upload validation.** A PHP payload named `innocent.png` with
`Content-Type: image/png` is rejected on its magic bytes. So is a real PNG
renamed `.jpg`, and a 7000px image that is tiny on disk.

**XSS.** A policy page fed `<script>`, `onerror=` and a `javascript:` href
keeps its `<strong>` and its real link and loses the rest.

**CSV injection.** A customer named `=1+1` exports as `'=1+1`.

---

## Verified by hand against the running stack

| Check | Result |
|---|---|
| Storefront, shop, admin routes | 200 |
| Catalogue API, media, sitemap, robots | 200 · sitemap lists 10 URLs |
| `/api/v1/admin/**` anonymous | 401 |
| Swagger in `prod` | disabled (SPA fallback returned) |
| Security headers | 5/5 on every location checked |
| Caching | fingerprinted assets `immutable`, `index.html` `no-cache`, API `no-store` |
| gzip | active on the JS bundle |
| Bootstrap admin | created from env, forced password change enforced, then dashboard reachable |
| Outbox | 5 backlogged notifications delivered on the worker's first pass |

The storefront was also exercised through a browser by the project owner, who
placed order `KV-260829-H03F` before any of this was automated.

---

## Problems found by building the production path

The dev server never exercises nginx or the built bundle. Building the images
surfaced five real defects that no amount of `npm run dev` would have shown:

1. **`Host: karvya_backend`** — `proxy_pass` to an upstream whose name contains
   an underscore sends that name as the Host header, which is not a valid
   hostname. Tomcat answered 400 and `sitemap.xml` and `robots.txt` were dead.
2. **nginx `add_header` does not merge.** Security headers declared once at
   `server` level were silently discarded in every location that set its own
   `Cache-Control` — which was most of them. Confirmed as 0/5 headers before
   the fix.
3. **IPv6 health check.** `wget http://localhost/healthz` resolved to `::1`
   while `listen 80` is IPv4-only, so the container reported unhealthy while
   serving perfectly from outside.
4. **Media is not in the image.** It lives on a volume, so a fresh deployment
   has a working site with no photographs until the volume is seeded. Now
   documented as a required step.
5. **`Secure` cookie in CI.** `application-prod.yml` hardcoded it, so an
   HTTP-only test harness could never keep a session. It is now an explicit
   opt-out (`APP_COOKIE_SECURE`), secure by default.

---

## Placeholders that must be replaced before launch

Queried from the running database, not assumed:

- **6 settings still contain `[PLACEHOLDER]` text**, including all three policy
  pages and three homepage sections. Customers can see most of it.
- **8 settings are empty** — WhatsApp number, public email, business address,
  social links. These do not look wrong; the storefront simply hides whatever
  they drive.
- **All 5 seeded products are flagged `placeholder_content`.** Prices,
  dimensions and care instructions are invented placeholders.

The admin Settings screen reports both counts and labels each field, and the
product list marks every placeholder row.

Nothing in the application asserts a fact about the business that was not
supplied. Product descriptions state only what is visible in the photograph.

---

## Limitations

**Not implemented, by design**
- Online payment. `PaymentInitiator` is a seam with only the offline
  implementation registered.
- Email verification. The flow exists behind a setting, off by default.

**Not done**
- **Core Web Vitals have not been measured.** Bundle sizes and image weights
  are known (mui 110 kB gzip, entry 81 kB, product card image ~25 kB) but no
  Lighthouse or field measurement was taken, so no claim is made about LCP,
  CLS or INP.
- Keyboard and screen-reader testing was not performed. Contrast **was**
  measured across twelve foreground/background pairs; one failure was found
  (`stone` at 2.91:1, used by the out-of-stock badge) and fixed to 5.20:1.
- The CI workflow has never run — there is no remote yet. It is written against
  the same commands verified locally, but that is an untested claim.

**Known weaknesses**
- Rate limiting is in-process; behind multiple instances each permits the full
  quota.
- CSV export is capped at 5000 rows and rendered in memory.
- `style-src 'unsafe-inline'` is required by MUI's runtime style injection.
- Security headers are sent twice on `/api/` responses — nginx and Spring
  Security both set them, with the same values.

---

## Definition of done

Against the brief's own checklist:

| | |
|---|---|
| Visitor browses, adds to cart, contacts, orders as guest | ✅ end-to-end test |
| Visitor registers, signs in, resets password, manages profile and addresses, orders, sees only their own history | ✅ end-to-end + integration |
| Server validates prices and stock; no overselling | ✅ concurrency test, with the lock removed to prove it detects |
| Administrator signs in, is forced to change the seeded password, manages products, photographs, prices, stock, enquiries, orders, offline payments | ✅ verified live |
| Orders survive email failure | ✅ SMTP broken mid-order |
| WhatsApp links open with the configured number and encoded context | ✅ hidden entirely when unset |
| Runs through Docker Compose with persistent database and uploads | ✅ 4 services healthy, E2E passes against it |
| Builds, migrations and automated tests pass | ✅ 126 tests |
| No secrets committed, no fabricated business claims | ✅ `.env` and `*.pem` ignored; placeholders labelled |
