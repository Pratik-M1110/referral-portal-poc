# Referral Portal — Current MVP behavior

This document explains, flow by flow, what is actually built today: which API is called,
which service/repository handles it, and which database tables/columns are read or written.
It reflects the code as of 28 October 2026 (post Spring Data JPA migration). Capabilities not
yet available to end users are called out explicitly at the end of each section.

Stack: Spring Boot (Java) + MySQL (Flyway migrations through `V3__company_referral_requests.sql`) on the backend,
React + Vite on the frontend. Auth is session-based (Spring Security OAuth2 Client), not JWT.

The shared Aiven MySQL instance is for development use. Developers may instead use the
isolated Docker Compose MySQL database. Production will use a different database server;
connection details belong in the ignored root `.env`, never in this document.

Backend code layout: `controller/` → `service/` → `repository/` → `repository/jpa/` (the active
data-access path, built on **Spring Data JPA**), plus a consolidated `dto/` package for all
request/response records and an `entity/` package of JPA `@Entity` classes (one per table, with
`@OneToOne`/`@ManyToOne`/`@JoinColumn` annotations documenting the foreign-key relationships).
Flyway migrations remain the real source of truth for the schema (`ddl-auto: none`); Hibernate
never generates or alters DDL, it only maps onto the existing tables. All beans use `@Autowired`
field injection throughout (controllers, services, repositories).
Each REST API controller declares the common `@RequestMapping("/api")` prefix; method mappings
are relative, and the resulting public URLs remain `/api/...`. `SpaController` keeps its
unprefixed frontend page routes.

- **`entity/`** — one `@Entity` class per table (`User`, `Profile`, `Company`,
  `ReferrerProfile`, `OpenRole`, `Wishlist`, `Badge`, `Conversation`, `Message`, `Referral`,
  `CompanyReferralRequest`, `ReferralRequestResponse`, `ReferralRequestCredit`, `Review`,
  `EmailOtp`, `ResumeFile`, `ReportsBlock`, `CreditLedger`, `Notification`). Each
  maps its table via `@Table`, and FKs are modelled as real object references
  (`@ManyToOne`/`@OneToOne` + `@JoinColumn`), e.g. `Conversation.seeker`/`Conversation.referrer`
  are `User` references, not raw `Long` ids.
- **`repository/jpa/`** — one `XxxJpaRepository extends JpaRepository<Entity, Id>` per entity.
  These are the only classes that talk to Hibernate directly: simple lookups are Spring Data
  **derived query methods** (e.g. `findByEmail`, `existsBySeekerIdAndCompanyId`); anything
  needing row locking, atomic counters, or a bulk delete/update is a hand-written
  `@Query`/`@Modifying` JPQL method (e.g. `ConversationJpaRepository.incrementMessageCount`,
  `EmailOtpJpaRepository.incrementAttempts`).
- **`repository/`** (the `XxxRepository` classes referenced throughout this document) — a thin
  orchestration layer, unchanged in shape from before the JPA migration: each one is
  `@Autowired`-injected with the `XxxJpaRepository`(s) it needs and translates between JPA
  entities and the DTOs/records the service layer expects. This is also where multi-entity
  business logic that doesn't fit a single derived query lives (e.g. `ReferrerRepository`
  composing OTP + company + referrer-profile lookups for `verifyReferrer`).

Two JPA patterns worth knowing if you touch this code:
- **FK-only references use `getReferenceById(id)`, not `new Entity(); entity.setId(id)`.**
  The latter creates a plain transient object; if it's ever read back in the same request
  (e.g. `conversation.getSeeker().getName()`), Hibernate returns that same empty object
  instead of re-fetching it. `getReferenceById()` returns a real lazy-loading proxy instead.
- **Bulk JPQL updates/deletes bypass Hibernate's in-memory cache**, so anything that mutates
  rows outside the normal `save()`/`deleteById()` path adds
  `@Modifying(clearAutomatically = true, flushAutomatically = true)` so later reads in the
  same transaction see the change (used by `UserJpaRepository.deleteUserById` and
  `EmailOtpJpaRepository.incrementAttempts`/`deleteByUserId`).

