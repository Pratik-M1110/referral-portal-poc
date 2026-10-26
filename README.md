# Referral Portal

A local-development proof of concept for discovering companies, requesting referrals, and connecting with referrers.

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

The normal team development setup uses the shared Aiven development database. In the ignored root `.env`, set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` to the development values provided privately by the project maintainer. The checked-in `.env.example` contains local Docker defaults, not Aiven credentials. Never commit `.env` or put credentials in chat, issues, or documentation. The app does not automatically seed demo companies, users, or roles. Production will use a different database server, not the shared development database.

To use an isolated local database instead, keep the local database values from `.env.example` and start Docker Compose MySQL in the next section. Local Docker data is separate from Aiven and is persisted in a Docker volume.

Google/GitHub OAuth and Brevo email credentials are not included. The app can start without them, but those sign-in and email flows will not work until configured. For local OAuth, register these callback URLs with the providers:

```text
http://localhost:8080/login/oauth2/code/google
http://localhost:8080/login/oauth2/code/github
```

## Referral workflow

Anyone with a seeker profile can request a referral; work-email verification is not required to seek help. The Companies page shows companies with at least one verified employee who has opted in to referral requests, without listing employee identities. A seeker chooses a role title, may add a job link and note, and explicitly chooses which profile/contact details can be shared.

One request covers one company and role, even when multiple employees accept. Every opted-in employee at that company receives an in-app request and an email. Employees can accept or decline; accepted employees can share their own contact fields by consent, then mark the referral submitted after using their company's internal process. A seeker confirms submission in the app. Acceptance does not guarantee an opening, completed referral, or interview.

All users start with 5 requests per rolling 30 days. A submitted referral confirmed by the seeker can earn the referrer one extra request credit, up to 2 credits per 30 days; credits expire after 90 days. Requests with no acceptance expire after 4 days and return one request to the seeker's allowance. Accepted assignments receive up to 3 email reminders at 2-day intervals and close after day 10 if left unsubmitted.

Referrers must verify a work email, then separately opt in to receive requests. They can pause the opt-in, choose role-help preferences (informational only; requests still go to all opted-in employees), and set a cap of 1-10 active accepted assignments. Optional phone numbers are saved in the profile and shared only when each participant consents for that request. Resume upload is not implemented.

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