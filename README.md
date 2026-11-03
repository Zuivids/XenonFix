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
- **Audit log:** logins, password changes and changes to admins, in `audit_log`. Never write
  passwords to it. Later features add their own event types.

MFA is left out for now. To add it: create a table for the admins' secrets, make the login answer
`MFA_REQUIRED` from `AuthService.login` (`LoginResponse.Status` is ready for it), and grant the
admin authority only after the second step (`Authorities`).
## Languages

Latvian is at the site root, as on the old site (`/`, later `/par-mums`). Russian and English
are under a prefix (`/ru`, `/en/...`). The server renders every version in full, with
`lang`, canonical and `hreflang` set, so Google can index each one.

Texts are in `frontend/src/app/i18n/messages.*.ts`. A text missing from a language fails the
build. New pages are added in `languageRoutes` in `app.routes.ts`, and `app.routes.server.ts`
needs an entry for each language.

For production, list the site's host names under `allowedHosts` in `frontend/angular.json`,
or Angular refuses to render the request.