---

## 1. Sign in (Google / GitHub OAuth)

**Where:** [SignIn.tsx](/d:/GDG/Referral-Portal/frontend/src/pages/SignIn.tsx) →
Spring Security OAuth2 login → [UserService.java](/d:/GDG/Referral-Portal/backend/src/main/java/com/referralportal/service/UserService.java) →
[UserRepository.java](/d:/GDG/Referral-Portal/backend/src/main/java/com/referralportal/repository/UserRepository.java)

**Flow:**
1. User clicks "Continue with Google" or "Continue with GitHub". This hits Spring Security's
   built-in `/oauth2/authorization/{google|github}` endpoint (configured in
   [SecurityConfig.java](/d:/GDG/Referral-Portal/backend/src/main/java/com/referralportal/config/SecurityConfig.java)).
2. After the provider redirects back, Spring Security builds an `OAuth2AuthenticationToken`.
   There is **no separate "register" step** — `UserService.current(auth)` is called on
   every authenticated request and does "find or create":
   - Extracts email from the OAuth profile (`emailOf`). For GitHub, if the account hides its
     email, a synthetic one is built: `{githubId}+{login}@users.noreply.github.com`.
   - Looks up `users` by email (`findIdByEmail`).
   - If no row exists, creates one.
3. **First-time sign-in** → `UserRepository.createAccount(name, email, provider, qrToken)`:
   - Builds a new `User` entity (`name`, `email`, `authProvider`, `consentAt=now`) and saves it
     via `UserJpaRepository.save()` — `consent_at` is stamped immediately (this doubles as
     DPDP consent capture).
   - A random 128-bit hex token is generated, a `Badge` entity is built referencing that user
     (via `getReferenceById`, not a raw FK), and saved via `BadgeJpaRepository.save()` —
     **every user gets a QR badge the moment their account is created**, seeker or not.
4. Session cookie is issued; the frontend calls `GET /api/me` to find out who's signed in
   ([MeService.java](/d:/GDG/Referral-Portal/backend/src/main/java/com/referralportal/service/MeService.java)):
   - `hasProfile`: does a row exist in `profiles` for this user?
   - `isReferrer`: is there a `referrer_profiles` row with `verified_at IS NOT NULL`?
   - The React router uses `hasProfile` to decide: no profile → send to **Profile** screen;
     has profile → send to **Companies**.

**Tables touched:** `users` (insert/select), `badges` (insert on first login only).

**MVP boundary:** LinkedIn sign-in is not implemented; Google and GitHub are supported.

---

## 2. Build / edit profile (seeker side)

**Where:** [Profile.tsx](/d:/GDG/Referral-Portal/frontend/src/pages/Profile.tsx) →
`PUT /api/me/profile` → [ProfileController.java](/d:/GDG/Referral-Portal/backend/src/main/java/com/referralportal/controller/ProfileController.java) →
[ProfileService.java](/d:/GDG/Referral-Portal/backend/src/main/java/com/referralportal/service/ProfileService.java) →
[ProfileRepository.java](/d:/GDG/Referral-Portal/backend/src/main/java/com/referralportal/repository/ProfileRepository.java)

**Flow:**
1. User fills in: pitch (≤200 chars), experience level (one of `student`, `fresher`, `1-3`,
   `3-6`, `6+`), skills chips, roles-wanted chips, GitHub/LinkedIn links, and the
   "hide my profile from people at my own company" checkbox (defaults **on**).
2. `ProfileService.save()` validates the experience level against a fixed set, and cleans
   the chip lists via `ChipLists.clean()` (caps skills at 12 items, roles at 6, trims/dedupes).
3. `ProfileRepository.save()` upserts the `Profile` entity (find-by-id then update, or build
   new) and saves it via `ProfileJpaRepository`:
   - `user_id` (`@Id`, also the FK to `users.id` via `@MapsId`)
   - `pitch`, `experienceLevel`
   - `skills`, `rolesWanted` — stored as **JSON arrays** (MySQL has no array type; there's a
     generated/functional index on `skills` for lookup)
  - `githubUrl`, `linkedinUrl`, and optional `phoneNumber`
   - `hideFromMyCompany` (boolean)
   - `seeker` defaults to `true` at row creation
