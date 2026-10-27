package com.referralportal.service;

import com.referralportal.common.ChipLists;
import com.referralportal.dto.ReferralRequestDtos;
import com.referralportal.entity.Company;
import com.referralportal.entity.CompanyReferralRequest;
import com.referralportal.entity.Profile;
import com.referralportal.entity.Referral;
import com.referralportal.entity.ReferralRequestCredit;
import com.referralportal.entity.ReferralRequestResponse;
import com.referralportal.entity.ReferrerProfile;
import com.referralportal.entity.User;
import com.referralportal.repository.jpa.CompanyJpaRepository;
import com.referralportal.repository.jpa.CompanyReferralRequestJpaRepository;
import com.referralportal.repository.jpa.OpenRoleJpaRepository;
import com.referralportal.repository.jpa.ProfileJpaRepository;
import com.referralportal.repository.jpa.ReferralJpaRepository;
import com.referralportal.repository.jpa.ReferralRequestCreditJpaRepository;
import com.referralportal.repository.jpa.ReferralRequestResponseJpaRepository;
import com.referralportal.repository.jpa.ReferrerProfileJpaRepository;
import com.referralportal.repository.jpa.UserJpaRepository;
import com.referralportal.user.CurrentUser;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static com.referralportal.dto.ReferralRequestDtos.AcceptRequest;
import static com.referralportal.dto.ReferralRequestDtos.CreateRequest;
import static com.referralportal.dto.ReferralRequestDtos.ReferrerInboxItem;
import static com.referralportal.dto.ReferralRequestDtos.SeekerRequest;
import static com.referralportal.dto.ReferralRequestDtos.SeekerResponse;

@Service
public class CompanyReferralRequestService {

    private static final int REQUEST_DAYS = 4;
    private static final int CREDIT_DAYS = 90;

    @Autowired
    private UserService users;
    @Autowired
    private UserJpaRepository userRows;
    @Autowired
    private ProfileJpaRepository profiles;
    @Autowired
    private CompanyJpaRepository companies;
    @Autowired
    private ReferrerProfileJpaRepository referrerProfiles;
    @Autowired
    private CompanyReferralRequestJpaRepository requests;
    @Autowired
    private ReferralRequestResponseJpaRepository responses;
    @Autowired
    private ReferralRequestCreditJpaRepository credits;
    @Autowired
    private ReferralJpaRepository referrals;
    @Autowired
    private OpenRoleJpaRepository openRoles;
    @Autowired
    private ReferralRequestBudget budget;
    @Autowired
    private ReferralNotificationService notifications;

