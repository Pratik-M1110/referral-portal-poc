# Referral Portal architecture

This diagram describes the application as it exists in this repository. Solid flows are
implemented; dashed flows are optional integrations or retained capabilities. The
referral-request workflow is the primary user journey.

## System context and runtime

```mermaid
flowchart LR
    person["Seeker / referrer"]
    browser["Browser<br/>React 19 · TypeScript · React Router<br/>TanStack Query · Vite"]
    apiClient["Frontend API client<br/>JSON · session cookies · CSRF token"]
    oauth["Google / GitHub<br/>OAuth 2.0"]
    app["Spring Boot application<br/>Java 21 · Spring MVC / Security"]
    mysql[("MySQL 8<br/>Flyway-managed schema")]
    smtp["SMTP provider<br/>Brevo-compatible configuration"]

    person --> browser
    browser --> apiClient
    apiClient -->|"HTTPS / same-origin in production<br/>/api/**"| app
    browser -. "Sign-in redirect" .-> oauth
    oauth -. "OAuth callback" .-> app
    app -->|"JPA repositories · transactions"| mysql
    app -. "OTP and referral emails" .-> smtp

    subgraph dev["Development"]
        vite["Vite dev server<br/>:5173"]
        boot["Spring Boot<br/>:8080"]
        aiven[("Aiven MySQL<br/>shared development")]
        docker[("Docker Compose MySQL<br/>isolated local option<br/>host :3307 → container :3306")]
        vite -->|"Proxies /api, /logout, /actuator"| boot
        boot --> aiven
        boot -. "alternative local database" .-> docker
    end

    subgraph deploy["Planned GCP production runtime"]
        image["Multi-stage Docker build<br/>builds React assets and Spring Boot JAR"]
        one["Cloud Run service<br/>Spring Boot API + static SPA"]
        externaldb[("Cloud SQL MySQL<br/>separate production database")]
        image --> one
        one --> externaldb
    end
```

In development, Vite serves the frontend separately and proxies API calls to Spring Boot.
The backend normally connects to the shared Aiven development database; Docker Compose
MySQL is an isolated local alternative. The planned GCP production deployment uses Cloud
Run for the packaged app and Cloud SQL for a database isolated from development. The
production Dockerfile builds the frontend into Spring Boot's static resources so one
application serves both the SPA and API. Database URLs and
credentials, OAuth client credentials, and SMTP settings are environment configuration,
not source-controlled values.

## Backend structure

```mermaid
flowchart TB
    request["HTTP request"]
    security["Spring Security<br/>OAuth2 login · authenticated API · session cookie<br/>CSRF protection"]
    controller["Controllers<br/>/api/me · profile · companies · referrer<br/>referral-requests · referrals · badge"]
    service["Services<br/>identity · profiles · companies · verification<br/>request lifecycle · limits / credits · notifications"]
    dto["DTOs<br/>validated request / response models"]
    repos["Spring Data JPA repositories<br/>derived queries · custom queries / updates"]
    entities["JPA entities<br/>mapped to relational tables"]
    migration["Flyway SQL migrations<br/>V1 → V2 → V3<br/>schema source of truth"]
    db[("MySQL")]
    scheduler["Scheduled request maintenance<br/>expiry · reminders · stale assignment closure"]
    mail["SMTP sender"]
    notifications["In-app notification records"]
    legacy["Retained conversation APIs<br/>and data; UI hidden"]

    request --> security --> controller
    controller <--> dto
    controller --> service
    service --> repos
    repos <--> entities
    entities <--> db
    migration -->|"applied at startup<br/>Hibernate ddl-auto: none"| db
    scheduler --> service
    service -. "send email" .-> mail
    service --> notifications
    notifications --> repos
    controller -.-> legacy
```

API controllers delegate business rules to services. Services persist through Spring Data
JPA repositories, which map entities to the Flyway-managed MySQL schema. DTOs are the
HTTP boundary; entities are persistence models. The scheduled worker runs inside the
backend process and applies referral-request lifecycle rules.

## Active referral workflow

