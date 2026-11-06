# XenonFix.lv

New website of SIA Elekanga: headlight repair and a small headlight catalog. It will replace
the current site at https://xenonfix.lv/.

- `backend/`: Java 21, Spring Boot 4.1, Flyway, MariaDB
- `frontend/`: Angular with server-side rendering and hydration
- `compose.yaml`: MariaDB 11.4 for local development

## Run locally

You need Docker Desktop (running), Java 21 and Node.js.

| | Windows | Linux, macOS, Git Bash |
|---|---|---|
| Start everything | `dev-start.bat` | `./dev-start.sh` |
| Stop (data is kept) | `dev-stop.bat` | `./dev-stop.sh` |
| Delete the local database | `dev-reset-db.bat` | `./dev-reset-db.sh` |

The first start creates `.env` from `.env.example` with random passwords. Never commit `.env`.
If you delete `.env`, reset the database too, because it keeps the old password.

| | Address |
|---|---|
| Site | http://localhost:4300 (`/ru`, `/en`) |
| Admin panel | http://localhost:4300/admin, login in `.env` |
| Backend health | http://localhost:8090/api/health |
| Database | `localhost:3308`, database `xenonfix`, login in `.env` |
| Emails sent locally | http://localhost:8026 (Mailpit; nothing reaches real addresses) |
| Logs | `logs/backend.log`, `logs/frontend.log` |

The ports differ from other local projects on purpose, so they can run side by side and
`dev-stop` does not stop anything else.

## Tests

```bash
cd backend && ./mvnw test
```

```bash
cd frontend && npm test
```

The backend tests start a MariaDB container, so Docker must be running.

## Backend layout

One package per feature under `lv.xenonfix`, for example `health`, with its controller and
service, and request and response records in a `dto` subpackage. Controllers only take and
return DTOs, never database entities. Schema changes are Flyway migrations in
`src/main/resources/db/migration`; Hibernate only validates against them.

Uploaded files stay on disk, in the folder set by `APP_UPLOADS_PATH`, and are served at
`/uploads/**`. The database holds their paths.

## Admin panel

The panel is at `/admin`, in Latvian only, and is not indexed by search engines.

- **First admin:** created at startup from `APP_ADMIN_EMAIL` and `APP_ADMIN_PASSWORD` while there
  are no admins. After that these settings are ignored. Outside the `local` profile, the password
  must be changed at first login.
- **More admins:** an admin adds them in the panel with a temporary password. The panel can also
  disable an admin and set a new temporary password for a forgotten one. Nobody can do either to
  their own account, so one active admin always remains.
- **Protection:** a two-step login (below), Argon2id password hashes, session cookie with CSRF
  protection, 30 minutes idle and 8 hours absolute session time, blocks after repeated failures,
  and a request limit per IP.
- **Products:** name and description per language (only Latvian is required), part number, price,
  sale price, stock and photos. A product starts as a draft and needs a photo to be published.
  Products are archived rather than deleted, because reservations will refer to them.
- **Photos:** JPEG or PNG up to 20 MB, judged by content. Each is turned upright and re-encoded as
  a JPEG of at most 1600 px plus a 600 px thumbnail, which also drops metadata such as GPS. Files
  are in `products/<id>/` under the uploads folder, so back that folder up with the database.
- **Reservations:** every reservation with the customer's details, a search and a status filter. Any
  status can become any other. Stock returns when a reservation is cancelled and is taken again when
  it is reopened, which is refused if not enough is left. The customer gets an email when it is
  confirmed or cancelled, but not for corrections to a completed one. Notes are for admins only: they
  are never in public responses, emails or the audit log.