    @Transactional
    public SeekerRequest create(Authentication authentication, CreateRequest input) {
        CurrentUser current = users.current(authentication);
        Instant now = Instant.now();
        User seeker = userRows.findByIdForUpdate(current.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Profile profile = profiles.findById(current.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Complete your seeker profile first."));
        if (!Boolean.TRUE.equals(input.shareProfile())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose whether to share your profile with invited referrers.");
        }
        if (Boolean.TRUE.equals(input.shareSeekerPhone()) && blank(profile.getPhoneNumber())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Add a phone number to your profile before sharing it.");
        }
        Company company = companies.findById(input.companyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found."));
        Optional<ReferrerProfile> seekerReferrer = referrerProfiles.findById(current.id());
        if (seekerReferrer.filter(rp -> rp.getVerifiedAt() != null && rp.getCompany().getId().equals(company.getId())).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot request a referral from your own company.");
        }
        List<ReferrerProfile> eligible = referrerProfiles
                .findByCompany_IdAndVerifiedAtIsNotNullAndReferrerTrueAndAcceptsReferralRequestsTrue(company.getId())
                .stream().filter(rp -> !rp.getUserId().equals(current.id())).toList();
        if (eligible.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This company is not accepting referral requests right now.");
        }
        String roleTitle = input.roleTitle().trim();
        if (!requests.findActiveDuplicate(current.id(), company.getId(), roleTitle).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already have an active request for this company and role.");
        }
        if (budget.allowance(current.id(), now).left() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have used all of your referral requests and credits for this period.");
        }

        CompanyReferralRequest request = new CompanyReferralRequest();
        request.setSeeker(seeker);
        request.setCompany(company);
        request.setRoleTitle(roleTitle);
        request.setJobUrl(blankToNull(input.jobUrl()));
        request.setSeekerNote(blankToNull(input.seekerNote()));
        request.setShareProfile(true);
        request.setShareSeekerEmail(true);
        request.setShareSeekerPhone(Boolean.TRUE.equals(input.shareSeekerPhone()));
        request.setCreatedAt(now);
        request.setExpiresAt(now.plus(REQUEST_DAYS, ChronoUnit.DAYS));
        request = requests.save(request);
        budget.charge(current.id(), request, now);

        for (ReferrerProfile referrerProfile : eligible) {
            ReferralRequestResponse response = new ReferralRequestResponse();
            response.setRequest(request);
            response.setReferrer(referrerProfile.getUser());
            response.setCreatedAt(now);
            response = responses.save(response);
            notifications.invite(request, response.getReferrer());
        }
        return seekerRequest(request);
    }

    @Transactional(readOnly = true)
    public List<SeekerRequest> seekerRequests(Authentication authentication) {
        long seekerId = users.current(authentication).id();
        return requests.findBySeekerId(seekerId).stream().map(this::seekerRequest).toList();
    }

    @Transactional(readOnly = true)
    public List<ReferrerInboxItem> inbox(Authentication authentication) {
        long referrerId = users.current(authentication).id();
        return responses.findInboxByReferrerId(referrerId).stream().map(this::inboxItem).toList();
    }

    @Transactional
    public Map<String, Object> accept(Authentication authentication, long requestId, AcceptRequest input) {
        CurrentUser current = users.current(authentication);
        Instant now = Instant.now();
        CompanyReferralRequest request = requests.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Referral request not found."));
        if (!"open".equals(request.getStatus()) || !request.getExpiresAt().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This referral request is no longer accepting responses.");
        }
        ReferralRequestResponse response = responses.findByRequestIdAndReferrerId(requestId, current.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You were not invited to this request."));
        if (!"pending".equals(response.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already responded to this request.");
        }
        ReferrerProfile referrer = referrerProfiles.findByUserIdForUpdate(current.id())
                .filter(profile -> profile.getVerifiedAt() != null && profile.isReferrer()
                        && profile.isAcceptsReferralRequests() && profile.getCompany().getId().equals(request.getCompany().getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Enable referral requests in your verified referrer settings first."));
        String referrerPhone = Optional.ofNullable(referrer.getUser().getProfile()).map(Profile::getPhoneNumber).orElse(null);
        if (Boolean.TRUE.equals(input.shareReferrerPhone()) && blank(referrerPhone)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Add a phone number to your profile before sharing it.");
        }
        if (responses.countActiveAssignments(current.id()) >= referrer.getReferralLimit()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have reached your active referral assignment limit.");
        }
        if (referrals.existsByReferrerIdAndCandidateIdAndCompanyId(current.id(), request.getSeeker().getId(), request.getCompany().getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already referred this person at your company.");
        }
        response.setStatus("accepted");
        response.setAcceptedAt(now);
        response.setShareReferrerEmail(Boolean.TRUE.equals(input.shareReferrerEmail()));
        response.setShareReferrerPhone(Boolean.TRUE.equals(input.shareReferrerPhone()));
        responses.save(response);
        notifications.accepted(request, response);
        return Map.of("responseId", response.getId(), "status", response.getStatus());
    }

    @Transactional
    public Map<String, Object> decline(Authentication authentication, long requestId) {
        long referrerId = users.current(authentication).id();
        CompanyReferralRequest request = requests.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Referral request not found."));
        ReferralRequestResponse response = responses.findByRequestIdAndReferrerId(requestId, referrerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You were not invited to this request."));
        if (!"open".equals(request.getStatus()) || !"pending".equals(response.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This request can no longer be declined.");
        }
        response.setStatus("declined");
        response.setClosedAt(Instant.now());
        responses.save(response);
        return Map.of("status", response.getStatus());
    }

    @Transactional
    public Map<String, Object> cancel(Authentication authentication, long requestId) {
        long seekerId = users.current(authentication).id();
        CompanyReferralRequest request = requests.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Referral request not found."));
        if (!request.getSeeker().getId().equals(seekerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (!"open".equals(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This request is already closed.");
        }
        List<ReferralRequestResponse> requestResponses = responses.findByRequestIdOrderByCreatedAt(requestId);
        if (requestResponses.stream().anyMatch(response -> "accepted".equals(response.getStatus())
                || "submitted".equals(response.getStatus()) || "confirmed".equals(response.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A referrer has accepted this request; it can no longer be cancelled.");
        }
        request.setStatus("expired");
        request.setClosedAt(Instant.now());
        requests.save(request);
        for (ReferralRequestResponse response : requestResponses) {
            if ("pending".equals(response.getStatus())) {
                response.setStatus("expired");
                response.setClosedAt(Instant.now());
                responses.save(response);
            }
        }
        budget.refundCreditIfUsed(request);
        return Map.of("status", request.getStatus());
    }

    @Transactional
    public Map<String, Object> markSubmitted(Authentication authentication, long requestId) {
        long referrerId = users.current(authentication).id();
        ReferralRequestResponse response = ownedResponse(requestId, referrerId, true);
        if ("submitted".equals(response.getStatus()) || "confirmed".equals(response.getStatus())) {
            return Map.of("status", response.getStatus());
        }
        if (!"accepted".equals(response.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only an accepted referral can be marked submitted.");
        }
        Instant now = Instant.now();
        if (referrals.existsByReferrerIdAndCandidateIdAndCompanyId(referrerId,
            response.getRequest().getSeeker().getId(), response.getRequest().getCompany().getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already referred this person at your company.");
        }
        Referral referral = new Referral();
        referral.setReferrer(response.getReferrer());
        referral.setCandidate(response.getRequest().getSeeker());
        referral.setCompany(response.getRequest().getCompany());
        openRoles.findFirstByCompanyIdAndTitle(response.getRequest().getCompany().getId(),
            response.getRequest().getRoleTitle()).ifPresent(referral::setRole);
        referral.setStatus("submitted");
        referral.setCreatedAt(now);
        referral.setUpdatedAt(now);
        referrals.save(referral);
        response.setStatus("submitted");
        response.setSubmittedAt(now);
        responses.save(response);
        notifications.submitted(response.getRequest(), response);
        return Map.of("status", response.getStatus());
    }

    @Transactional
    public Map<String, Object> confirmSubmission(Authentication authentication, long requestId, long responseId) {
        long seekerId = users.current(authentication).id();
        CompanyReferralRequest request = requests.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Referral request not found."));
        ReferralRequestResponse response = responses.findByIdForUpdate(responseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Submitted referral not found."));
        if (!response.getRequest().getId().equals(request.getId()) || !request.getSeeker().getId().equals(seekerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if ("confirmed".equals(response.getStatus())) {
            return Map.of("status", response.getStatus(), "creditAwarded", credits.findBySourceResponseId(response.getId()).isPresent());
        }
        if (!"submitted".equals(response.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This referral has not been marked submitted.");
        }
        Instant now = Instant.now();
        response.setStatus("confirmed");
        response.setConfirmedAt(now);
        responses.save(response);
        boolean awarded = false;
        ReferrerProfile referrer = referrerProfiles.findByUserIdForUpdate(response.getReferrer().getId()).orElse(null);
        if (referrer != null && budget.mayEarnAnother(response.getReferrer().getId(), now)
                && credits.findBySourceResponseId(response.getId()).isEmpty()) {
            ReferralRequestCredit credit = new ReferralRequestCredit();
            credit.setUser(response.getReferrer());
            credit.setSourceResponse(response);
            credit.setCreatedAt(now);
            credit.setExpiresAt(now.plus(CREDIT_DAYS, ChronoUnit.DAYS));
            credits.save(credit);
            awarded = true;
        }
        notifications.confirmed(response.getRequest(), response);
        return Map.of("status", response.getStatus(), "creditAwarded", awarded);
    }

    private ReferralRequestResponse ownedResponse(long requestId, long referrerId, boolean requireAccepted) {
        CompanyReferralRequest request = requests.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Referral request not found."));
        ReferralRequestResponse response = responses.findByRequestIdAndReferrerId(requestId, referrerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        if (requireAccepted && response.getAcceptedAt() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return response;
    }

    private SeekerRequest seekerRequest(CompanyReferralRequest request) {
        List<ReferralRequestResponse> all = responses.findByRequestIdOrderByCreatedAt(request.getId());
        List<SeekerResponse> accepted = all.stream()
                .filter(response -> response.getAcceptedAt() != null)
                .map(response -> {
                    ReferrerProfile profile = referrerProfiles.findById(response.getReferrer().getId()).orElse(null);
                    String referrerPhone = profile == null || !response.isShareReferrerPhone() ? null
                            : Optional.ofNullable(profile.getUser().getProfile()).map(Profile::getPhoneNumber).orElse(null);
                    return new SeekerResponse(response.getId(), response.getStatus(), response.getReferrer().getName(),
                            profile == null ? null : profile.getJobTitle(),
                            response.isShareReferrerEmail() && profile != null ? profile.getWorkEmail() : null,
                            referrerPhone, time(response.getAcceptedAt()), time(response.getSubmittedAt()),
                            time(response.getConfirmedAt()));
                }).toList();
        int pending = (int) all.stream().filter(response -> "pending".equals(response.getStatus())).count();
        int declined = (int) all.stream().filter(response -> "declined".equals(response.getStatus())).count();
        int acceptedCount = (int) all.stream().filter(response -> response.getAcceptedAt() != null).count();
        String status = request.getStatus();
        if ("open".equals(status) && acceptedCount > 0) {
            status = "in_progress";
        } else if ("open".equals(status) && pending == 0 && declined > 0) {
            status = "no_acceptances";
        }
        return new SeekerRequest(request.getId(), request.getCompany().getName(), request.getRoleTitle(), request.getJobUrl(),
                request.getSeekerNote(), status, time(request.getCreatedAt()), time(request.getExpiresAt()),
                pending, declined, accepted);
    }

    private ReferrerInboxItem inboxItem(ReferralRequestResponse response) {
        CompanyReferralRequest request = response.getRequest();
        User seeker = request.getSeeker();
        Profile profile = profiles.findById(seeker.getId()).orElse(null);
        boolean accepted = response.getAcceptedAt() != null;
        boolean profileShared = request.isShareProfile() && profile != null;
        String email = accepted && request.isShareSeekerEmail() ? seeker.getEmail() : null;
        String phone = accepted && request.isShareSeekerPhone() && profile != null ? profile.getPhoneNumber() : null;
        boolean canSharePhone = Optional.ofNullable(response.getReferrer().getProfile())
            .map(Profile::getPhoneNumber).filter(value -> !value.isBlank()).isPresent();
        List<String> referralRolePreferences = referrerProfiles.findById(response.getReferrer().getId())
            .map(referrer -> ChipLists.fromJson(referrer.getReferralRolePreferences())).orElse(List.of());
        return new ReferrerInboxItem(response.getId(), request.getId(), request.getCompany().getName(), request.getRoleTitle(),
                request.getJobUrl(), seeker.getName(), profileShared ? profile.getExperienceLevel() : null,
                profileShared ? ChipLists.fromJson(profile.getSkills()) : List.of(),
            profileShared ? ChipLists.fromJson(profile.getRolesWanted()) : List.of(), referralRolePreferences,
                profileShared ? profile.getPitch() : null,
                profileShared ? profile.getGithubUrl() : null,
                profileShared ? profile.getLinkedinUrl() : null,
                request.getSeekerNote(), canSharePhone, email, phone,
            response.getStatus(), time(response.getCreatedAt()),
                time(response.getAcceptedAt()), time(response.getSubmittedAt()), response.getRemindersSent());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String time(Instant value) {
        return value == null ? null : value.toString();
    }
}