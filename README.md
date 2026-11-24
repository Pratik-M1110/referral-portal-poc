# Referral Portal

An MVP that helps job seekers request company referrals and helps employees manage those requests.

This guide covers a fresh Windows 10/11 setup using PowerShell and the shared Aiven development database. Docker Compose MySQL is available as an isolated local alternative. For additional setup notes, see [docs/SETUP-ON-NEW-SYSTEM.md](docs/SETUP-ON-NEW-SYSTEM.md).

For the current system diagrams and component overview, see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Prerequisites

- Windows 10 (version 2004/build 19041 or later) or Windows 11, 64-bit.
- Git for Windows.
- Java 21 JDK.
- Node.js 24 LTS (npm is included).
- Docker Desktop with WSL 2 is optional, and is only needed for the isolated local MySQL alternative.

You do not need to install Maven separately; the backend includes the Maven wrapper.

## Install on Windows

Install Git, the Java 21 JDK, and Node.js LTS from PowerShell:

```powershell
winget install --id Git.Git --exact
winget install --id EclipseAdoptium.Temurin.21.JDK --exact
winget install --id OpenJS.NodeJS.LTS --exact
```

For isolated local MySQL, also install Docker Desktop and enable its WSL 2 backend. Docker may require virtualization support, a Windows restart, and administrator approval. Docker Desktop sign-in is not normally required for local development.

Close and reopen PowerShell or VS Code after installation so the updated `PATH` is loaded. Verify the tools:

```powershell
git --version
java -version
node --version
npm --version
```

Confirm Java reports version `21` and Node reports major version `24`. If using local MySQL, also verify Docker Compose:

```powershell
docker compose version
```

