package com.referralportal.repository;

import com.referralportal.common.ChipLists;
import com.referralportal.entity.Company;
import com.referralportal.entity.ReferrerProfile;
import com.referralportal.repository.jpa.CompanyJpaRepository;
import com.referralportal.repository.jpa.EmailOtpJpaRepository;
import com.referralportal.repository.jpa.ReferrerProfileJpaRepository;
import com.referralportal.repository.jpa.UserJpaRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import static com.referralportal.dto.ReferrerDtos.Settings;

@Repository
public class ReferrerRepository {

    @Autowired
    private EmailOtpJpaRepository otps;
    @Autowired
    private ReferrerProfileJpaRepository referrerProfiles;
    @Autowired
    private CompanyJpaRepository companies;
    @Autowired
    private UserJpaRepository users;

    public long countOtpRequestsInLast15Minutes(long userId) {
        return otps.countByUserIdAndCreatedAtAfter(userId, Instant.now().minus(15, ChronoUnit.MINUTES));
    }

    public boolean hasOtpRequestInLastMinute(long userId) {
        return otps.existsByUserIdAndCreatedAtAfter(userId, Instant.now().minus(1, ChronoUnit.MINUTES));
    }

    public void deleteOtps(long userId) {
        otps.deleteByUserId(userId);
    }

    public void insertOtp(long userId, String workEmail, String codeHash) {
        com.referralportal.entity.EmailOtp otp = new com.referralportal.entity.EmailOtp();
        otp.setUser(users.getReferenceById(userId));
        otp.setWorkEmail(workEmail);
        otp.setCodeHash(codeHash);
        otp.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        otp.setCreatedAt(Instant.now());
        otps.save(otp);
    }

    public Optional<EmailOtp> latestOtpForUpdate(long userId) {
        List<com.referralportal.entity.EmailOtp> rows = otps.findLatestForUpdate(userId, PageRequest.of(0, 1));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        com.referralportal.entity.EmailOtp otp = rows.get(0);
        boolean expired = !otp.getExpiresAt().isAfter(Instant.now());
        return Optional.of(new EmailOtp(otp.getId(), otp.getWorkEmail(), otp.getCodeHash(), expired, otp.getAttempts()));
    }

    public void incrementAttempts(long otpId) {
        otps.incrementAttempts(otpId);
    }

    public void deleteOtp(long otpId) {
        otps.deleteById(otpId);
    }

    public boolean emailAlreadyClaimed(long userId, String workEmail) {
        return referrerProfiles.existsByWorkEmailAndUserIdNot(workEmail, userId)
                || users.existsByEmailAndIdNot(workEmail, userId);
    }

    public long findOrCreateCompany(String emailDomain, String companyName) {
        return companies.findByEmailDomain(emailDomain).map(Company::getId).orElseGet(() -> {
            Company company = new Company();
            company.setName(companyName);
            company.setEmailDomain(emailDomain);
            return companies.save(company).getId();
        });
    }

    public void verifyReferrer(long userId, long companyId, String workEmail) {
        ReferrerProfile profile = referrerProfiles.findById(userId).orElseGet(() -> {
            ReferrerProfile created = new ReferrerProfile();
            created.setUser(users.getReferenceById(userId));
            return created;
        });
        profile.setCompany(companies.getReferenceById(companyId));
        profile.setWorkEmail(workEmail);
        profile.setVerifiedAt(Instant.now());
        profile.setReferrer(true);
        referrerProfiles.save(profile);
    }

    public Settings settings(long userId) {
        return referrerProfiles.findById(userId)
                .map(rp -> new Settings(rp.getVerifiedAt() != null, rp.getWorkEmail(), rp.getCompany().getName(),
                rp.isOpenToMeet(), rp.getSlotCapacity(), rp.getReferralLimit(), rp.isAcceptsReferralRequests(),
                ChipLists.fromJson(rp.getReferralRolePreferences())))
            .orElse(new Settings(false, null, null, false, 3, 10, false, List.of()));
    }

        public void updateSettings(long userId, boolean openToMeet, int slotCapacity, int referralLimit,
            boolean acceptsReferralRequests, List<String> referralRolePreferences) {
        referrerProfiles.findById(userId).filter(rp -> rp.getVerifiedAt() != null).ifPresent(rp -> {
            rp.setOpenToMeet(openToMeet);
            rp.setSlotCapacity((short) slotCapacity);
            rp.setReferralLimit((short) referralLimit);
            rp.setAcceptsReferralRequests(acceptsReferralRequests);
            rp.setReferralRolePreferences(ChipLists.toJson(referralRolePreferences));
            referrerProfiles.save(rp);
        });
    }

    public record EmailOtp(long id, String workEmail, String codeHash, boolean expired, int attempts) {}
}