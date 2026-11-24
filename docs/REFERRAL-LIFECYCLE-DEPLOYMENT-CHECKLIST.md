# Referral Lifecycle Deployment Checklist

This checklist covers the referral, resume snapshot, and assignment-timeout changes before deploying them to production.

## Release Contents

Deploy the backend and frontend from the same reviewed revision. The backend behavior depends on these Flyway migrations being present and applied in order:

- `V8__referral_resume_consent.sql` adds the per-request flag indicating whether a resume snapshot was included. Sharing is automatic when the seeker has a resume; this flag is not an opt-in choice.
- `V9__request_scoped_resume_snapshots.sql` creates `referral_request_resumes`, which stores the immutable PDF snapshot associated with each request.
- `V10__exclusive_referral_assignment.sql` adds the `accepted_elsewhere` response state used when the first referrer accepts. The matching backend prevents a second acceptance if an older request already has an active accepted/submitted/confirmed response.
- `V11__pending_password_registrations.sql` creates temporary storage for hashed email-verification challenges and password hashes used before a password account is created.

Do not edit migrations that have already been applied. Back up the production database, verify its Flyway history, and let Flyway apply pending migrations during deployment. Stop and investigate if migration validation or application fails; do not delete production Flyway history to force a retry.

The API requires an HTTPS job URL when creating a referral request. The historical `job_url` column remains nullable for compatibility with existing rows; new API requests are validated in the backend.

## Referral Behavior to Smoke-Test

Use test accounts and a non-production environment first:

- An unverified seeker starts with 2 base requests per rolling 30 days. Verifying a work email increases the base limit to 5 total, accounting for requests already used in that window. Earned credits remain separate.
- Creating a referral request without a job link or with a non-HTTPS link is rejected; a valid HTTPS link is accepted.
- Every verified referrer at the target company receives the request. The first acceptance wins; other invitees see that someone else accepted and cannot accept the same request at that time.
- Simulate an older in-flight request with one active acceptance and a pending invitee. The pending invitee must not be able to accept while the other assignment is active.
- An accepted referrer receives reminder emails at approximately 24, 48, and 72 hours. If no submission is recorded after 96 hours, the assignment times out.
- On the original request's four-day expiry, pending invitees must remain eligible if an accepted assignment is still active; the assignment timeout/continuation flow must not lose those invitees.
- If other originally invited referrers remain, the seeker sees a continuation option for 4 days. Continuing reopens the existing request only for those invitees; employees verified after the original request are not added. The endpoint must reject attempts made after the four-day decision deadline, and continuing must not charge another request.
- After continuation, test expiry with no new acceptance. The historical timed-out acceptance must not prevent the reopened attempt from expiring and refunding its allowance or consumed bonus credit.
- Verify a request whose older data already has another accepted/submitted/confirmed response does not get refunded when one assignment times out.
- If no referrers remain or the seeker does not continue before the deadline, the request expires and its base allowance or consumed bonus credit is returned.
- Verify resume access is limited to invited referrers, uses the request-time snapshot, is unavailable after decline/expiry, and is removed according to the retention policy or when the seeker deletes their resume.
- Test password registration with disposable mailboxes: starting registration must not create a `users` row; a correct, unexpired code plus the original password creates the password account; wrong/expired codes and the fifth incorrect attempt must not create one. Verify an existing OAuth email cannot be claimed, and code sending is rate-limited.

## Maintenance and Retention

The Cloud Run web profile disables the in-process scheduler. Keep the external Cloud Scheduler maintenance job enabled (hourly, or at a cadence explicitly accepted for reminder delays) and verify that it invokes the maintenance profile on the intended schedule. Maintenance handles reminder delivery, assignment timeouts, continuation-window expiry/refunds, regular request expiry, and resume snapshot cleanup. Deploy the scheduler job and web service from the same release; the job must use code compatible with V10.

Resume snapshots are retained for 30 days after a request is closed or expires by default. `RESUME_REQUEST_SNAPSHOT_RETENTION_DAYS` can override this value. Set and document the production value deliberately; confirm database storage and backup capacity can accommodate copied PDFs during the retention period.

## Rollout Compatibility

The `accepted_elsewhere` state is introduced in V10. Requests already in flight from an earlier release may have multiple accepted responses or pending responses alongside an accepted response. The updated acceptance guard rejects new acceptance if an active assignment already exists; maintenance preserves remaining invitees when the original request window expires while an assignment is active, and avoids refunds while another assignment is still active. Regression tests cover these compatibility cases.

Deploy the V10 schema and matching backend together. Keep the maintenance job on the same release so stale-assignment handling and continuation decisions use the same state rules.

## Final Checks

- Confirm production Flyway history is at V7 or earlier before applying V8-V11; if the target database already has any of these versions, verify the migration contents and checksum instead of editing history.
- Confirm the `referral_request_responses.status` enum includes `accepted_elsewhere` after V10 and that the V10 schema and matching application code deploy together.
- Confirm V11 has applied and pending password-registration records contain only password/code hashes; verify SMTP delivery before enabling password sign-up.
- Confirm production OAuth, SMTP, frontend URL, database credentials, and session-cookie settings are configured through the deployment platform’s secret/environment settings, not this file.
- Confirm the maintenance job ran successfully after deployment by checking both the Scheduler execution and matching Cloud Run execution.
- Review application logs, migration output, and notification/email delivery after the smoke test.
- Do not use real seeker resumes or personal data for deployment tests; use disposable accounts and synthetic PDFs.
