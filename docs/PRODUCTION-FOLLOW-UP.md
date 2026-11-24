# Production follow-up list

This list records known work still to be done for the GCP production deployment.
Production uses a real database; developer testing can create real records and send
real emails. The Cloud Run web service is publicly reachable by user request for
testing and the demo. The maintenance job remains private. App API endpoints still
require sign-in.

## Recommended development-to-production workflow

The production service is public for testing. A public URL is not secret access:
others may discover it. The application requires sign-in for `/api/**`, but
sign-in alone does not establish that access is limited to the intended
developers. Review restrictions and authorization before inviting volunteers.

For each feature, develop and test it in the separate development environment,
review and commit the agreed code, then deploy a known tested image to production.
Before deployment, check database migration and rollback compatibility, backup
readiness, and whether both the web service and maintenance job need updating.
After deployment, verify the feature and monitor for errors. Production tests use
real data and may send real messages; agree on safe test accounts and cleanup.
Avoid overwriting or copying the development database into production.

## Before inviting volunteers

- [ ] **Set up and test maintenance failure alerts.** Create an email notification
  channel in Cloud Monitoring, then configure alerts for both a failed Cloud
  Scheduler invocation and a failed Cloud Run maintenance execution. Test that
  notifications actually reach the intended inbox. Also decide how to detect a
  missing scheduled run, such as a paused job or disabled API.

  **Why:** Scheduler only starts the job; a successful Scheduler request does not
  mean the job itself succeeded. If maintenance stops, expired requests and
  sessions may remain, refunds or closures may be delayed, and reminders may not
  be sent. A missing run may not produce a normal failure event. No notification
  channel or failure alert has been configured yet. Monitoring and logging usage
  can have costs beyond applicable free allowances.

  **How to start:** In the production project's Cloud Monitoring **Alerting** page,
  choose **Edit notification channels**, add an email channel, and save it. Enter
  the address only in Google Cloud; do not put it in this file or share it in chat.
  The alert conditions and a safe end-to-end notification test still need to be
  designed and verified.

- [x] **Verify a regular hourly maintenance run.** The 03:00 run on November 19,
  2026 started from `referral-portal-maintenance-hourly`, and the corresponding
  `referral-portal-maintenance-9vr9b` Cloud Run execution completed successfully.

  **Why:** A Scheduler success by itself only confirms that the job was started.
  Failure alerts and detection of missing runs remain outstanding.

- [ ] **Test restoring a backup to a separate temporary database instance.**
  Choose a recent production backup, restore it to a new temporary instance,
  verify the restored data safely, and remove only the temporary resources once
  the team agrees they are no longer needed. Never restore over production for
  this drill. Check temporary instance and retained-backup costs first.

  **Why:** A successful backup does not prove that it can be restored or that the
  team knows how to recover from data loss. This drill was explicitly postponed.
  Restored data is real user data and requires the same access protections as
  production.

- [ ] **Test application emails and review delivery capacity.** Test the actual
  referral and reminder email flows with consenting developers, review errors and
  the shared Brevo allowance, and use an authenticated team domain as the sender.

  **Why:** A direct SMTP test email arrived, but it did not test the application's
  email flows. The current temporary sender has authentication warnings, which can
  affect delivery. Development and production share the email provider's
  allowance; hitting it can delay or prevent emails. Reminder retries can
  occasionally create duplicates when delivery confirmation is uncertain.

- [ ] **Check realistic load and scaling.** Measure startup time, request
  concurrency, database connections, and maintenance duration under representative
  traffic before deciding Cloud Run instance limits and database capacity. Review
  email volume alongside traffic.

  **Why:** The number of registered users is not the same as simultaneous traffic.
  The current web service has a maximum of one instance, and each instance can
  use up to five database connections. Scaling, overlapping revisions, and the
  maintenance job can increase total connections and cost. Current settings have
  not been validated for event traffic.

