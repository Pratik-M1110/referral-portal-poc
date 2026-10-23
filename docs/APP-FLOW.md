# Referral Portal — How the app works (current implementation)

This document explains, flow by flow, what is actually built today: which API is called,
which service/repository handles it, and which database tables/columns are read or written.
It reflects the code as of late October 2026 (post Spring Data JPA migration), not the full
product brief — gaps are called out explicitly at the end of each section.

Stack: Spring Boot (Java) + MySQL (Flyway migration `V1__init.sql`) on the backend,
React + Vite on the frontend. Auth is session-based (Spring Security OAuth2 Client), not JWT.

Backend code layout: `controller/` → `service/` → `repository/` → `repository/jpa/` (the active
data-access path, built on **Spring Data JPA**), plus a consolidated `dto/` package for all
request/response records and an `entity/` package of JPA `@Entity` classes (one per table, with
`@OneToOne`/`@ManyToOne`/`@JoinColumn` annotations documenting the foreign-key relationships).
Flyway migrations remain the real source of truth for the schema (`ddl-auto: none`); Hibernate
never generates or alters DDL, it only maps onto the existing tables. All beans use `@Autowired`
field injection throughout (controllers, services, repositories).

- **`entity/`** — one `@Entity` class per table (`User`, `Profile`, `Company`,
  `ReferrerProfile`, `OpenRole`, `Wishlist`, `Badge`, `Conversation`, `Message`, `Referral`,
  `Review`, `EmailOtp`, `ResumeFile`, `ReportsBlock`, `CreditLedger`, `Notification`). Each
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
   - If `app.seed-demo=true` (local/dev only), `DemoDataSeeder.seedWelcome(id)` populates a
     starter inbox (sample chat, pending invite, two referrals) so a fresh demo account
     doesn't look empty. The seeder itself is now also JPA-based (`UserJpaRepository`,
     `ConversationJpaRepository`, `MessageJpaRepository`, `ReferralJpaRepository`, etc.),
     not raw SQL.
4. Session cookie is issued; the frontend calls `GET /api/me` to find out who's signed in
   ([MeService.java](/d:/GDG/Referral-Portal/backend/src/main/java/com/referralportal/service/MeService.java)):
   - `hasProfile`: does a row exist in `profiles` for this user?
   - `isReferrer`: is there a `referrer_profiles` row with `verified_at IS NOT NULL`?
   - The React router uses `hasProfile` to decide: no profile → send to **Profile** screen;
     has profile → send to **Companies**.

**Tables touched:** `users` (insert/select), `badges` (insert on first login only).

**Gaps vs. brief:** LinkedIn sign-in is not implemented (Google/GitHub only, matches MVP-1 scope).

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
   - `githubUrl`, `linkedinUrl`
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

**Business rule enforced here:** none of the wishlist/request limits — those are enforced
where they're used (Companies/Conversations), not on the profile itself.

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
7. Returns the referrer `Settings` (verified, work email, company name, open-to-meet,
   slot capacity, referral limit) so the UI can immediately show the "Referrer at {Company}"
   chip and settings form.

### 3c. Update referrer settings (`PUT /api/referrer/settings`)
- Requires `verified_at IS NOT NULL` first (`403 Verify your work email first.`).
- Lets the referrer toggle `open_to_meet` and adjust `slot_capacity` / `referral_limit`
  within bounds set in the UI (1–10).

**Tables touched:** `email_otps` (insert/update/delete), `companies` (insert-if-absent),
`referrer_profiles` (insert/update).

