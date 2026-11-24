package com.referralportal.referral;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.referralportal.dto.ReferralRequestDtos.AcceptRequest;
import com.referralportal.dto.ReferralRequestDtos.CreateRequest;
import com.referralportal.dto.ReferralRequestDtos.ReferrerInboxItem;
import com.referralportal.dto.ReferralRequestDtos.SeekerRequest;
import com.referralportal.service.CompanyReferralRequestService;
import com.referralportal.service.CompanyService;
import com.referralportal.service.ReferralRequestBudget;
import com.referralportal.service.ReferralRequestScheduler;
import com.referralportal.service.ResumeService;
import com.referralportal.repository.jpa.ReferralRequestResponseJpaRepository;
import jakarta.validation.Validator;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-google-client",
        "spring.security.oauth2.client.registration.google.client-secret=test-google-secret",
        "spring.security.oauth2.client.registration.github.client-id=test-github-client",
        "spring.security.oauth2.client.registration.github.client-secret=test-github-secret",
        "spring.datasource.url=jdbc:mysql://localhost:3307/referral_portal?allowPublicKeyRetrieval=true&useSSL=false",
        "spring.datasource.username=referral",
        "spring.datasource.password=referral_local"
})
@Transactional
class CompanyReferralRequestFlowTests {

    @Autowired
    private CompanyReferralRequestService requests;

        @Autowired
        private Validator validator;

        @Autowired
        private CompanyService companyService;

        @Autowired
        private CompanyService companies;

        @Autowired
        private ReferralRequestBudget budget;

        @Autowired
        private ReferralRequestScheduler scheduler;

        @Autowired
        private ResumeService resumes;

        @Autowired
        private ReferralRequestResponseJpaRepository responses;

    @Autowired
    private JdbcClient jdbc;

        @Autowired
        private EntityManager entityManager;

    @MockitoBean
    private JavaMailSender mailSender;

    @Test
        void firstVerifiedReferrerAcceptanceLocksOutOtherReferrers() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String seekerEmail = "seeker-" + suffix + "@example.test";
        String firstReferrerEmail = "first-" + suffix + "@example.test";
        String secondReferrerEmail = "second-" + suffix + "@example.test";
        long seekerId = insertUser("Seeker", seekerEmail);
        long firstReferrerId = insertUser("First Referrer", firstReferrerEmail);
        long secondReferrerId = insertUser("Second Referrer", secondReferrerEmail);
        long companyId = insertCompany(suffix);
        insertProfile(seekerId, "+1 555 010 1234");
        insertProfile(firstReferrerId, "+1 555 010 5678");
        insertProfile(secondReferrerId, "+1 555 010 9012");
        jdbc.sql("UPDATE profiles SET github_url = 'https://github.com/seeker', linkedin_url = 'https://linkedin.com/in/seeker', portfolio_url = 'https://seeker.example.test/portfolio' WHERE user_id = ?")
                .param(seekerId).update();
        insertReferrer(firstReferrerId, companyId, "first-work-" + suffix + "@company.test");
        insertReferrer(secondReferrerId, companyId, "second-work-" + suffix + "@company.test");

        SeekerRequest request = requests.create(authentication(seekerEmail, "Seeker"),
                new CreateRequest(companyId, "Backend Engineer", "https://jobs.example.test/backend", "Please consider my profile.",
                        true, true));

        assertEquals(2, request.pendingCount());
        assertEquals(2, count("referral_request_responses", "request_id = ?", request.id()));
        ReferrerInboxItem firstInbox = inboxItem(requests.inbox(authentication(firstReferrerEmail, "First Referrer")));
        assertEquals("https://github.com/seeker", firstInbox.githubUrl());
        assertEquals("https://linkedin.com/in/seeker", firstInbox.linkedinUrl());
        assertEquals("https://seeker.example.test/portfolio", firstInbox.portfolioUrl());
        assertNull(firstInbox.seekerEmail());
        assertNull(firstInbox.seekerPhone());
        long firstResponseId = firstInbox.responseId();

        requests.accept(authentication(firstReferrerEmail, "First Referrer"), request.id(), new AcceptRequest(true, true));
        assertEquals(seekerEmail, inboxItem(requests.inbox(authentication(firstReferrerEmail, "First Referrer")))
                .seekerEmail());
        ReferrerInboxItem otherInbox = inboxItem(requests.inbox(authentication(secondReferrerEmail, "Second Referrer")));
        assertEquals("accepted_elsewhere", otherInbox.status());
        assertNull(otherInbox.seekerEmail());
        ResponseStatusException alreadyAccepted = assertThrows(ResponseStatusException.class,
                () -> requests.accept(authentication(secondReferrerEmail, "Second Referrer"), request.id(), new AcceptRequest(false, false)));
        assertEquals(HttpStatus.CONFLICT, alreadyAccepted.getStatusCode());
        SeekerRequest afterAccept = requests.seekerRequests(authentication(seekerEmail, "Seeker")).getFirst();
        assertEquals(1, afterAccept.responses().size());
        var sharedResponse = afterAccept.responses().stream()
                .filter(response -> response.referrerEmail() != null).findFirst().orElseThrow();
        assertEquals("first-work-" + suffix + "@company.test", sharedResponse.referrerEmail());
        assertEquals("+1 555 010 5678", sharedResponse.referrerPhone());

        requests.markSubmitted(authentication(firstReferrerEmail, "First Referrer"), request.id());
        requests.confirmSubmission(authentication(seekerEmail, "Seeker"), request.id(), firstResponseId);