- **Deleting customer data:** for a customer who asks, a cancelled or completed reservation's page has
  "Dzēst klienta datus". It erases the name, phone, email, comment and notes, and keeps the number,
  product, amounts, status and dates. An anonymized reservation can no longer be changed. Every night
  at 03:30 the same happens to cancelled and completed reservations not changed for
  `app.reservation.retention` (183 days, the policy's "6 months"). The privacy policy states this period: change both together.
- **Pages ("Lapas"):** the texts, headings and photos of the home page, Par mums, Mūsu pakalpojumi,
  Mūsu darbi and Kontakti, and the shop's contact details used everywhere, in three languages. See
  "Editable pages" below.
- **Audit log:** logins, password changes, and changes to admins, products, pages and reservations, in `audit_log`.
  Never write passwords to it. Later features add their own event types.

### Two-step login

Every admin logs in with their password and then a 6-digit code from an authenticator app (Google
Authenticator, Microsoft Authenticator or similar). There are no remembered devices.

1. **Password** (`POST /api/admin/auth/login`). A right password alone gives no access and does not
   reset the failure counts.
2. **Code** (`POST /api/admin/auth/mfa`): from the app, or one of the admin's backup codes. An admin
   without the app is sent to `/admin/mfa` instead. It shows a QR code and the key as text, and a code
   from the app proves the setup. Then the admin gets 10 backup codes, shown once.
3. **Temporary password:** changed only after the second step.

How it is protected:
- **Permissions:** each step gives the session only what that step needs (`Authorities`,
  `SecurityConfig`). The second step must follow within 5 minutes, or the session ends.
- **Wrong codes** count towards the same lockouts as wrong passwords, and a code works only once.
- **Keys:** the authenticator keys are stored encrypted with `APP_MFA_KEY` (`MfaSecretCipher`, AES-GCM).
  Backup codes are stored only as hashes.
- **The key setting:** without `APP_MFA_KEY` the backend does not start. `dev-start` adds one to `.env`.
  If the key is lost or changed, every admin must set up the app again: keep it with the backups.

A lost phone:
- **Backup code:** the admin logs in with one instead of a code from the app.
- **Another admin:** on the admins page, "Atiestatīt divpakāpju pieslēgšanos". This erases the setup and
  the backup codes, and ends the admin's sessions. They set up the app again at their next login.
  Nobody can reset their own.
- **New backup codes:** on the password page, with a current code from the app. The old ones stop working.
- **If no admin can log in:** set `mfa_secret` to `NULL` for that admin in the database, and delete their
  `admin_recovery_code` rows.

## Store and reservations

Customers have no accounts. The store is at `/store`, a product at `/product/<slug>`, with the
usual language prefixes. Only published products are shown, without the stock number, and text
that is not translated falls back to Latvian.

- **Reserving:** one product per reservation, up to 10 pieces, without paying. The customer collects
  it at the shop. `POST /api/store/reservations` is the only public endpoint that changes anything:
  it is limited per IP and has a hidden field to catch bots. It needs no CSRF token, because it uses
  no login or session.
- **Stock** is taken when the reservation is made, in one conditional `UPDATE`, so two customers
  cannot get the last piece. The same statement raises the product's version, so an admin's product
  form opened earlier is refused rather than putting the old stock back. It stays taken until an
  admin cancels the reservation.
- **Emails** go to the customer, in their language, and to the shop (`APP_MAIL_SHOP`), after the
  reservation is saved and in the background. A mail problem never loses a reservation, but a failed
  email is not sent again; the log has the reference only.
- **Locally** emails stop at Mailpit, unless `.env` names a real mail server (see `.env.example`).
  The automated tests never send real email.
- **On a server** set `SPRING_MAIL_HOST` and the other `SPRING_MAIL_*` settings, or the backend does
  not start. Set `BACKEND_URL` for the frontend server if the backend is not at
  `http://localhost:8090`.

## Editable pages

The home page, Par mums, Mūsu pakalpojumi, Mūsu darbi and Kontakti, and the contact details
(phone, email, address, opening hours, social links, company details), are edited in the panel under
"Lapas". The design stays fixed: each heading, text and photo is a field, and lists can be added to,
removed from and reordered.

- **Storage:** one row per page in the `page` table, holding a JSON document with all three
  languages; each text is `{lv, ru, en}`. Its shape is a record in `lv.xenonfix.page.content`, and a
  save that does not fit it is refused. Latvian is required; the site shows Latvian where Russian or
  English is empty.
- **Starting content:** `backend/src/main/resources/pages/*.json`, today's texts. A page without a row
  gets its file at startup; an edited page is never replaced. To change a page's fields, change its
  record, its JSON file, the frontend types in `pages/page-api.ts` and the editor in
  `admin/pages/page-schemas.ts` together. A stored page that no longer fits needs a migration.
- **Photos:** resized and cleaned like product photos, in `pages/` under the uploads folder, so the
  existing backup covers them. Saving deletes a page's photos it no longer uses. Uploads never saved
  into a page are deleted after a day, at night.
- **The site** reads pages from `GET /api/pages/{page}?lang=`. When the backend cannot be reached they
  show a short "not available" message (503). The header and footer then fall back to the built-in
  contact details in `pages/site-info.ts`.
- **Contact details** also go into the customer emails (address, phone, email, company).
- **The legal texts** (payment, delivery and warranty terms, privacy policy) stay in
  `i18n/content.*.ts`, and short interface texts in `i18n/messages.*.ts`.

## Languages

Latvian is at the site root, as on the old site (`/`, `/par-mums`). Russian and English are under
a prefix with the same page names (`/ru`, `/en/par-mums`). The server renders every version in
full, with `lang`, canonical and `hreflang` set, so Google can index each one.

Short texts are in `frontend/src/app/i18n/messages.*.ts`, and long ones in `content.*.ts`. A text
missing from a language fails the build or a test. New pages are added to `PAGES` in
`app.routes.ts`, which also covers all languages on the server.

New files in `frontend/public` appear only after restarting the dev server.

For production, list the site's host names under `allowedHosts` in `frontend/angular.json`,
or Angular refuses to render the request. `NG_ALLOWED_HOSTS` adds more without a rebuild.

## Old addresses, sitemap and robots

The frontend server (`frontend/src/server.ts`) answers these before Angular, so they work locally
too:

- **Old site addresses** (`src/server/old-addresses.ts`): a trailing `/` is dropped with a permanent
  redirect, keeping the query (`/kontakti/` to `/kontakti`). Renamed pages redirect too, for example
  `/shop/` to `/store` and `/piegades-nosacijumi-2/` to `/piegades-nosacijumi`. WordPress's own
  addresses (`/wp-admin/…`, `/feed/`, `/?p=12`) answer 410 Gone, so search engines drop them.
- **`/sitemap.xml`** lists every page and published product in all three languages, each with its
  other languages, from `GET /api/store/sitemap`. If the backend does not answer it gives the last
  list it had, or 503 so search engines try again.
- **`/robots.txt`** is in `frontend/public`.

**Old product addresses** keep working when a product's page address field is the old one. The old
shop had these 13, all under `/product/`:

```
audi-a6-c7-4g-2015-2017-prieksejais-kreisais-lukturis
audi-a6-c7-4g-2015-2018-prieksejais-kreisais-lukturis
audi-a5-8t-2011-2016-prieksejais-labais-lukturis-copy
audi-a6-c7-4g-2015-2018-prieksejais-labais-lukturis-2
audi-a6-c7-4g-2015-2018-prieksejais-kreisais-lukturis-2
bmw-e60-e61-2007-2010-labais-lukturis
audi-a6-c7-4g-2011-2014-labais-lukturis-3
audi-a6-c7-4g-2011-2014-labais-lukturis-2
audi-a6-c7-4g-2011-2014-labais-lukturis
vw-tiguan-2017-2019-labais-lukturis
volvo-xc60-2009-2013-labais-lukturis
volvo-xc60-2009-2013-kreisais-lukturis
skoda-octavia-2020-aizmugurejais-lukturis
```

## Going live

The server runs everything in Docker (`compose.prod.yaml`): MariaDB, the backend, the frontend
server and Caddy, which gets the HTTPS certificates and is the only part reachable from outside.
Caddy sends `/api` and `/uploads` to the backend and everything else to the frontend, and redirects
`www` to the main address (`deploy/Caddyfile`).

1. **A server:** any small Linux server (2 GB of memory is enough), with Docker and its Compose
   plugin installed. Allow ports 80 and 443 in its firewall, and nothing else besides SSH.
2. **The code:** `git clone https://github.com/Zuivids/XenonFix.git` and `cd XenonFix`.
3. **Settings:** `cp deploy/.env.production.example .env`, then fill in `.env`: two new database
   passwords, `APP_MFA_KEY` (`openssl rand -base64 32`), the first admin, and the mail server from the
   email provider. Never commit this file. Keep a copy of `APP_MFA_KEY` with the backups, off the server.
4. **Try it before DNS:** `docker compose -f compose.prod.yaml up -d --build`. `docker compose -f
   compose.prod.yaml ps` should show all four as running, three of them healthy. Caddy can only get
   certificates once DNS points to the server, so until then its log shows certificate errors.
5. **DNS:** point `xenonfix.lv` and `www.xenonfix.lv` (A records, and AAAA if the server has IPv6)
   to the server. Within minutes of that Caddy has its certificates and the site answers at
   https://xenonfix.lv. The old WordPress site stops being reachable at that moment.
6. **First login** at https://xenonfix.lv/admin with the admin from `.env`: set up the authenticator
   app, keep the backup codes, and change the password. Then add the second admin, and remove
   `APP_ADMIN_PASSWORD` from `.env`.
7. **Products:** enter them, with the old page addresses above where they apply.
8. **Backups:** see below. Set them up on the first day.
9. **Search engines:** in Google Search Console, submit `https://xenonfix.lv/sitemap.xml`.

**Updating** after new commits: `git pull`, then `docker compose -f compose.prod.yaml up -d --build`.
Database migrations run by themselves when the backend starts.

**Logs:** `docker compose -f compose.prod.yaml logs backend` (or `frontend`, `caddy`), with `-f` to
follow.

**Visitor addresses:** the backend takes the visitor's address from `X-Forwarded-For`, which Caddy
sets, but only from private network addresses (`application-prod.yml`). That is why the backend's
port must never be published: rate limits, lockouts and the audit log depend on that address.

### Backups

`deploy/backup.sh` writes the database and the uploaded photos into one dated folder under
`/var/backups/xenonfix`, and keeps the newest 14. Run it every night with cron (`crontab -e`):

```
15 3 * * * cd /root/XenonFix && deploy/backup.sh >> /var/log/xenonfix-backup.log 2>&1
```

A backup on the server's own disk is lost with the server, so also copy that folder somewhere else
regularly, for example with `rsync` to another machine or the provider's backup service. The backup
does not contain `.env`: keep `APP_MFA_KEY` and the database passwords safe separately, or a restored
database needs every admin to set up the authenticator app again.

`deploy/restore.sh <folder>` puts a backup back, after asking. Everything changed since that backup
is lost, and the site is down for the minute it takes.