The Winget LTS package can move to a newer major version over time; if it does not install Node 24, select the Node 24 Windows installer from the [official Node.js downloads page](https://nodejs.org/en/download) and verify with `node --version`.

## Clone and configure

In PowerShell, clone the repository and create the local environment file:

```powershell
git clone https://github.com/Pratik-M1110/referral-portal-poc.git
Set-Location referral-portal-poc
Copy-Item .env.example .env
```

The normal team development setup uses the shared Aiven development database. In the ignored root `.env`, set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` to the development values provided privately by the project maintainer. The checked-in `.env.example` contains local Docker defaults, not Aiven credentials. Never commit `.env` or put credentials in chat, issues, or documentation. The app does not automatically seed sample companies, users, or roles. Production will use a different database server, not the shared development database.

To use an isolated local database instead, keep the local database values from `.env.example` and start Docker Compose MySQL in the next section. Local Docker data is separate from Aiven and is persisted in a Docker volume.

Google/GitHub OAuth and Brevo email credentials are not included. The app can start without them, but those sign-in and email flows will not work until configured. Password registration also needs working email delivery to verify ownership before creating an account. For local OAuth, register these callback URLs with the providers:

```text
http://localhost:8080/login/oauth2/code/google
http://localhost:8080/login/oauth2/code/github
```

## Referral workflow

Anyone with a seeker profile can request a referral; work-email verification is not required to seek help. The Companies page shows companies with verified referrers, without listing employee identities. A seeker chooses a role title, provides an HTTPS job link, and may add a note. Their profile summary and sign-in email are shared with verified referrers who receive the request according to the displayed rules; sign-in email is visible only to employees who accept.

One request covers one company and role. Every verified referrer at that company receives it, but only one employee can accept at a time; other invitees see that someone else accepted. Employees can share their own contact fields by consent, then mark the referral submitted after using their company's internal process. A seeker confirms submission in the app. Acceptance does not guarantee an opening, completed referral, or interview.

Unverified seekers start with 2 base requests per rolling 30 days. Verifying a company work email raises the base allowance to 5 total for that rolling period. Confirmed referrals can earn up to 2 additional credits per 30 days; credits expire after 90 days. Unaccepted requests expire after 4 days and return one request to the seeker's allowance. Accepted referrers receive daily reminders for 3 days; if they do not submit within 96 hours, the seeker can continue with another invited referrer for 4 days. If none remain or the seeker does not continue, the request expires and its credit is returned.

Employees who verify a work email automatically receive referral requests for their company. They can choose role-help preferences (informational only; requests go to all verified referrers), and set a cap of 1-10 active accepted assignments. Optional phone numbers are saved in the profile and shared only when each participant consents for that request. If a seeker has uploaded a PDF resume, it is shared with the verified referrers invited to each request so they can review it before accepting.

The app defaults to light theme. Use the Sun/Moon control to switch themes; the choice is saved in the current browser.

The former meeting/chat UI is hidden for now. Its backend endpoints, tables, and existing records remain in the repository for possible future reintroduction.

## Start the application

Run the backend and frontend in separate PowerShell terminals. Start Docker Desktop and the local MySQL service only if you selected the isolated local database instead of Aiven.

1. Optional, for local MySQL only, start the database from the repository root:

   ```powershell
   docker compose up -d mysql
   docker compose ps
   ```

   Wait until the `mysql` service reports healthy. Docker maps MySQL to host port `3307`; its data is kept in a Docker volume. Skip this step when using Aiven.

2. Start the backend from the repository root:

   ```powershell
   .\backend\mvnw.cmd -f backend\pom.xml spring-boot:run
   ```

   The backend listens on `http://localhost:8080`. On first run, Maven downloads dependencies and Flyway creates the local schema.

3. Start the frontend from the repository root in another terminal:

   ```powershell
   npm --prefix frontend ci
   npm --prefix frontend run dev
   ```

   Open the Vite URL printed in the terminal, normally `http://localhost:5173`.

Check that the backend is ready at `http://localhost:8080/actuator/health`; it should return `{"status":"UP"}`.

## Run checks

From the repository root:

```powershell
.\backend\mvnw.cmd -f backend\pom.xml test
npm --prefix frontend run lint
npm --prefix frontend run build
```

## GCP Cloud Run preparation

Render and Aiven remain the development environment. The opt-in `cloudrun` Spring
profile uses the Cloud SQL Java Connector; without this profile, the existing
`DB_URL` configuration and local/Render behavior are unchanged. The Dockerfile
packages the React frontend and backend into one service. Docker build context
rules exclude dotenv files, including nested frontend files; never bake credentials
or a development API origin into a production image.

The initial GCP target is:

| Resource | Value |
| --- | --- |
| Project | `referral-portal-production` |
| Region | `asia-south1` (Mumbai) |
| Artifact Registry repository | `referral-portal` |
| Cloud SQL connection name | `referral-portal-production:asia-south1:referral-portal-prod-db` |
| Database | `referral_portal` |
| Database user | `referral_app` |
| Runtime service account | `referral-portal-runtime@referral-portal-production.iam.gserviceaccount.com` |
| Password secret | `referral-db-password` |

Set these on Cloud Run, not in the development `.env`:

| Environment variable | Value |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `cloudrun` |
| `CLOUD_SQL_INSTANCE` | The Cloud SQL connection name above |
| `DB_NAME` | `referral_portal` |
| `DB_USERNAME` | `referral_app` |
| `DB_PASSWORD` | Secret reference to `referral-db-password`, version `1`; never a literal in a deploy command |
| `APP_FRONTEND_URL` | The service's canonical HTTPS origin, without a trailing slash; update it if the production domain changes |

Assign the runtime service account to the Cloud Run revision. It needs Cloud SQL
Client on the project and Secret Manager Secret Accessor on this specific secret.
The Java Connector uses the attached identity's Application Default Credentials
and encrypts traffic to the database's public IP; no service-account JSON key,
authorized network, VPC connector, separate proxy process, or Cloud Run Cloud SQL
socket attachment is needed for this connection method. Do not copy the Cloud
Shell `--get-server-public-key` option into the JDBC configuration. The profile
uses lazy certificate refresh for request-based CPU allocation, up to five database
connections per instance, and secure session cookies. It deliberately overrides
`DB_URL` and requires an explicit frontend origin for OAuth redirects and email links;
production serves the SPA on the same origin.

### First deployment is a private smoke test, not a user launch

- The initial smoke test required Cloud Run IAM authentication. The web service
  was subsequently made public by user request for testing and the demo; the
  maintenance job remains private. Do not invite volunteers before the gates below.
- Start with request-based billing, minimum instances `0`, maximum instances `1`,
  concurrency `20`, one vCPU, and `1 GiB` memory. Measure startup/memory before
  increasing capacity. A single-instance maximum does not make sessions durable
  or guarantee no overlapping instances during deployments.
- Configure the startup probe for `/actuator/health` with enough startup time for
  Spring Boot and Flyway. The first successful startup applies migrations to the
  production database; it must not point at Aiven or contain development data.
- Verify startup logs, authenticated access to `/actuator/health`, migration
  history, and the SPA using an authenticated Cloud Run proxy before exposing it.
- The production Cloud SQL connection, image build, OAuth, and real load must be
  verified in GCP; local configuration tests alone do not prove deployment readiness.

### Gates before real users

See [the production follow-up list](docs/PRODUCTION-FOLLOW-UP.md) for postponed
tasks, their reasons, and remaining verification.

- **Sessions:** The `cloudrun` profile uses Spring Session JDBC with Flyway-managed
  tables and secure cookies; other profiles retain their existing session behavior.
  Test real Google and GitHub login across instances and revisions before autoscaling
  or a user launch. Deploying this change invalidates existing in-memory sessions;
  serialized session data can also require users to sign in again after upgrades or
  rollback. Protect database access because sessions include authentication data.
  Expired-session cleanup is disabled inside the web service and runs in the
  maintenance job described below.
- **Maintenance:** `cloudrun` disables the in-process referral-request scheduler.
  Development retains the existing hourly timer. A separate Cloud Run job using
  profiles `cloudrun,maintenance` runs expiry, refunds, reminders, stale-assignment
  closure, and expired-session cleanup once and exits. Trigger it hourly with an
  authenticated Cloud Scheduler job; do not make a public maintenance endpoint or
  enable the timer on request-billed, scale-to-zero instances. The job and web
  service must use compatible images and database migrations.
  Each request is locked and rechecked before changes. Failed reminder emails roll
  back their update and are retried on the next run; a retry after an uncertain SMTP
  result can send a duplicate. Job failures need monitoring and alerts: Scheduler
  can successfully start a job even if its later execution fails. Both a manual
  Scheduler trigger and the first regular hourly run started successful executions;
  failure alerts still need configuration and testing before user launch.
- **Sign-in and mail:** provision separate production Google/GitHub OAuth and SMTP
  secrets, grant access on each secret, register the actual HTTPS callbacks
  `/login/oauth2/code/google` and `/login/oauth2/code/github`, and verify delivery
  quotas. Placeholder credentials are not working sign-in. Never share secret
  values in screenshots, chat, source, or deploy-command history.
- **Operations:** test backup restore, backward-compatible migrations, rollback,
  database connection usage, and realistic concurrent traffic. Database compute,
  storage/backups, and other services can consume credit while the app is idle;
  budget alerts are not a spending cap.
  The backup restore drill is postponed: use a recent backup and restore to a
  separate temporary instance, never over the production instance. Verify the
  restored data and account for temporary instance and retained-backup costs.

### Production maintenance operations

The private Cloud Run service is `referral-portal`; the Cloud Run job is
`referral-portal-maintenance`. Cloud Scheduler job
`referral-portal-maintenance-hourly` invokes it hourly at the start of the hour
in `Asia/Kolkata`, using the dedicated
`referral-maintenance-invoker@referral-portal-production.iam.gserviceaccount.com`
identity. Scheduler and task retries are disabled; failed reminders remain due for
the next hourly run. Manually triggering the Scheduler or job can process real
production records and send real emails.

When releasing changes, update both the service and maintenance job to compatible,
tested image versions; normally use the same immutable image digest. Consider
database migration and rollback compatibility before either update. Check the
Scheduler invocation **and** the resulting Cloud Run job execution and logs: a
successful invocation only means the job was started. Configure and test alerts
for failed Scheduler triggers and failed Cloud Run job executions before launch.

## Stop services

Stop the frontend and backend with `Ctrl+C`. If you started local MySQL, stop it with:

```powershell
docker compose down
```

The database volume is preserved for the next run. Do not use `docker compose down -v` unless you intentionally want to delete local database data. Git does not transfer the database volume or uploaded resume files.

## Troubleshooting

- **Docker says Virtual Machine Platform is missing:** This only affects the local MySQL option. Run `wsl --install` in an elevated PowerShell, restart Windows, then rerun the Docker Desktop installer. If you lack administrator rights, ask your administrator for help.
- **Docker commands cannot reach the engine:** Start Docker Desktop and wait for it to finish starting. Check `docker info`.
- **Backend cannot connect to MySQL:** For Aiven, confirm the privately supplied development connection values in `.env` and check network access. For local MySQL, check `docker compose ps` and confirm `.env` uses the local database settings from `.env.example`; the host port is `3307`, not `3306`.
- **`java`, `node`, or `npm` is not recognized:** Close and reopen the terminal after installing the tool. Recheck the version commands above.
- **A port is already in use:** Defaults are MySQL `3307`, backend `8080`, and Vite `5173`. Stop the conflicting process or update the related configuration consistently.
- **OAuth or email does not work:** Configure valid provider credentials in the ignored root `.env`; never put credentials in `.env.example` or commit them.