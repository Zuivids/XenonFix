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
- **Protection:** Argon2id password hashes, session cookie with CSRF protection, 30 minutes idle
  and 8 hours absolute session time, blocks after repeated failures, and a request limit per IP.
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
- **Audit log:** logins, password changes, and changes to admins, products and reservations, in `audit_log`.
  Never write passwords to it. Later features add their own event types.

MFA is left out for now. To add it: create a table for the admins' secrets, make the login answer
`MFA_REQUIRED` from `AuthService.login` (`LoginResponse.Status` is ready for it), and grant the
admin authority only after the second step (`Authorities`).

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

## Languages

Latvian is at the site root, as on the old site (`/`, `/par-mums`). Russian and English are under
a prefix with the same page names (`/ru`, `/en/par-mums`). The server renders every version in
full, with `lang`, canonical and `hreflang` set, so Google can index each one.

Short texts are in `frontend/src/app/i18n/messages.*.ts`, and long ones in `content.*.ts`. A text
missing from a language fails the build or a test. New pages are added to `PAGES` in
`app.routes.ts`, which also covers all languages on the server.

New files in `frontend/public` appear only after restarting the dev server.

For production, list the site's host names under `allowedHosts` in `frontend/angular.json`,
or Angular refuses to render the request.
