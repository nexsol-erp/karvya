# Karvya

A storefront and back office for selling handwoven coir handicrafts. Guest and
registered checkout, offline payment, and an admin area for orders, catalogue,
enquiries and settings.

Phase one settles payment offline. There is no online gateway, and no fake one:
`PaymentInitiator` exists as a seam with only the offline implementation
registered, so phase two can add a provider without touching the order flow.

---

## Stack

| | |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA |
| Database | PostgreSQL 17, migrated by Flyway |
| Frontend | React 19, TypeScript, MUI 9, Vite |
| Serving | Nginx serves the built SPA and proxies `/api` |
| Mail | SMTP, sent from a transactional outbox |
| Tests | JUnit + Testcontainers, Vitest, Playwright |

---

## Running it

### Production-shaped, with Docker

```bash
cp .env.example .env
# edit .env - at minimum POSTGRES_PASSWORD and APP_ADMIN_PASSWORD
docker compose up -d --build
```

The storefront is then on <http://localhost>, the back office on
<http://localhost/admin>.

**Seed the media volume once**, or every product image will 404. The
derivatives are generated on the host and the container reads them from a named
volume, so they have to be copied across:

```bash
cd scripts && npm install && node optimise-images.mjs --crop-watermark && cd ..
docker run --rm -v karvya_media_data:/media -v "$PWD/media:/src:ro" alpine   sh -c 'cp -r /src/. /media/'
```

On Windows use the native path (`E:/karvya/media`) rather than a Git Bash one,
which Docker cannot resolve.

Sign in with the `APP_ADMIN_*` values from `.env`. **The first administrator is
forced to change its password before it can reach anything else** — the
bootstrap value is known to whoever deployed the application and often ends up
in a shell history.

### Development, with hot reload

Development runs the backend and frontend outside Docker against a
containerised database and mail sink.

```bash
# database on 55432, and Mailpit on 1025 (UI at http://localhost:8025)
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d db mail

# backend on 8080
cd backend
SPRING_PROFILES_ACTIVE=dev \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/karvya \
SPRING_DATASOURCE_USERNAME=karvya SPRING_DATASOURCE_PASSWORD=... \
MAIL_HOST=localhost MAIL_PORT=1025 \
APP_ADMIN_EMAIL=admin@karvya.local APP_ADMIN_PASSWORD=... \
mvn spring-boot:run

# frontend on 5173, proxying /api and /media to 8080
cd frontend && npm install && npm run dev
```

**The database is on 55432, not 5432.** A native PostgreSQL service on the
developer machine will otherwise win the port, and the failure appears as a
baffling "password authentication failed" while the container is perfectly
healthy.

---

## Environment variables

Everything environment-specific comes from here. Nothing has a secret baked in
as a default.

| Variable | Purpose |
|---|---|
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | Database. Set a real password. |
| `SPRING_PROFILES_ACTIVE` | `prod` or `dev`. |
| `APP_BASE_URL` | Public origin. Used in emails, canonical URLs and the sitemap. |
| `APP_ADMIN_USERNAME` / `APP_ADMIN_EMAIL` / `APP_ADMIN_PASSWORD` | The first administrator, created on first boot only. With no password set, **no account is created** and the log says so. |
| `APP_WHATSAPP_NUMBER` | Digits only, country code first. Empty hides every WhatsApp link. |
| `APP_ADMIN_NOTIFICATION_EMAIL` | Where order and enquiry alerts go. |
| `APP_STORAGE_DIR` | Upload directory. Mounted as a volume. |
| `APP_OPENAPI_ENABLED` | Swagger UI. **Keep `false` in production.** |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` / `MAIL_FROM` | SMTP. |

Most day-to-day configuration is **not** here — store name, delivery charge,
payment instructions, policy pages and homepage copy live in the database and
are edited in **Settings** in the back office, so changing them needs no
deployment.

---

## Concurrency: how overselling is prevented

Two mechanisms, and they do different jobs.

`Product` carries a **`@Version` optimistic lock**. It guards administrator
edits against lost updates, and it is also a last line of defence during
checkout.

Checkout additionally takes a **pessimistic row lock**: `PlaceOrderService`
selects every product in the order `FOR UPDATE`, **in ascending id order**.
The ordering is what prevents deadlock — two concurrent checkouts touching the
same products acquire the locks in the same sequence, so one waits rather than
the pair colliding. Stock is re-checked and decremented inside that same
transaction, alongside the line-item snapshots and the order row, so either all
of it commits or none does.

The pessimistic lock was measured, not assumed. Removing it and running the
concurrency test — ten buyers racing three units — does **not** produce an
oversell, because the version column makes the losing transactions fail. What
it produces is *under*-selling: only two of the three units sold, and a real
customer was turned away from stock that existed. The row lock is what makes
buyers queue instead of collide.

Cancellation restores stock through `stockRestoredAt`, claimed under the same
row lock, so a double-click or two administrators cannot credit it twice.

---

## Product images

Originals live untouched in `assets/originals/`. The pipeline writes responsive
derivatives into the media volume:

```bash
cd scripts && npm install
node optimise-images.mjs                     # keep the frame as shot
node optimise-images.mjs --crop-watermark    # trim the bottom 15%
```

Four widths (480/800/1280/native) in AVIF, WebP and JPEG. The database stores
only a base key; `{key}-{width}.{format}` is composed by the client, so adding
a width or a format is a pipeline change rather than a migration.

Uploads through the admin are validated on **size, extension, magic bytes and
pixel dimensions** — the declared content type is never trusted — and stored
under a generated UUID inside a path checked to be within the storage root.

---

## Tests

```bash
cd backend  && mvn test     # 111 - integration against real PostgreSQL
cd frontend && npm test     # 11  - components
cd frontend && npm run e2e  # 4   - full flows against the dev server
```

To run the end-to-end suite against the Docker stack instead of the dev server:

```bash
E2E_BASE_URL=http://localhost E2E_ADMIN_EMAIL=... E2E_ADMIN_PASSWORD=... npx playwright test
```

Setting `E2E_BASE_URL` stops Playwright starting Vite, so the tests exercise
the production path - nginx, the built bundle, the real container - rather than
a dev server that behaves differently.

Integration tests use **Testcontainers, not H2**, because most of what is worth
testing here is behaviour H2 does not reproduce: partial unique indexes,
`FOR UPDATE` semantics, JSONB, and PostgreSQL's refusal to type a null bind
parameter.

---

## Backing up and restoring

`pg_dump` from inside the running container. The custom format compresses and
allows selective restore.

```bash
# back up
docker compose exec -T db pg_dump -U karvya -d karvya -Fc \
  > backup-$(date +%Y%m%d-%H%M).dump