4.  Resume upload is not implemented. The `resume_files` table and `ResumeFile` entity define
    storage for PDF bytes, but there is no upload endpoint, service/repository flow, or upload
    control in the profile UI.
5. `GET /api/me/profile` reads the row back for the edit form; if no row exists yet,
   `ProfileView.exists()` is `false` and the UI shows the "Save and continue" (create) label
   instead of "Save changes".

**Tables touched:** `profiles` (insert/update). `resume_files` is defined in the schema but is
not written by the application.

**Business rule enforced here:** wishlist and referral-request limits are enforced by their
respective company and referral services, not by profile save.

---

## 3. Work-email verification (becoming a referrer)

**Where:** Verify-email card inside [Profile.tsx](/d:/GDG/Referral-Portal/frontend/src/pages/Profile.tsx) →
`POST /api/referrer/verify/start` and `/confirm` →
[ReferrerController.java](/d:/GDG/Referral-Portal/backend/src/main/java/com/referralportal/controller/ReferrerController.java) →
[ReferrerService.java](/d:/GDG/Referral-Portal/backend/src/main/java/com/referralportal/service/ReferrerService.java) →
[ReferrerRepository.java](/d:/GDG/Referral-Portal/backend/src/main/java/com/referralportal/repository/ReferrerRepository.java)

### 3a. Start verification (`POST /api/referrer/verify/start`)
1. Lower-cases and trims the submitted email; rejects it if the domain has no `@` or matches
   a hard-coded free-mail blocklist (gmail, yahoo, outlook, hotmail, icloud, proton, gmx,
   yandex, zoho, aol, …) → `400 Use your company work email address.`
2. Rejects if that exact work email is already linked to a **different** account
   (`emailAlreadyClaimed`) → `409`.
3. Rejects if the current user is **already verified** → `409`.
4. Rate limits: max 1 OTP request per minute, max 3 requests per 15 minutes per user → `429`.
5. Generates a 6-digit numeric code, hashes it with `PasswordEncoder` (BCrypt), and saves a new
   `EmailOtp` entity (`user`, `workEmail`, `codeHash`, `expiresAt=+10 minutes`) via
   `EmailOtpJpaRepository.save()`.
6. Emails the **plaintext** code via Brevo SMTP (`JavaMailSender`). The hash, not the code,
   is what's stored — code is never logged.