### If the user has NOT verified their work email yet — what happens?
- `referrer_profiles` has no row (or `verified_at IS NULL`) for them.
- `GET /api/me` → `isReferrer = false`. The **mode toggle** ("Find opportunities" / "Refer
  people") and the **Badge header link in referrer mode** simply don't render — there is
  nothing to switch to.
- They can still use every seeker feature (Companies, wishlist, meet requests, chat, badge,
  referrals-received) normally.
- They **cannot**: appear in any company's "employees open to meet" list, be scanned as a
  referrer, accept/decline meet requests as a referrer, or change referrer settings
  (`PUT /api/referrer/settings` returns `403` until verified).
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
- `CompanyJpaRepository.findAllByOrderBySponsorDescNameAsc()` for the company list, with
  `OpenRoleJpaRepository.findByCompanyIdOrderById()` (role counts/views) and
  `ReferrerProfileJpaRepository.findAll()` filtered in-memory to verified, `openToMeet=true`
  profiles, to list open-to-meet employees per company.
- Computes `wishlistCount` (rows in `wishlists` for this user, via `WishlistJpaRepository`) vs.
  max **5**, and `requestsLeft` via `RequestBudget.left()` (see §5) vs. max **3**.
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
`WishlistJpaRepository.deleteBySeekerIdAndCompanyId`.

**Tables touched:** `companies`, `open_roles` (read-only), `referrer_profiles` (read-only),
`wishlists` (insert/delete).

---

## 5. Meet requests → accept/decline → chat

Already covered in depth in earlier answers, repeated here for completeness as part of the
unified flow. See
[ConversationService.java](/d:/GDG/Referral-Portal/backend/src/main/java/com/referralportal/service/ConversationService.java),
[ConversationRepository.java](/d:/GDG/Referral-Portal/backend/src/main/java/com/referralportal/repository/ConversationRepository.java).

**Request budget:** `RequestBudget.left(seekerId)` = `3 + SUM(credit_ledger.delta) − count of
conversations this person started as seeker`. `credit_ledger` exists in the schema for V1.5
("earn +1 request") but nothing writes to it yet, so it's effectively always `3 − used`.

**Send a request (`POST /api/conversations`):**
1. Can't request yourself.
2. Target must be a **verified, `open_to_meet=TRUE`** referrer (`findOpenReferrer`) → else 404.
3. Can't request your own verified company (`hasVerifiedCompany`) → 409.
4. Can't request a blocked person (checks `reports_blocks` both directions) → 403.
5. Target must not be fully booked: `countAcceptedForReferrer(referrerId) < slot_capacity` → else 409.
6. Caller must have budget left (`RequestBudget.left() > 0`) → else 409.
7. In one transaction: a `Conversation` entity is built (seeker/referrer set via
   `getReferenceById`, `initiatedBy='seeker'`, `firstMessage=note`, `status='pending'`) and
   saved via `ConversationJpaRepository.save()`. A `UNIQUE (seeker_id, referrer_id)` constraint
   means **one conversation per pair, ever** — a duplicate request now throws
   `DataIntegrityViolationException` → 409 "You already have a conversation with this person."
8. (Dev-only) if `app.seed-demo=true` and the target is a seeded demo referrer, the
   conversation is auto-accepted with a canned first message, so demo data looks alive.

**Accept/decline (`POST /api/conversations/:id/accept|decline`):**
- Only the **non-initiator** can decide, and only while `status='pending'`.
- Accept additionally re-checks slot capacity at the moment of accepting (so a referrer can't
  over-accept if they already filled slots from other concurrent requests).
- A bulk `@Modifying` JPQL update — `update Conversation set status=:status where id=:id and
  status='pending'` — the `status='pending'` guard makes this safe against double-submits.

**Chat (`GET/POST /api/conversations/:id/messages`):**
- Only works once `status='accepted'`, and if the pair isn't blocked.
- Each send runs `ConversationJpaRepository.incrementMessageCount` — a bulk `@Modifying` JPQL
  update (`update Conversation set messageCount=messageCount+1 where id=:id and
  status='accepted' and messageCount<20`) that returns the affected-row count; 0 rows means
  409 ("used all 20 messages"); otherwise the `Message` entity (with the conversation set via
  `getReferenceById`) is inserted in the **same transaction**. This is what makes the
  20-message cap atomic even with two people typing simultaneously.
- Frontend polls `GET /api/conversations` every 5s and `GET /.../messages` every 4s (no
  WebSockets).

**Tables touched:** `conversations`, `messages`, `reports_blocks` (read), `referrer_profiles`
(read), `credit_ledger` (read only, unused for writes today).

**Gap vs. brief:** the referrer-initiated "Invite to chat" button (which should set
`initiated_by='referrer'` and not consume the seeker's budget) has the schema support but no
endpoint/service code yet — every request today is seeker-initiated.

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

**Gap vs. brief:** there is no scan endpoint implemented (`POST /api/scan`), so the `scans`
table is defined in the schema but nothing inserts into it. A referrer currently has no way
to scan someone else's badge to open their profile.

---

## 7. Referrals (received / "My referrals")

**Where:** [Referrals.tsx](/d:/GDG/Referral-Portal/frontend/src/pages/Referrals.tsx) →
`GET /api/referrals?as=candidate` →
[ReferralController.java](/d:/GDG/Referral-Portal/backend/src/main/java/com/referralportal/controller/ReferralController.java) →
[ReferralService.java](/d:/GDG/Referral-Portal/backend/src/main/java/com/referralportal/service/ReferralService.java) →
[ReferralRepository.java](/d:/GDG/Referral-Portal/backend/src/main/java/com/referralportal/repository/ReferralRepository.java)

- Reads `referrals` via `ReferralJpaRepository`, filtered to `candidate_id = currentUser`,
  ordered by `updated_at DESC`, with `company`, `referrer` (a `User`) and `role` (an
  `OpenRole`, nullable) resolved through the entity's `@ManyToOne` associations rather than a
  hand-written SQL join.
- Shown fields: company name, referrer name, role title (nullable), status
  (`submitted|under_review|interview|closed`), last updated time.
- Polled every 10s.

**Tables touched:** `referrals` (read-only), joined with `companies`/`users`/`open_roles`.

**Gap vs. brief — this is the big one:** there is **no `POST /api/referrals`, no Scan tab, no
Candidates list, no Candidate card, and no Refer/Save/Pass buttons** in the code. The
`reviews` table (Save/Pass + private note) and the referral-creation path described in the
brief (§3 rule 9) are not implemented. Every row currently visible in "My referrals" comes
from `DemoDataSeeder` test data, not a live user action. Referrers also have no "Referrals
made" screen, no referral_limit enforcement in practice, and no "Copy referral packet" /
status-update UI yet.

---

## 8. Account deletion

**Where:** `DELETE /api/account` → [AccountController.java](/d:/GDG/Referral-Portal/backend/src/main/java/com/referralportal/controller/AccountController.java) →
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
  `reports_blocks`, and `messages` (through the `conversations` cascade) automatically.
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
| Companies list, open-role counts, open-to-meet employees | ✅ Implemented |
| Wishlist (max 5, no self-company) | ✅ Implemented |
| Meet requests (budget 3, no self-company, capacity check) | ✅ Implemented |
| Accept/decline, slot capacity re-check | ✅ Implemented |
| Chat, 20-message atomic cap, polling | ✅ Implemented |
| Contact-sharing banner | ✅ Implemented |
| Badge + QR token generation/display | ✅ Implemented |
| Referrer-initiated "Invite to chat" | ⚠️ Schema ready, not wired up |
| Scan a badge / check-in | ❌ Not implemented |
| Candidates list, Candidate card | ❌ Not implemented |
| Refer / Save / Pass actions | ❌ Not implemented |
| "My referrals" (view only) | ✅ Implemented (read-only; demo data only) |
| "Referrals made", copy packet, status update | ❌ Not implemented |
| Block / report | ⚠️ Block is enforced in chat/requests; no report-submission UI found |
| Credit ledger (+1 request) | ⚠️ Schema + read path only, nothing writes |
| Message retention job (30-90 days) | ❌ Not implemented |
| Admin panel (stats, companies, sponsors, report queue, export) | ❌ Not implemented |
| LinkedIn sign-in | ❌ Not implemented (not required for MVP-1) |