# uploaded images are NOT in the database and must be captured separately
docker run --rm -v karvya_media_data:/media -v "$PWD:/backup" alpine \
  tar czf /backup/media-$(date +%Y%m%d).tar.gz -C /media .
```

Restore into an empty database:

```bash
docker compose down
docker volume rm karvya_db_data
docker compose up -d db
docker compose exec -T db psql -U karvya -d karvya -c \
  'DROP SCHEMA public CASCADE; CREATE SCHEMA public;'
docker compose exec -T db pg_restore -U karvya -d karvya --no-owner < backup.dump

docker run --rm -v karvya_media_data:/media -v "$PWD:/backup" alpine \
  sh -c 'tar xzf /backup/media-YYYYMMDD.tar.gz -C /media'

docker compose up -d
```

**Verify a restore before you need one.** A backup that has never been restored
is a hypothesis. Restore into a scratch database, sign in, and open an order.

---

## Deploying to a Linux server

```bash
git clone <repository> karvya && cd karvya
cp .env.example .env      # then edit it
docker compose up -d --build
docker compose logs -f backend
```

Three things to get right:

**TLS terminates in front of this stack** — Caddy, or nginx with certbot, or a
load balancer. The container listens on plain HTTP and reads
`X-Forwarded-Proto`, and `SPRING_PROFILES_ACTIVE=prod` marks the session cookie
`Secure`, so **sessions will not work until HTTPS is actually in front of it.**
HSTS belongs on the terminator, not here.

**Only port 80 is published.** The database and backend are reachable only on
the compose network. Do not add a `ports:` entry to `db` on a public host —
that is what `docker-compose.dev.yml` is for, and it is never used in
production.

**Back the volumes up.** `karvya_db_data` and `karvya_media_data` hold
everything that cannot be rebuilt from the repository.

### Logging

Logs go to stdout for the Docker driver to collect. Order numbers and account
ids are logged; **passwords, reset tokens, session ids and email bodies are
not.** A failed notification records its recipient and the error, never the
message.

---

## Before going live

The seeded catalogue is deliberately provisional, and the back office says so.

1. **Settings → 6 entries still hold `[PLACEHOLDER]` text**, including all three
   policy pages. Customers can see most of it.
2. **9 settings are empty** — WhatsApp number, public email, business address,
   social links. These do not look wrong; the storefront simply hides what they
   drive.
3. **Every seeded product is flagged `placeholder`.** Prices, dimensions and
   care instructions are invented placeholders and must be replaced.
4. The photographs carry a small sparkle mark in the corner. `--crop-watermark`
   removes it; the derivatives currently shipped were generated with it.

Nothing in the application asserts a fact about the business that was not
supplied. Product descriptions state only what is visible in the photograph.

---

## Known limitations

- **Rate limiting is in-process.** Behind more than one instance each process
  permits the full quota. Moving it to Redis is the fix.
- **The outbox worker is single-instance-safe but not sharded.** Rows are
  claimed `FOR UPDATE SKIP LOCKED`, so a second instance takes different rows,
  but there is no partitioning.
- **CSV export is capped at 5000 rows** and rendered in memory. Streaming would
  need the query kept open past the transaction, which is what silently
  truncated an earlier attempt.
- **No online payment.** By design for phase one.
- `style-src 'unsafe-inline'` is required by MUI's runtime style injection. A
  nonce-based policy is the next improvement.
- **Security headers are sent twice on `/api/` responses** - once by nginx and
  once by Spring Security. Harmless, since both send the same values, but one
  of the two should own them.
- **Media lives on a volume, not in the image.** A fresh deployment needs the
  seeding step above; forgetting it produces a working site with no
  photographs.

git remote add origin git@github.com:nexsol-erp/karvya.git
git branch -M main
git push -u origin main