### 3b. Confirm verification (`POST /api/referrer/verify/confirm`)
Runs in one DB transaction (`confirmInTransaction`):
1. Loads the latest OTP row for this user (`latestOtpForUpdate` — a JPQL query with
   `@Lock(LockModeType.PESSIMISTIC_WRITE)`, so concurrent confirm attempts can't race).
2. If no OTP row, or the email doesn't match → `EXPIRED` → `400`.
3. If `expires_at` has passed → deletes the OTP (`EmailOtpJpaRepository.deleteById`) →
   `EXPIRED` → `400`.
4. If the submitted code doesn't match the hash → increments `attempts` via a bulk
   `@Modifying` JPQL update (`clearAutomatically = true, flushAutomatically = true`, so the
   new count is visible on the next read in this same transaction); at 5 failed
   attempts, deletes all OTPs for the user (bulk `@Modifying` delete, same clear/flush
   pattern) → `429 Too many incorrect codes`.
5. If the email got claimed by someone else in the meantime → `EMAIL_IN_USE` → `409`.
6. **On success:**
   - Derives the company name from the domain (e.g. `acme.com` → `Acme`).
   - `findOrCreateCompany(domain, name)` — **this is the "no manual company onboarding" rule
     in code**: `CompanyJpaRepository.findByEmailDomain(domain)`, or if absent, a new
     `Company` entity is built and saved (the domain is a unique column) — a `companies` row
     is created automatically from the email domain.
   - `verifyReferrer(userId, companyId, workEmail)` — finds-or-builds the `ReferrerProfile`
     entity for this user (`company`, `workEmail`, `verifiedAt=now`, `referrer=true`), with
     defaults `openToMeet=false`, `slotCapacity=3`, `referralLimit=10`, and saves it.
   - Deletes all OTPs for the user (cleanup).
7. Returns the referrer `Settings` (verified, work email, company name, meeting availability,
   referral-request opt-in, role preferences, and assignment limit) so the UI can show the
   "Referrer at {Company}" chip and settings form.

### 3c. Update referrer settings (`PUT /api/referrer/settings`)
- Requires `verified_at IS NOT NULL` first (`403 Verify your work email first.`).
- Lets the referrer toggle the legacy `open_to_meet` setting and the separate
  `accepts_referral_requests` opt-in, set role preferences, and adjust the legacy
  `slot_capacity` plus active referral `referral_limit` (1–10).

**Tables touched:** `email_otps` (insert/update/delete), `companies` (insert-if-absent),
`referrer_profiles` (insert/update).

### If the user has NOT verified their work email yet — what happens?
- `referrer_profiles` has no row (or `verified_at IS NULL`) for them.
- `GET /api/me` → `isReferrer = false`. The profile shows the work-email verification form;
  referral-request opt-in controls are available only after verification.
- They can still use seeker features (company directory, wishlist, referral requests, badge,
  and their own referral status) without verifying a work email.
- They cannot opt in to receive referral requests or respond as a referrer, or change verified
  referrer settings (`PUT /api/referrer/settings` returns `403` until verified).
- There's no partial/pending state surfaced in the UI beyond the verify-email form itself —
  verification is binary (not verified → verified), not a multi-step "pending review" queue.

---

## 4. Companies list and wishlist

**Where:** [Companies.tsx](/d:/GDG/Referral-Portal/frontend/src/pages/Companies.tsx) →
`GET /api/companies`, `PUT/DELETE /api/wishlist/:companyId` →
[CompanyController.java](/d:/GDG/Referral-Portal/backend/src/main/java/com/referralportal/controller/CompanyController.java) →
[CompanyService.java](/d:/GDG/Referral-Portal/backend/src/main/java/com/referralportal/service/CompanyService.java) →
[CompanyRepository.java](/d:/GDG/Referral-Portal/backend/src/main/java/com/referralportal/repository/CompanyRepository.java)

**Listing (`GET /api/companies`):**
- Returns companies with at least one verified employee who has opted in to referral requests.
  It excludes the seeker's own verified company and does not return employee names or identities.
- Returns the seeker's wishlist count (maximum **5**) and separate company-referral request
  allowance fields: base allowance **5 per rolling 30 days** plus available earned credits.
- Marks each company `wishlisted: true/false` based on the caller's own `wishlists` rows.

**Add to wishlist (`PUT /api/wishlist/:companyId`):**
1. 404 if the company doesn't exist (`CompanyJpaRepository.existsById`).
2. 409 if the company is the user's own **verified** company
   (`isVerifiedOwnCompany` — reads the caller's `ReferrerProfile`) → "You cannot wishlist your
   own company."
3. If not already wishlisted, checks `countWishlistItems(userId) >= 5` → 409 "wishlist is full".
4. Otherwise builds a `Wishlist` entity (seeker/company set via `getReferenceById`, not raw
   ids) and saves it via `WishlistJpaRepository`.

**Remove (`DELETE /api/wishlist/:companyId`):** unconditional
`WishlistJpaRepository.deleteBySeekerIdAndCompanyId`. Both add and remove operations run in
`CompanyService` transactions so the derived delete query has an active transaction.

**Tables touched:** `companies`, `open_roles` (read-only), `referrer_profiles` (read-only),
`wishlists` (insert/delete).

---

## 5. Company referral requests

The former per-employee meeting/chat entry points are removed from the frontend. Their
controllers, endpoints, schema, and existing records are retained as legacy code. New referral
requests use a separate request/response lifecycle.

**Create (`POST /api/referral-requests`):**
- Any signed-in seeker with a profile can request a referral; work-email verification is not
  required. The seeker chooses a company and free-form role title, may add an HTTPS job URL
  and note, and explicitly consents to sharing their profile summary.
- The company must have at least one verified, opted-in referrer. A seeker cannot request help
  from their own verified company or submit another active request for the same company/role.
- One company/role request counts once, regardless of how many employee responses it creates.
  All currently opted-in employees at that company receive a pending response and email.
- The seeker's sign-in email is shared with employees who accept; it remains hidden from
  pending invitees. The seeker may choose whether to share their optional profile phone number.
- The request has a four-day response window. If nobody accepts, it expires and refunds the
  base allowance or restores the bonus credit that was consumed.

**Employee inbox and decisions (`GET /api/referral-requests/inbox`, `POST /api/referral-requests/{id}/accept|decline`):**
- Only verified employees who opted in for that company are invited and can respond. Role
  preferences are informational; they do not filter the invitation list.
- Employees see the seeker name and the profile fields the seeker consented to share, but no
  contact fields before acceptance. Each employee can accept or decline independently; several
  employees may accept one request.
- Accepting requires remaining active-assignment capacity (`referral_limit`, 1–10). The
  employee separately chooses whether to share their verified work email and profile phone.
  Each participant sees only the contact fields the other participant chose to share.
- Declines are shown as an anonymous count to the seeker; the directory never exposes employee
  identities.

**Submission, confirmation, and earned credits:**
- An accepting referrer marks the referral submitted after using their company's internal
  process (`POST /api/referral-requests/{id}/submitted`). This creates a row in `referrals`;
  the app does not submit candidates to an employer's internal system.
- The seeker confirms the submission for that specific referrer. Confirmation can award that
  referrer one bonus request credit, at most two credits per rolling 30 days. Credits expire
  after 90 days. This referral allowance is separate from the legacy meeting budget.
- Accepted but unsubmitted assignments trigger up to three work-email reminders, two days
  apart. They close on day 10 and release the referrer's active-assignment capacity.

**Tables touched:** `company_referral_requests`, `referral_request_responses`,
`referral_request_credits`, `referrer_profiles`, `profiles`, `notifications`, `referrals`,
`companies`, and `users`.

---

## 6. Badge / QR

**Where:** [Badge.tsx](/d:/GDG/Referral-Portal/frontend/src/pages/Badge.tsx) →
`GET /api/me/badge` → [BadgeController.java](/d:/GDG/Referral-Portal/backend/src/main/java/com/referralportal/controller/BadgeController.java) →
[BadgeService.java](/d:/GDG/Referral-Portal/backend/src/main/java/com/referralportal/service/BadgeService.java) →
[BadgeRepository.java](/d:/GDG/Referral-Portal/backend/src/main/java/com/referralportal/repository/BadgeRepository.java)

- The token was created once, at sign-up (§1). This endpoint just reads the current user's
  `Badge` entity (`BadgeJpaRepository.findById`) and returns `{ name, token }`.
- The frontend renders a QR code (client-side, via `qrcode.react`) encoding a URL built from
  that token — **the QR never encodes the raw user id.**
- `badges.checked_in_at` exists in the schema (for event check-in) but nothing writes to it yet.

**Tables touched:** `badges` (read-only).

**MVP boundary:** there is no scan endpoint implemented (`POST /api/scan`), so the `scans`
table is defined in the schema but nothing inserts into it. A referrer currently has no way
to scan someone else's badge to open their profile.

---

## 7. Referral tracking

The Referrals screen shows both requests sent by the current seeker and requests assigned to
the current referrer. The seeker sees pending/declined counts without the identities of
employees who declined; after acceptance they see participating referrers and only the contact
fields those referrers consented to share. Referrers have Active and History views: pending,
accepted, and submitted responses remain active, while declined, expired, confirmed, and closed
responses appear in history. The Requests for me badge counts active responses. Referrers see
their request details, consent choices, submission actions, and reminder count. Accepted
referrers can preview and copy a referral packet from consent-filtered inbox details; copying
does not submit it to the employer. The frontend polls request status every 10 seconds.

The existing `GET /api/referrals` endpoint still lists formal referral rows. A formal row is
created when an accepted employee marks a request submitted; confirmation by the seeker is
tracked separately on the response and controls the earned-credit award. No hiring-system
integration, candidate search, or resume upload is part of this flow.

---

## 8. Account deletion

**Where:** `DELETE /api/me/account` → [AccountController.java](/d:/GDG/Referral-Portal/backend/src/main/java/com/referralportal/controller/AccountController.java) →
[AccountService.java](/d:/GDG/Referral-Portal/backend/src/main/java/com/referralportal/service/AccountService.java) →
[UserRepository.java](/d:/GDG/Referral-Portal/backend/src/main/java/com/referralportal/repository/UserRepository.java)

- In one transaction: bulk `@Modifying` JPQL deletes for `conversations` (either side),
  `reviews` (either side), `referrals` (either side), then a bulk `@Modifying` JPQL delete for
  the `users` row itself (`UserJpaRepository.deleteUserById`) — deliberately **not** a plain
  `deleteById()` (load-then-remove), because `User` eagerly loads its `Badge`/`Profile`/
  `ReferrerProfile`/`ResumeFile` associations, and removing a managed entity with those
  loaded can trip a Hibernate flush-ordering check; a direct bulk delete avoids that entirely.
- Deleting `users` cascades (via `ON DELETE CASCADE` FKs) to `profiles`, `referrer_profiles`,
  `badges`, `wishlists`, `email_otps`, `resume_files`, `credit_ledger`, `notifications`,
  `reports_blocks`, company referral requests/responses/credits, and `messages` (through the
  `conversations` cascade) automatically.
  The explicit deletes above exist specifically because `conversations`/`reviews`/`referrals`
  were **not** given `ON DELETE CASCADE` (MySQL forbids a cascading FK on a column that also
  has a `CHECK` constraint, e.g. `seeker_id <> referrer_id`), so the app does that cleanup
  itself — this is covered by
  [UserRepositoryDeletionTests.java](/d:/GDG/Referral-Portal/backend/src/test/java/com/referralportal/user/UserRepositoryDeletionTests.java).

---

## 9. Admin panel

Not implemented. `users.is_admin` exists in the schema; no admin controller/service/UI exists
yet (no stats endpoint, company/sponsor management, report queue, or badge/QR bulk export).

---

## Summary table: feature vs. implementation status

| Feature | Status |
|---|---|
| Google/GitHub sign-in, auto account creation | ✅ Implemented |
| Seeker profile (pitch, skills, roles, links, hide-from-company) | ✅ Implemented |
| Resume upload (PDF, stored in DB) | ❌ Not implemented (schema/entity only) |
| Work-email OTP verification, auto company creation from domain | ✅ Implemented |
| Companies list with opted-in referrer availability, no employee names | ✅ Implemented |
| Wishlist (max 5, no self-company) | ✅ Implemented |
| Company referral requests (5 per 30 days, multiple opted-in referrers) | ✅ Implemented |
| Referral accept/decline, active assignment capacity, contact consent | ✅ Implemented |
| Submission confirmation, capped credits, expiry/refund | ✅ Implemented |
| In-app invitation notifications and capped email reminders | ✅ Implemented |
| Legacy meet/chat UI | Hidden; backend APIs/data retained |
| Badge + QR token generation/display | ✅ Implemented |
| Referrer-initiated "Invite to chat" | ⚠️ Legacy API; UI hidden |
| Scan a badge / check-in | ❌ Not implemented |
| Candidates list, Candidate card | ❌ Not implemented |
| Refer / Save / Pass actions | ❌ Not implemented |
| Seeker/referrer referral request inbox and status | ✅ Implemented |
| Copy referral packet for an accepted request | ✅ Implemented; copies the visible, consent-filtered details |
| Separate "Referrals made" dashboard and manual status updates | ❌ Not implemented |
| Block / report | ⚠️ Block is enforced in chat/requests; no report-submission UI found |
| Legacy meeting credit ledger | ⚠️ Unused; referral-request credits use separate expiring records |
| Message retention job (30-90 days) | ❌ Not implemented |
| Admin panel (stats, companies, sponsors, report queue, export) | ❌ Not implemented |
| LinkedIn sign-in | ❌ Not currently supported; Google and GitHub sign-in are available |