```mermaid
sequenceDiagram
    actor Seeker
    participant UI as React app
    participant API as Spring Boot API
    participant DB as MySQL
    participant Referrer
    participant Mail as SMTP

    Seeker->>UI: Sign in with Google or GitHub
    UI->>API: OAuth authorization and callback
    API->>DB: Find or create user and badge
    API-->>UI: Authenticated session cookie
    UI->>API: GET /api/me and profile
    Seeker->>UI: Complete profile; browse companies
    UI->>API: GET /api/companies
    API->>DB: Find companies with verified, opted-in referrers
    Seeker->>UI: Submit company + role + sharing choices
    UI->>API: POST /api/referral-requests
    API->>DB: Validate eligibility / allowance; save request and invite responses
    API->>DB: Save in-app notifications
    API-->>Mail: Invite opted-in employees by email
    Referrer->>UI: Accept or decline; choose contact-sharing
    UI->>API: POST /api/referral-requests/{id}/accept
    API->>DB: Save response and acceptance
    Referrer->>UI: Mark referral submitted through employer process
    UI->>API: POST /api/referral-requests/{id}/submitted
    API->>DB: Save submission and referral record
    Seeker->>UI: Confirm submission
    UI->>API: POST /api/referral-requests/{id}/responses/{responseId}/confirm
    API->>DB: Confirm and award eligible request credit
```

Each referral request is for one company and role; it creates a response for each eligible
opted-in employee. Referral acceptance and submission are tracked in the portal, but the
portal does not submit a candidate to the employer's hiring system. Request allowance,
opt-in, work-email verification, assignment capacity, and per-request contact-sharing
consent are enforced by backend services.

## Data domains

```mermaid
erDiagram
    USERS ||--o| PROFILES : has
    USERS ||--o| REFERRER_PROFILES : "may be"
    COMPANIES ||--o{ REFERRER_PROFILES : employs
    COMPANIES ||--o{ OPEN_ROLES : lists
    USERS ||--o{ WISHLISTS : saves
    COMPANIES ||--o{ WISHLISTS : saved_by
    USERS ||--o{ COMPANY_REFERRAL_REQUESTS : creates
    COMPANIES ||--o{ COMPANY_REFERRAL_REQUESTS : receives
    COMPANY_REFERRAL_REQUESTS ||--o{ REFERRAL_REQUEST_RESPONSES : invites
    USERS ||--o{ REFERRAL_REQUEST_RESPONSES : responds
    REFERRAL_REQUEST_RESPONSES ||--o| REFERRAL_REQUEST_CREDITS : may_earn
    USERS ||--o{ NOTIFICATIONS : receives
    USERS ||--o| BADGES : owns
    USERS ||--o{ REFERRALS : participates
    COMPANIES ||--o{ REFERRALS : associated_with

    USERS {
        bigint id PK
        string email UK
        string auth_provider
    }
    PROFILES {
        bigint user_id PK,FK
        string experience_level
        json skills
        json roles_wanted
        string phone_number
    }
    REFERRER_PROFILES {
        bigint user_id PK,FK
        bigint company_id FK
        string work_email UK
        datetime verified_at
        boolean accepts_referral_requests
    }
    COMPANIES {
        bigint id PK
        string email_domain UK
    }
    COMPANY_REFERRAL_REQUESTS {
        bigint id PK
        bigint seeker_id FK
        bigint company_id FK
        string role_title
        string status
    }
    REFERRAL_REQUEST_RESPONSES {
        bigint id PK
        bigint request_id FK
        bigint referrer_id FK
        string status
    }
    REFERRAL_REQUEST_CREDITS {
        bigint id PK
        bigint user_id FK
        bigint source_response_id FK
        datetime expires_at
    }
    WISHLISTS {
        bigint seeker_id PK,FK
        bigint company_id PK,FK
    }
    OPEN_ROLES {
        bigint id PK
        bigint company_id FK
    }
    BADGES {
        bigint user_id PK,FK
        string qr_token UK
    }
    REFERRALS {
        bigint id PK
        bigint company_id FK
        string status
    }
    NOTIFICATIONS {
        bigint id PK
        bigint user_id FK
        string type
    }
```

Other retained or reserved schema areas include `email_otps` for work-email verification;
`conversations` and `messages` for the hidden legacy chat flow; and `reviews`, `scans`,
`resume_files`, `reports_blocks`, and `credit_ledger` for capabilities that are not fully
implemented in the current UI.

## Cross-cutting behavior

- **Authentication:** Google/GitHub OAuth creates an application session. API routes
  require authentication; mutations use the CSRF token cookie/header pair.
- **Work-email verification:** an employee verifies a work email by OTP before opting in
  as a referrer. Verification and referral-request opt-in are separate actions.
- **Notifications:** referral invitations and lifecycle events are persisted as in-app
  notifications; configured SMTP is used for invitations, reminders, and verification.
- **Automation:** the backend periodically expires unanswered requests, refunds eligible
  allowance, sends reminders, and closes stale accepted assignments.
- **Operations:** Flyway applies schema changes on startup; `/actuator/health` is exposed
  for health checks. GitHub Actions runs backend tests and frontend build checks.
- **Current boundaries:** chat UI is hidden; resume upload, QR scan history, admin tools,
  and employer-system referral submission are not implemented.