        assertEquals(1, count("referral_request_credits", "user_id = ?", firstReferrerId));
        assertEquals(1, count("referrals", "referrer_id = ? AND candidate_id = ? AND company_id = ?",
                firstReferrerId, seekerId, companyId));
    }

    @Test
    void legacyPendingInviteCannotAcceptWhenAnotherResponseIsAlreadyActive() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String seekerEmail = "legacy-seeker-" + suffix + "@example.test";
        String activeReferrerEmail = "legacy-active-" + suffix + "@example.test";
        String pendingReferrerEmail = "legacy-pending-" + suffix + "@example.test";
        long activeReferrerId = insertUser("Active Referrer", activeReferrerEmail);
        long pendingReferrerId = insertUser("Pending Referrer", pendingReferrerEmail);
        long companyId = insertCompany(suffix);
        insertUser("Legacy Seeker", seekerEmail);
        insertProfile(jdbc.sql("SELECT id FROM users WHERE email = ?").param(seekerEmail).query(Long.class).single(), null);
        insertProfile(activeReferrerId, null);
        insertProfile(pendingReferrerId, null);
        insertReferrer(activeReferrerId, companyId, "legacy-active-work-" + suffix + "@company.test");
        insertReferrer(pendingReferrerId, companyId, "legacy-pending-work-" + suffix + "@company.test");

        SeekerRequest request = requests.create(authentication(seekerEmail, "Legacy Seeker"),
                new CreateRequest(companyId, "Engineer", "https://jobs.example.test/engineer", "", true, false));
        requests.accept(authentication(activeReferrerEmail, "Active Referrer"), request.id(), new AcceptRequest(false, false));
        entityManager.flush();
        jdbc.sql("UPDATE referral_request_responses SET status = 'pending', closed_at = NULL "
                + "WHERE request_id = ? AND referrer_id = ?").params(request.id(), pendingReferrerId).update();
        entityManager.clear();

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> requests.accept(authentication(pendingReferrerEmail, "Pending Referrer"), request.id(),
                        new AcceptRequest(false, false)));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(1, count("referral_request_responses", "request_id = ? AND status IN ('accepted', 'submitted', 'confirmed')",
                request.id()));
    }

    @Test
    void seekerCanContinueWithRemainingReferrersAfterFourDayAssignmentTimeout() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String seekerEmail = "continue-seeker-" + suffix + "@example.test";
        String firstEmail = "continue-first-" + suffix + "@example.test";
        String secondEmail = "continue-second-" + suffix + "@example.test";
        String thirdEmail = "continue-third-" + suffix + "@example.test";
        long seekerId = insertUser("Continue Seeker", seekerEmail);
        long firstReferrerId = insertUser("First Referrer", firstEmail);
        long secondReferrerId = insertUser("Second Referrer", secondEmail);
        long thirdReferrerId = insertUser("Third Referrer", thirdEmail);
        long companyId = insertCompany(suffix);
        insertProfile(seekerId, null);
        insertProfile(firstReferrerId, null);
        insertProfile(secondReferrerId, null);
        insertProfile(thirdReferrerId, null);
        insertReferrer(firstReferrerId, companyId, "continue-first-work-" + suffix + "@company.test");
        insertReferrer(secondReferrerId, companyId, "continue-second-work-" + suffix + "@company.test");

        SeekerRequest request = requests.create(authentication(seekerEmail, "Continue Seeker"),
                new CreateRequest(companyId, "Engineer", "https://jobs.example.test/engineer", "", true, false));
        insertReferrer(thirdReferrerId, companyId, "continue-third-work-" + suffix + "@company.test");
        requests.accept(authentication(firstEmail, "First Referrer"), request.id(), new AcceptRequest(false, false));
        entityManager.flush();
        jdbc.sql("UPDATE referral_request_responses SET status = 'pending' "
                + "WHERE request_id = ? AND referrer_id = ?").params(request.id(), secondReferrerId).update();
        jdbc.sql("UPDATE referral_request_responses SET accepted_at = DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 5 DAY) "
                + "WHERE request_id = ? AND referrer_id = ?").params(request.id(), firstReferrerId).update();
        jdbc.sql("UPDATE company_referral_requests SET expires_at = DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 MINUTE) "
                + "WHERE id = ?").param(request.id()).update();
        entityManager.clear();

        scheduler.processDueRequests();

        SeekerRequest awaiting = requests.seekerRequests(authentication(seekerEmail, "Continue Seeker")).getFirst();
        assertEquals("awaiting_seeker", awaiting.status());
        assertTrue(awaiting.canContinueWithOtherReferrers());
        assertEquals(1, awaiting.otherReferrerCount());
        assertEquals("pending", responses.findByRequestIdAndReferrerId(request.id(), secondReferrerId)
                .orElseThrow().getStatus());

        requests.continueWithOtherReferrers(authentication(seekerEmail, "Continue Seeker"), request.id());
        assertEquals("pending", inboxItem(requests.inbox(authentication(secondEmail, "Second Referrer"))).status());
        assertTrue(requests.inbox(authentication(thirdEmail, "Third Referrer")).stream()
                .noneMatch(item -> item.requestId() == request.id()));
        requests.accept(authentication(secondEmail, "Second Referrer"), request.id(), new AcceptRequest(false, false));
        assertEquals("accepted", responses.findByRequestIdAndReferrerId(request.id(), secondReferrerId).orElseThrow().getStatus());
    }

    @Test
    void refundsAllowanceWhenTimedOutReferrerHasNoOtherReferrersToContinueWith() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String seekerEmail = "refund-timeout-seeker-" + suffix + "@example.test";
        String referrerEmail = "refund-timeout-referrer-" + suffix + "@example.test";
        long seekerId = insertUser("Refund Timeout Seeker", seekerEmail);
        long referrerId = insertUser("Only Referrer", referrerEmail);
        long companyId = insertCompany(suffix);
        insertProfile(seekerId, null);
        insertProfile(referrerId, null);
        insertReferrer(referrerId, companyId, "only-work-" + suffix + "@company.test");

        SeekerRequest request = requests.create(authentication(seekerEmail, "Refund Timeout Seeker"),
                new CreateRequest(companyId, "Engineer", "https://jobs.example.test/engineer", "", true, false));
        requests.accept(authentication(referrerEmail, "Only Referrer"), request.id(), new AcceptRequest(false, false));
        entityManager.flush();
        jdbc.sql("UPDATE referral_request_responses SET accepted_at = DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 5 DAY) "
                + "WHERE request_id = ?").param(request.id()).update();
        jdbc.sql("UPDATE company_referral_requests SET expires_at = DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 MINUTE) "
                + "WHERE id = ?").param(request.id()).update();
        entityManager.clear();

        scheduler.processDueRequests();

        SeekerRequest expired = requests.seekerRequests(authentication(seekerEmail, "Refund Timeout Seeker")).getFirst();
        assertEquals("expired", expired.status());
        assertTrue(expired.creditReturned());
        assertEquals(2, budget.allowance(seekerId, Instant.now()).left());
    }

    @Test
    void refundsAllowanceWhenSeekerDoesNotContinueWithinFourDays() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String seekerEmail = "decision-seeker-" + suffix + "@example.test";
        String firstEmail = "decision-first-" + suffix + "@example.test";
        String secondEmail = "decision-second-" + suffix + "@example.test";
        long seekerId = insertUser("Decision Seeker", seekerEmail);
        long firstReferrerId = insertUser("First Decision Referrer", firstEmail);
        long secondReferrerId = insertUser("Second Decision Referrer", secondEmail);
        long companyId = insertCompany(suffix);
        insertProfile(seekerId, null);
        insertProfile(firstReferrerId, null);
        insertProfile(secondReferrerId, null);
        insertReferrer(firstReferrerId, companyId, "decision-first-work-" + suffix + "@company.test");
        insertReferrer(secondReferrerId, companyId, "decision-second-work-" + suffix + "@company.test");

        SeekerRequest request = requests.create(authentication(seekerEmail, "Decision Seeker"),
                new CreateRequest(companyId, "Engineer", "https://jobs.example.test/engineer", "", true, false));
        requests.accept(authentication(firstEmail, "First Decision Referrer"), request.id(), new AcceptRequest(false, false));
        entityManager.flush();
        jdbc.sql("UPDATE referral_request_responses SET accepted_at = DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 5 DAY) "
                + "WHERE request_id = ? AND referrer_id = ?").params(request.id(), firstReferrerId).update();
        jdbc.sql("UPDATE company_referral_requests SET expires_at = DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 MINUTE) "
                + "WHERE id = ?").param(request.id()).update();
        entityManager.clear();

        scheduler.processDueRequests();
        assertTrue(requests.seekerRequests(authentication(seekerEmail, "Decision Seeker")).getFirst()
                .canContinueWithOtherReferrers());

        jdbc.sql("UPDATE company_referral_requests SET closed_at = DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 5 DAY) "
                + "WHERE id = ?").param(request.id()).update();
        entityManager.clear();
        ResponseStatusException lateContinue = assertThrows(ResponseStatusException.class,
                () -> requests.continueWithOtherReferrers(authentication(seekerEmail, "Decision Seeker"), request.id()));
        assertEquals(HttpStatus.CONFLICT, lateContinue.getStatusCode());
        scheduler.processDueRequests();

        SeekerRequest expired = requests.seekerRequests(authentication(seekerEmail, "Decision Seeker")).getFirst();
        assertEquals("expired", expired.status());
        assertTrue(expired.creditReturned());
        assertEquals(2, budget.allowance(seekerId, Instant.now()).left());
    }

    @Test
    void expiresUnacceptedContinuationAndRefundsDespiteHistoricalAcceptance() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String seekerEmail = "continuation-refund-seeker-" + suffix + "@example.test";
        String firstEmail = "continuation-refund-first-" + suffix + "@example.test";
        String secondEmail = "continuation-refund-second-" + suffix + "@example.test";
        long seekerId = insertUser("Continuation Refund Seeker", seekerEmail);
        long firstReferrerId = insertUser("First Referrer", firstEmail);
        long secondReferrerId = insertUser("Second Referrer", secondEmail);
        long companyId = insertCompany(suffix);
        insertProfile(seekerId, null);
        insertProfile(firstReferrerId, null);
        insertProfile(secondReferrerId, null);
        insertReferrer(firstReferrerId, companyId, "continuation-first-work-" + suffix + "@company.test");
        insertReferrer(secondReferrerId, companyId, "continuation-second-work-" + suffix + "@company.test");

        SeekerRequest request = requests.create(authentication(seekerEmail, "Continuation Refund Seeker"),
                new CreateRequest(companyId, "Engineer", "https://jobs.example.test/engineer", "", true, false));
        requests.accept(authentication(firstEmail, "First Referrer"), request.id(), new AcceptRequest(false, false));
        entityManager.flush();
        jdbc.sql("UPDATE referral_request_responses SET accepted_at = DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 5 DAY) "
                + "WHERE request_id = ? AND referrer_id = ?").params(request.id(), firstReferrerId).update();
        jdbc.sql("UPDATE company_referral_requests SET expires_at = DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 MINUTE) "
                + "WHERE id = ?").param(request.id()).update();
        entityManager.clear();

        scheduler.processDueRequests();
        requests.continueWithOtherReferrers(authentication(seekerEmail, "Continuation Refund Seeker"), request.id());
        entityManager.flush();

        jdbc.sql("UPDATE company_referral_requests SET expires_at = DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 MINUTE) "
                + "WHERE id = ?").param(request.id()).update();
        entityManager.clear();
        scheduler.processDueRequests();

        SeekerRequest expired = requests.seekerRequests(authentication(seekerEmail, "Continuation Refund Seeker")).getFirst();
        assertEquals("expired", expired.status());
        assertTrue(expired.creditReturned());
        assertEquals(2, budget.allowance(seekerId, Instant.now()).left());
    }

    @Test
    void historicalMultipleAcceptanceDoesNotRefundWhileAnotherAssignmentIsActive() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String seekerEmail = "legacy-active-seeker-" + suffix + "@example.test";
        String firstEmail = "legacy-active-first-" + suffix + "@example.test";
        String secondEmail = "legacy-active-second-" + suffix + "@example.test";
        long seekerId = insertUser("Legacy Active Seeker", seekerEmail);
        long firstReferrerId = insertUser("First Referrer", firstEmail);
        long secondReferrerId = insertUser("Second Referrer", secondEmail);
        long companyId = insertCompany(suffix);
        insertProfile(seekerId, null);
        insertProfile(firstReferrerId, null);
        insertProfile(secondReferrerId, null);
        insertReferrer(firstReferrerId, companyId, "legacy-first-work-" + suffix + "@company.test");
        insertReferrer(secondReferrerId, companyId, "legacy-second-work-" + suffix + "@company.test");

        SeekerRequest request = requests.create(authentication(seekerEmail, "Legacy Active Seeker"),
                new CreateRequest(companyId, "Engineer", "https://jobs.example.test/engineer", "", true, false));
        requests.accept(authentication(firstEmail, "First Referrer"), request.id(), new AcceptRequest(false, false));
        entityManager.flush();
        jdbc.sql("UPDATE referral_request_responses SET status = 'accepted', accepted_at = CURRENT_TIMESTAMP "
                + "WHERE request_id = ? AND referrer_id = ?").params(request.id(), secondReferrerId).update();
        jdbc.sql("UPDATE referral_request_responses SET accepted_at = DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 5 DAY) "
                + "WHERE request_id = ? AND referrer_id = ?").params(request.id(), firstReferrerId).update();
        jdbc.sql("UPDATE company_referral_requests SET expires_at = DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 MINUTE) "
                + "WHERE id = ?").param(request.id()).update();
        entityManager.clear();

        scheduler.processDueRequests();

        assertEquals("accepted", responses.findByRequestIdAndReferrerId(request.id(), secondReferrerId)
                .orElseThrow().getStatus());
        assertEquals(1, budget.allowance(seekerId, Instant.now()).left());
    }

        @Test
        void resumeUploadRejectsMissingAndNonPdfFiles() {
                String seekerEmail = "resume-test-" + UUID.randomUUID().toString().replace("-", "") + "@example.test";
                insertUser("Resume Seeker", seekerEmail);

                assertThrows(ResponseStatusException.class,
                        () -> resumes.upload(authentication(seekerEmail, "Resume Seeker"), null));

                MockMultipartFile notPdf = new MockMultipartFile("file", "resume.txt", "text/plain", "not a pdf".getBytes());
                ResponseStatusException nonPdf = assertThrows(ResponseStatusException.class,
                        () -> resumes.upload(authentication(seekerEmail, "Resume Seeker"), notPdf));
                assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, nonPdf.getStatusCode());
        }

        @Test
        void resumeIsAutomaticallyAvailableToInvitedReferrersBeforeAcceptance() {
                String suffix = UUID.randomUUID().toString().replace("-", "");
                String seekerEmail = "resume-seeker-" + suffix + "@example.test";
                String referrerEmail = "resume-referrer-" + suffix + "@example.test";
                long seekerId = insertUser("Resume Seeker", seekerEmail);
                long referrerId = insertUser("Resume Referrer", referrerEmail);
                long companyId = insertCompany(suffix);
                insertProfile(seekerId, null);
                insertReferrer(referrerId, companyId, "resume-work-" + suffix + "@company.test");

                byte[] pdf = new byte[]{'%', 'P', 'D', 'F', '-', '1', '.', '7'};
                resumes.upload(authentication(seekerEmail, "Resume Seeker"),
                        new MockMultipartFile("file", "candidate.pdf", "application/pdf", pdf));

                SeekerRequest request = requests.create(authentication(seekerEmail, "Resume Seeker"),
                        new CreateRequest(companyId, "Backend Engineer", "https://jobs.example.test/backend", "", true, false));

                assertTrue(requests.inbox(authentication(referrerEmail, "Resume Referrer")).stream()
                        .filter(item -> item.requestId() == request.id()).findFirst().orElseThrow().resumeAvailable());
                assertEquals("candidate.pdf", resumes.downloadForReferrer(
                        authentication(referrerEmail, "Resume Referrer"), request.id()).getFilename());

                requests.accept(authentication(referrerEmail, "Resume Referrer"), request.id(), new AcceptRequest(false, false));
                assertEquals("candidate.pdf", resumes.downloadForReferrer(authentication(referrerEmail, "Resume Referrer"), request.id()).getFilename());

                byte[] replacementPdf = new byte[]{'%', 'P', 'D', 'F', '-', '2', '.', '0'};
                resumes.upload(authentication(seekerEmail, "Resume Seeker"),
                        new MockMultipartFile("file", "updated.pdf", "application/pdf", replacementPdf));
                var originalSnapshot = resumes.downloadForReferrer(authentication(referrerEmail, "Resume Referrer"), request.id());
                assertEquals("candidate.pdf", originalSnapshot.getFilename());
                assertArrayEquals(pdf, originalSnapshot.getData());

                SeekerRequest automaticShareRequest = requests.create(authentication(seekerEmail, "Resume Seeker"),
                        new CreateRequest(companyId, "Data Engineer", "https://jobs.example.test/data-engineer", "", true, false));
                assertEquals("updated.pdf", resumes.downloadForReferrer(
                        authentication(referrerEmail, "Resume Referrer"), automaticShareRequest.id()).getFilename());

                requests.decline(authentication(referrerEmail, "Resume Referrer"), automaticShareRequest.id());
                ResponseStatusException declined = assertThrows(ResponseStatusException.class,
                        () -> resumes.downloadForReferrer(authentication(referrerEmail, "Resume Referrer"), automaticShareRequest.id()));
                assertEquals(HttpStatus.FORBIDDEN, declined.getStatusCode());

                resumes.delete(authentication(seekerEmail, "Resume Seeker"));
                ResponseStatusException removed = assertThrows(ResponseStatusException.class,
                        () -> resumes.downloadForReferrer(authentication(referrerEmail, "Resume Referrer"), request.id()));
                assertEquals(HttpStatus.NOT_FOUND, removed.getStatusCode());
                assertEquals(0, count("resume_files", "user_id = ?", seekerId));
                assertEquals(0, count("referral_request_resumes", "request_id IN (?, ?)", request.id(), automaticShareRequest.id()));
        }

        @Test
        void removesRequestResumeSnapshotsThirtyDaysAfterClosure() {
                String suffix = UUID.randomUUID().toString().replace("-", "");
                String seekerEmail = "retention-seeker-" + suffix + "@example.test";
                String referrerEmail = "retention-referrer-" + suffix + "@example.test";
                long seekerId = insertUser("Retention Seeker", seekerEmail);
                long referrerId = insertUser("Retention Referrer", referrerEmail);
                long companyId = insertCompany(suffix);
                insertProfile(seekerId, null);
                insertReferrer(referrerId, companyId, "retention-work-" + suffix + "@company.test");
                resumes.upload(authentication(seekerEmail, "Retention Seeker"),
                        new MockMultipartFile("file", "retention.pdf", "application/pdf",
                                new byte[]{'%', 'P', 'D', 'F', '-', '1', '.', '7'}));

                SeekerRequest request = requests.create(authentication(seekerEmail, "Retention Seeker"),
                        new CreateRequest(companyId, "Engineer", "https://jobs.example.test/engineer", "", true, false));
                assertEquals(1, count("referral_request_resumes", "request_id = ?", request.id()));

                jdbc.sql("UPDATE company_referral_requests SET status = 'expired', "
                        + "closed_at = DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 31 DAY) WHERE id = ?")
                        .param(request.id()).update();
                entityManager.clear();

                scheduler.processDueRequests();

                assertEquals(0, count("referral_request_resumes", "request_id = ?", request.id()));
                assertEquals(1, count("resume_files", "user_id = ?", seekerId));
        }

        @Test
        void jobUrlIsRequiredAndMustUseHttps() {
                CreateRequest withoutUrl = new CreateRequest(1L, "Backend Engineer", "", "", true, false);
                CreateRequest insecureUrl = new CreateRequest(1L, "Backend Engineer", "http://jobs.example.test/role", "", true, false);
                CreateRequest validUrl = new CreateRequest(1L, "Backend Engineer", "https://jobs.example.test/role", "", true, false);

                assertTrue(validator.validate(withoutUrl).stream()
                                .anyMatch(violation -> "jobUrl".equals(violation.getPropertyPath().toString())));
                assertTrue(validator.validate(insecureUrl).stream()
                                .anyMatch(violation -> "jobUrl".equals(violation.getPropertyPath().toString())));
                assertFalse(validator.validate(validUrl).stream()
                                .anyMatch(violation -> "jobUrl".equals(violation.getPropertyPath().toString())));
        }

        @Test
        void directoryShowsCompaniesWithVerifiedReferrersAndNoEmployeeData() {
                String suffix = UUID.randomUUID().toString().replace("-", "");
                String seekerEmail = "directory-seeker-" + suffix + "@example.test";
                insertUser("Directory Seeker", seekerEmail);
                long firstCompanyId = insertCompany(suffix);
                long secondCompanyId = insertCompany("second-" + suffix);
                long unverifiedCompanyId = insertCompany("unverified-" + suffix);
                long firstReferrerId = insertUser("First Referrer", "first-" + suffix + "@example.test");
                long secondReferrerId = insertUser("Second Referrer", "second-" + suffix + "@example.test");
                insertReferrer(firstReferrerId, firstCompanyId, "first-work-" + suffix + "@company.test");
                jdbc.sql("INSERT INTO referrer_profiles (user_id, company_id, work_email, verified_at) "
                                + "VALUES (?, ?, ?, CURRENT_TIMESTAMP)")
                                .params(secondReferrerId, secondCompanyId, "second-work-" + suffix + "@company.test").update();

                var directory = companies.list(authentication(seekerEmail, "Directory Seeker"));

                assertTrue(directory.companies().stream().anyMatch(company -> company.id() == firstCompanyId));
                assertTrue(directory.companies().stream().anyMatch(company -> company.id() == secondCompanyId));
                assertTrue(directory.companies().stream().noneMatch(company -> company.id() == unverifiedCompanyId));
                assertEquals(5, directory.companies().getFirst().getClass().getRecordComponents().length);
                assertEquals(2, directory.referralRequestsMax());
                assertEquals(2, directory.referralRequestsLeft());
                assertEquals(0, directory.referralBonusCredits());
        }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        void wishlistInsertAndRemovalWorkWithCompositeKey() {
                String suffix = UUID.randomUUID().toString().replace("-", "");
                String seekerEmail = "wishlist-seeker-" + suffix + "@example.test";
                long seekerId = insertUser("Wishlist Seeker", seekerEmail);
                long companyId = insertCompany(suffix);

                try {
                        assertEquals(Boolean.TRUE, companyService.addToWishlist(authentication(seekerEmail, "Wishlist Seeker"), companyId).get("wishlisted"));
                        assertEquals(1, count("wishlists", "seeker_id = ? AND company_id = ?", seekerId, companyId));
                        assertEquals(Boolean.FALSE, companyService.removeFromWishlist(authentication(seekerEmail, "Wishlist Seeker"), companyId).get("wishlisted"));
                        assertEquals(0, count("wishlists", "seeker_id = ? AND company_id = ?", seekerId, companyId));
                } finally {
                        jdbc.sql("DELETE FROM users WHERE id = ?").param(seekerId).update();
                        jdbc.sql("DELETE FROM companies WHERE id = ?").param(companyId).update();
                }
        }

    @Test
    void expiresUnacceptedRequestAndRefundsItsAllowance() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        long seekerId = insertUser("Expiry Seeker", "expiry-seeker-" + suffix + "@example.test");
        long referrerId = insertUser("Expiry Referrer", "expiry-referrer-" + suffix + "@example.test");
        long companyId = insertCompany(suffix);
        insertProfile(seekerId, null);
        insertProfile(referrerId, null);
        insertReferrer(referrerId, companyId, "expiry-work-" + suffix + "@company.test");
        String seekerEmail = "expiry-seeker-" + suffix + "@example.test";
        SeekerRequest request = requests.create(authentication(seekerEmail, "Expiry Seeker"),
                new CreateRequest(companyId, "Data Engineer", "https://jobs.example.test/role", "", true, false));

        jdbc.sql("UPDATE company_referral_requests SET expires_at = DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 MINUTE) WHERE id = ?")
                .param(request.id()).update();
        entityManager.clear();
        scheduler.processDueRequests();

        assertEquals("expired", jdbc.sql("SELECT status FROM company_referral_requests WHERE id = ?")
                .param(request.id()).query(String.class).single());
        assertEquals(2, budget.allowance(seekerId, Instant.now()).left());
    }

        @Test
        void unverifiedSeekersReceiveTwoBaseCompanyRequestsPerRollingPeriod() {
                String suffix = UUID.randomUUID().toString().replace("-", "");
                String seekerEmail = "budget-seeker-" + suffix + "@example.test";
                String referrerEmail = "budget-referrer-" + suffix + "@example.test";
                long seekerId = insertUser("Budget Seeker", seekerEmail);
                long referrerId = insertUser("Budget Referrer", referrerEmail);
                long companyId = insertCompany(suffix);
                insertProfile(seekerId, null);
                insertProfile(referrerId, null);
                insertReferrer(referrerId, companyId, "budget-work-" + suffix + "@company.test");

                for (int requestNumber = 1; requestNumber <= 2; requestNumber++) {
                        requests.create(authentication(seekerEmail, "Budget Seeker"),
                                        new CreateRequest(companyId, "Role " + requestNumber,
                                                "https://jobs.example.test/role", "", true, false));
                }

                assertEquals(0, budget.allowance(seekerId, Instant.now()).left());
                ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                                () -> requests.create(authentication(seekerEmail, "Budget Seeker"),
                                                new CreateRequest(companyId, "Role 3", "https://jobs.example.test/role", "", true, false)));
                assertEquals(HttpStatus.CONFLICT.value(), exception.getStatusCode().value());
        }

        @Test
        void verifyingWorkEmailAddsThreeBaseRequestsForTheRollingPeriod() {
                String suffix = UUID.randomUUID().toString().replace("-", "");
                String seekerEmail = "verified-budget-seeker-" + suffix + "@example.test";
                String referrerEmail = "verified-budget-referrer-" + suffix + "@example.test";
                long seekerId = insertUser("Verified Budget Seeker", seekerEmail);
                long referrerId = insertUser("Budget Referrer", referrerEmail);
                long targetCompanyId = insertCompany("target-" + suffix);
                long verifiedCompanyId = insertCompany("verified-" + suffix);
                insertProfile(seekerId, null);
                insertProfile(referrerId, null);
                insertReferrer(referrerId, targetCompanyId, "target-work-" + suffix + "@company.test");

                for (int requestNumber = 1; requestNumber <= 2; requestNumber++) {
                        requests.create(authentication(seekerEmail, "Verified Budget Seeker"),
                                new CreateRequest(targetCompanyId, "Role " + requestNumber,
                                        "https://jobs.example.test/role", "", true, false));
                }

                ReferralRequestBudget.Allowance unverifiedAllowance = budget.allowance(seekerId, Instant.now());
                assertEquals(0, unverifiedAllowance.left());
                assertEquals(2, unverifiedAllowance.baseMax());

                jdbc.sql("INSERT INTO referrer_profiles (user_id, company_id, work_email, verified_at) "
                        + "VALUES (?, ?, ?, CURRENT_TIMESTAMP)")
                        .params(seekerId, verifiedCompanyId, "verified-work-" + suffix + "@company.test").update();

                ReferralRequestBudget.Allowance verifiedAllowance = budget.allowance(seekerId, Instant.now());
                assertEquals(3, verifiedAllowance.left());
                assertEquals(5, verifiedAllowance.baseMax());
                assertEquals(3, verifiedAllowance.baseLeft());
        }

            @Test
            void referralCreditsAreCappedAtTwoAndExpireAfterNinetyDays() {
                String suffix = UUID.randomUUID().toString().replace("-", "");
                String referrerEmail = "credit-referrer-" + suffix + "@example.test";
                long referrerId = insertUser("Credit Referrer", referrerEmail);
                long companyId = insertCompany(suffix);
                insertProfile(referrerId, null);
                insertReferrer(referrerId, companyId, "credit-work-" + suffix + "@company.test");

                for (int referralNumber = 1; referralNumber <= 3; referralNumber++) {
                    String seekerEmail = "credit-seeker-" + referralNumber + "-" + suffix + "@example.test";
                    insertProfile(insertUser("Credit Seeker " + referralNumber, seekerEmail), null);
                    long seekerId = jdbc.sql("SELECT id FROM users WHERE email = ?").param(seekerEmail).query(Long.class).single();
                    SeekerRequest request = requests.create(authentication(seekerEmail, "Credit Seeker"),
                            new CreateRequest(companyId, "Role " + referralNumber, "https://jobs.example.test/role", "", true, false));
                    requests.accept(authentication(referrerEmail, "Credit Referrer"), request.id(), new AcceptRequest(false, false));
                    long responseId = responseIdFor(requests.inbox(authentication(referrerEmail, "Credit Referrer")), request.id());
                    requests.markSubmitted(authentication(referrerEmail, "Credit Referrer"), request.id());
                    requests.confirmSubmission(authentication(seekerEmail, "Credit Seeker"), request.id(), responseId);
                    assertEquals(1, count("referrals", "referrer_id = ? AND candidate_id = ? AND company_id = ?",
                            referrerId, seekerId, companyId));
                }

                assertEquals(2, count("referral_request_credits", "user_id = ?", referrerId));
                jdbc.sql("UPDATE referral_request_credits SET expires_at = DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 91 DAY) WHERE user_id = ?")
                        .param(referrerId).update();
                assertEquals(0, budget.allowance(referrerId, Instant.now()).bonusAvailable());
            }

            @Test
            void restoresBonusCreditWhenUnansweredRequestExpires() {
                String suffix = UUID.randomUUID().toString().replace("-", "");
                String seekerEmail = "refund-seeker-" + suffix + "@example.test";
                String referrerEmail = "refund-referrer-" + suffix + "@example.test";
                long seekerId = insertUser("Refund Seeker", seekerEmail);
                long referrerId = insertUser("Refund Referrer", referrerEmail);
                long referralCompanyId = insertCompany(suffix);
                long targetCompanyId = insertCompany("target-" + suffix);
                insertProfile(seekerId, null);
                insertProfile(referrerId, null);
                long targetReferrerId = insertUser("Target Referrer", "target-referrer-" + suffix + "@example.test");
                insertProfile(targetReferrerId, null);
                insertReferrer(referrerId, referralCompanyId, "refund-work-" + suffix + "@company.test");
                insertReferrer(targetReferrerId, targetCompanyId, "target-work-" + suffix + "@company.test");

                SeekerRequest earnedFor = requests.create(authentication(seekerEmail, "Refund Seeker"),
                        new CreateRequest(referralCompanyId, "Referral Role", "https://jobs.example.test/role", "", true, false));
                requests.accept(authentication(referrerEmail, "Refund Referrer"), earnedFor.id(), new AcceptRequest(false, false));
                long earnedResponseId = responseIdFor(requests.inbox(authentication(referrerEmail, "Refund Referrer")), earnedFor.id());
                requests.markSubmitted(authentication(referrerEmail, "Refund Referrer"), earnedFor.id());
                requests.confirmSubmission(authentication(seekerEmail, "Refund Seeker"), earnedFor.id(), earnedResponseId);

                for (int requestNumber = 1; requestNumber <= 5; requestNumber++) {
                    requests.create(authentication(referrerEmail, "Refund Referrer"),
                            new CreateRequest(targetCompanyId, "Target Role " + requestNumber, "https://jobs.example.test/role", "", true, false));
                }
                assertEquals(1, budget.allowance(referrerId, Instant.now()).bonusAvailable());
                SeekerRequest bonusRequest = requests.create(authentication(referrerEmail, "Refund Referrer"),
                        new CreateRequest(targetCompanyId, "Target Role Bonus", "https://jobs.example.test/role", "", true, false));
                assertEquals(0, budget.allowance(referrerId, Instant.now()).left());

                jdbc.sql("UPDATE company_referral_requests SET expires_at = DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 MINUTE) WHERE id = ?")
                        .param(bonusRequest.id()).update();
                entityManager.flush();
                entityManager.clear();
                scheduler.processDueRequests();

                assertEquals(1, budget.allowance(referrerId, Instant.now()).bonusAvailable());
                assertEquals(0, count("referral_request_credits", "user_id = ? AND consumed_by_request_id IS NOT NULL", referrerId));
            }

    @Test
    void acceptedAssignmentReminderIsSentOnlyOncePerDueWindow() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String seekerEmail = "reminder-seeker-" + suffix + "@example.test";
        String referrerEmail = "reminder-referrer-" + suffix + "@example.test";
        long seekerId = insertUser("Reminder Seeker", seekerEmail);
        long referrerId = insertUser("Reminder Referrer", referrerEmail);
        long companyId = insertCompany(suffix);
        insertProfile(seekerId, null);
        insertProfile(referrerId, null);
        insertReferrer(referrerId, companyId, "reminder-work-" + suffix + "@company.test");
        SeekerRequest request = requests.create(authentication(seekerEmail, "Reminder Seeker"),
                new CreateRequest(companyId, "Platform Engineer", "https://jobs.example.test/role", "", true, false));
        requests.accept(authentication(referrerEmail, "Reminder Referrer"), request.id(), new AcceptRequest(false, false));
        entityManager.flush();
        jdbc.sql("UPDATE referral_request_responses SET accepted_at = DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 3 DAY) WHERE request_id = ?")
                .param(request.id()).update();
        entityManager.clear();

        scheduler.processDueRequests();
        scheduler.processDueRequests();

        entityManager.flush();
        assertEquals(1, count("referral_request_responses", "request_id = ? AND reminders_sent = 1", request.id()));
        assertEquals(1, count("notifications", "user_id = ? AND type = 'referral_reminder'", referrerId));
    }

    @Test
        void activeAssignmentCapBlocksAcceptance() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String referrerEmail = "capacity-referrer-" + suffix + "@example.test";
        String firstSeekerEmail = "capacity-first-" + suffix + "@example.test";
        String secondSeekerEmail = "capacity-second-" + suffix + "@example.test";
        long referrerId = insertUser("Capacity Referrer", referrerEmail);
        long firstSeekerId = insertUser("Capacity Seeker 1", firstSeekerEmail);
        long secondSeekerId = insertUser("Capacity Seeker 2", secondSeekerEmail);
        long companyId = insertCompany(suffix);
        insertProfile(referrerId, null);
        insertProfile(firstSeekerId, null);
        insertProfile(secondSeekerId, null);
        insertReferrer(referrerId, companyId, "capacity-work-" + suffix + "@company.test");
        jdbc.sql("UPDATE referrer_profiles SET referral_limit = 1 WHERE user_id = ?").param(referrerId).update();

        SeekerRequest first = requests.create(authentication(firstSeekerEmail, "Capacity Seeker 1"),
                new CreateRequest(companyId, "First Role", "https://jobs.example.test/role", "", true, false));
        SeekerRequest second = requests.create(authentication(secondSeekerEmail, "Capacity Seeker 2"),
                new CreateRequest(companyId, "Second Role", "https://jobs.example.test/role", "", true, false));
        requests.accept(authentication(referrerEmail, "Capacity Referrer"), first.id(), new AcceptRequest(false, false));

        ResponseStatusException full = assertThrows(ResponseStatusException.class,
                () -> requests.accept(authentication(referrerEmail, "Capacity Referrer"), second.id(), new AcceptRequest(false, false)));
        assertEquals(HttpStatus.CONFLICT.value(), full.getStatusCode().value());

    }

    @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        void dayFourClosureReleasesAssignmentCapacity() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String referrerEmail = "timeout-referrer-" + suffix + "@example.test";
        String firstSeekerEmail = "timeout-first-" + suffix + "@example.test";
        String secondSeekerEmail = "timeout-second-" + suffix + "@example.test";
        long referrerId = insertUser("Timeout Referrer", referrerEmail);
        long firstSeekerId = insertUser("Timeout Seeker 1", firstSeekerEmail);
        long secondSeekerId = insertUser("Timeout Seeker 2", secondSeekerEmail);
        long companyId = insertCompany(suffix);
        try {
            insertProfile(referrerId, null);
            insertProfile(firstSeekerId, null);
            insertProfile(secondSeekerId, null);
            insertReferrer(referrerId, companyId, "timeout-work-" + suffix + "@company.test");
            jdbc.sql("UPDATE referrer_profiles SET referral_limit = 1 WHERE user_id = ?").param(referrerId).update();

            SeekerRequest first = requests.create(authentication(firstSeekerEmail, "Timeout Seeker 1"),
                    new CreateRequest(companyId, "First Timeout Role", "https://jobs.example.test/role", "", true, false));
            SeekerRequest second = requests.create(authentication(secondSeekerEmail, "Timeout Seeker 2"),
                    new CreateRequest(companyId, "Second Timeout Role", "https://jobs.example.test/role", "", true, false));
            requests.accept(authentication(referrerEmail, "Timeout Referrer"), first.id(), new AcceptRequest(false, false));

            jdbc.sql("UPDATE referral_request_responses SET accepted_at = DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 5 DAY) WHERE request_id = ?")
                    .param(first.id()).update();
            assertEquals(1, responses.findStaleAcceptedIds(Instant.now().minus(4, ChronoUnit.DAYS)).size());
            scheduler.processDueRequests();

            assertEquals("closed", responses.findByRequestIdOrderByCreatedAt(first.id()).getFirst().getStatus());
            requests.accept(authentication(referrerEmail, "Timeout Referrer"), second.id(), new AcceptRequest(false, false));
            assertEquals(1, count("referral_request_responses", "request_id = ? AND status = 'accepted'", second.id()));
        } finally {
            jdbc.sql("DELETE FROM users WHERE id IN (?, ?, ?)")
                    .params(referrerId, firstSeekerId, secondSeekerId).update();
        }
    }

    private long insertUser(String name, String email) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO users (name, email, auth_provider) VALUES (?, ?, 'test')")
                .params(name, email).update(keys);
        return keys.getKey().longValue();
    }

    private long insertCompany(String suffix) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO companies (name, email_domain) VALUES ('Referral Test', ?)")
                .param("referral-" + suffix + ".example.test").update(keys);
        return keys.getKey().longValue();
    }

    private void insertProfile(long userId, String phone) {
        jdbc.sql("INSERT INTO profiles (user_id, experience_level, pitch, skills, roles_wanted, phone_number) "
                + "VALUES (?, '3-6', 'I build backend services.', JSON_ARRAY('Java'), JSON_ARRAY('Backend'), ?)")
                .params(userId, phone).update();
    }

    private void insertReferrer(long userId, long companyId, String workEmail) {
        jdbc.sql("INSERT INTO referrer_profiles (user_id, company_id, work_email, verified_at, accepts_referral_requests) "
                + "VALUES (?, ?, ?, CURRENT_TIMESTAMP, TRUE)")
                .params(userId, companyId, workEmail).update();
    }

        private ReferrerInboxItem inboxItem(List<ReferrerInboxItem> items) {
                return items.stream().filter(item -> item.seekerName() != null).findFirst().orElseThrow();
    }

        private long responseIdFor(List<ReferrerInboxItem> items, long requestId) {
                return items.stream().filter(item -> item.requestId() == requestId).findFirst().orElseThrow().responseId();
        }

    private OAuth2AuthenticationToken authentication(String email, String name) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        var principal = new DefaultOAuth2User(authorities, Map.of("email", email, "name", name), "email");
        return new OAuth2AuthenticationToken(principal, authorities, "google");
    }

    private long count(String table, String predicate, Object... parameters) {
        return jdbc.sql("SELECT COUNT(*) FROM " + table + " WHERE " + predicate).params(parameters)
                .query(Long.class).single();
    }
}