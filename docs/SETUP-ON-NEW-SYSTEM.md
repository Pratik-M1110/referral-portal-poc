# Referral Portal: setup on a new system

This guide is for getting a fresh clone running locally on Windows. It describes the repository as it is today; it does not require copying generated build output or local credentials from another computer.

## What to install

- Git
- Docker Desktop with Docker Compose
- Java 21 JDK
- Node.js 24 LTS (npm is included)

The backend includes the Maven wrapper, so you do not need a separate Maven installation. The wrapper and frontend lockfile are committed and should be used for repeatable builds.

## Clone and configure

Open PowerShell and run:

```powershell
git clone https://github.com/Pratik-M1110/referral-portal-poc.git
Set-Location referral-portal-poc
Copy-Item .env.example .env
```

The root `.env` is ignored by Git. The example contains local Docker database defaults; do not put real credentials in the example file or commit your `.env`.

The frontend's tracked `frontend/.env.development` points the Vite development server at `http://localhost:8080`. Change it only if you run the backend on a different address.

## Start the application

Keep the following processes running in separate PowerShell terminals.

### 1. Start the local MySQL database

From the repository root:

```powershell
docker compose up -d mysql
docker compose ps
```

Compose exposes MySQL on host port `3307` and stores its data in a Docker volume. The Spring backend runs Flyway migrations on startup.

### 2. Start the backend

From the repository root:

```powershell
.\backend\mvnw.cmd -f backend\pom.xml spring-boot:run
```

The backend listens on `http://localhost:8080`. The first run may take longer while Maven downloads dependencies.

### 3. Start the frontend

In another terminal:

```powershell
Set-Location frontend
npm ci
npm run dev
```

Open the Vite URL printed in the terminal (normally `http://localhost:5173`).

## Configure sign-in and email verification

Google/GitHub OAuth and Brevo SMTP are not supplied by GitHub. To test those flows, create or use provider credentials and put them in the root `.env`:

- `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`
- `GITHUB_CLIENT_ID` and `GITHUB_CLIENT_SECRET`
- `MAIL_USERNAME`, `MAIL_PASSWORD`, and `MAIL_FROM` for Brevo

Register these local OAuth callback URLs with the providers:

```text
http://localhost:8080/login/oauth2/code/google
http://localhost:8080/login/oauth2/code/github
```

Restart the backend after changing `.env`. Keep all real OAuth and mail credentials private; never paste them into source files, issues, chat, or commits. Without provider credentials, the application can start, but real OAuth sign-in and email delivery will not work.

`SEED_DEMO=true` enables sample companies, users, and a sample inbox for local demonstrations. It is enabled by the copied `.env.example`. Keep it disabled in any real or production environment.

## Verify the setup

From the repository root:

```powershell
.\backend\mvnw.cmd -f backend\pom.xml test
npm --prefix frontend run lint
npm --prefix frontend run build
```

The backend health endpoint is `http://localhost:8080/actuator/health`.

## Stop services and preserve local data

Stop the frontend and backend with `Ctrl+C`, then stop MySQL:

```powershell
docker compose down
```

The Docker volume normally preserves the local database for the next run. **Do not use `docker compose down -v` unless you intentionally want to delete that database.** Git does not transfer the database volume or uploaded resume data. If you need existing local data on the new computer, export and transfer a database backup separately using a secure method.

## Common setup problems

- **Docker cannot start:** Confirm Docker Desktop is running, then retry `docker compose up -d mysql`.
- **Backend cannot connect to MySQL:** Check `docker compose ps` and confirm `.env` has the local database values from `.env.example`. The host port is `3307`, not the usual `3306`.
- **A port is already in use:** The defaults are MySQL `3307`, backend `8080`, and Vite `5173`. Stop the conflicting process or update the corresponding configuration consistently.
- **OAuth reports a redirect/callback error:** Make sure the callback registered with the provider exactly matches the URL above and that the corresponding client ID/secret is in `.env`.
- **Email verification does not send mail:** Configure valid Brevo SMTP credentials and `MAIL_FROM`; the app does not include email-provider credentials.
- **Fresh-clone build fails:** Use Node.js 24 and `npm ci` (not a copied `node_modules` directory); use the checked-in Maven wrapper for the backend.

## What should and should not be in GitHub

Keep the files needed to recreate the application under version control: source code, Maven wrapper files, `backend/pom.xml`, Flyway migrations, `frontend/package.json` and `frontend/package-lock.json`, Docker configuration, `.env.example`, and this guide. `frontend/.env.development` currently contains only the local backend URL.

Do not push `.env`, OAuth/SMTP credentials, database dumps, uploaded resumes, `node_modules`, frontend `dist`, or backend `target` build output. Those are local, sensitive, or reproducible.

The root `README.md` is the repository's public setup entry point. The root `.gitignore` continues to exclude `CLAUDE.md`, `docs/MVP.md`, and the screen-ideas PDF. If future contributors or Copilot need the product brief or wireframes, review those files for private content before deciding whether to commit them.

## Ask Copilot for help on the new system

This guide is the setup source of truth for this repository. In a new clone, ask Copilot: "Read `docs/SETUP-ON-NEW-SYSTEM.md` and help me set up the Referral Portal locally. First check prerequisites and diagnose any errors without exposing or committing secrets."