- [ ] **Test real sign-in sessions across instances and revisions.** Exercise
  Google and GitHub sign-in, refreshes, and a deployment or instance change using
  consenting test accounts; check that users stay signed in as intended.

  **Why:** JDBC session persistence has been tested locally, and production OAuth
  redirects have been checked, but real cross-instance and cross-revision behavior
  has not been verified. Upgrades or rollbacks can also require users to sign in
  again if session serialization changes.

- [ ] **Review browser access for developers and volunteers.** The web service is
  now public by request; review who can sign in and what they can access. Consider
  restricted access options and their cost if the team wants developer-only access.

  **Why:** Public invocation is not the same as allowing only invited developers
  or volunteers to use the application. An unauthenticated check of `/api/me`
  returned HTTP 401, but this alone does not prove all authorization behavior.

- [ ] **Review database storage growth and cost alerts.** Decide whether to set a
  storage growth cap and configure useful storage or cost alerts. Review the
  existing budget and credit usage.

  **Why:** Cloud SQL runs even when no one uses the app, and automatic storage
  growth can increase costs. Budget alerts are notifications, not a spending cap.
  A storage cap can prevent further growth but may cause an outage if the database
  fills up, so the trade-off needs a deliberate decision.

- [ ] **Review deployment, migration, and rollback procedures.** Keep the web
  service and maintenance job on compatible tested images, check migration and
  rollback compatibility, and agree how the team's uncommitted changes will be
  reviewed and shared in Git.

  **Why:** Production currently contains changes not yet pushed to GitHub. A
  mismatched job, web image, or database schema can break maintenance or recovery.
  Do not commit or push until the team agrees to do so.

## Future features and final handover

- [ ] **Revisit chat before releasing it.** The previous chat interface is hidden;
  its legacy backend has a 20-message limit and unpaginated history.

  **Why:** This does not meet the desired unrestricted chat behavior, and large
  histories need a scalable design and load testing.

- [ ] **Update the deployment PDF and prepare the future-change checklist at the
  end of the deployment process.** Version 3 of the PDF and its editable source
  were created on November 19, 2026 at the user's request. Update them using
  available verified history, redact secrets and personal data, and clearly
  identify unavailable details or screenshots rather than inventing them.

  **Why:** The user wants a beginner-friendly record of the steps taken from the
  start, plus a separate checklist for future GCP and temporary-configuration
  changes. Version 3 is a snapshot; it does not mean the deployment is finished.

## Who does what

**You** means the project owner and team; **Assistant** means help requested in a
future session. The assistant should not make access, cost, data, or deployment
changes without the necessary approval.

| Follow-up | You and the team | Assistant |
| --- | --- | --- |
| Maintenance alerts | Privately create the notification channel when ready and confirm receipt of a test alert. | Configure and verify the agreed alert conditions and help test delivery safely. |
| Regular hourly run | No immediate action; the first regular run succeeded. | Continue checking both the Scheduler attempt and matching Cloud Run executions when investigating problems. |
| Backup restore drill | Approve the timing and temporary cost. | Explain and perform the approved restore and checks; help clean up the specific temporary resources. |
| Application emails | Provide consenting testers and arrange an authenticated team domain; enter credentials privately. | Guide and verify app flows, inspect failures without exposing private data, and review provider limits. |
| Load and scaling | Agree on realistic usage assumptions and approve any test or infrastructure costs. | Prepare a safe test plan, measure bottlenecks, and recommend capacity based on the results. |
| Sign-in sessions | Help test with consenting accounts and approve any temporary scaling. | Guide the test, inspect results, and restore temporary settings when appropriate. |
| Browser access | Decide who may access the app and approve any access or cost changes. | Compare options and costs, implement the approved option, and verify allowed and denied access. |
| Storage and cost | Decide the acceptable cost and outage trade-off. | Inspect settings, explain options, and help configure and verify approved alerts or limits. |
| Releases, migrations, and Git | Coordinate team review and approve Git and release actions. | Summarize changes, run checks, and prepare compatible releases and rollback procedures. |
| Chat | Confirm requirements and priorities with the team. | Implement and test the agreed design before release. |
| Final documents | Review the current PDF and request updates when ready. | Update the PDF at the end and create the future-change checklist using available, verified records without secrets. |
