package com.referralportal.repository.jpa;

import com.referralportal.entity.CompanyReferralRequest;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanyReferralRequestJpaRepository extends JpaRepository<CompanyReferralRequest, Long> {

    @Query("select r from CompanyReferralRequest r join fetch r.company where r.seeker.id = :seekerId order by r.createdAt desc")
    List<CompanyReferralRequest> findBySeekerId(@Param("seekerId") long seekerId);

        @Query("select count(r) from CompanyReferralRequest r where r.seeker.id = :seekerId and r.createdAt >= :since "
            + "and r.status <> 'expired' and r.baseCharge = true")
    long countBudgetedBySeekerSince(@Param("seekerId") long seekerId, @Param("since") Instant since);

    @Query("select r.id from CompanyReferralRequest r where r.status = 'open' and r.expiresAt <= :now order by r.id")
    List<Long> findExpiredOpenRequestIds(@Param("now") Instant now);

        @Query("select r.id from CompanyReferralRequest r where r.status = 'closed' and r.closedAt <= :decisionBefore "
            + "and exists (select assigned.id from ReferralRequestResponse assigned where assigned.request = r "
            + "and assigned.status = 'closed' and assigned.acceptedAt is not null and assigned.submittedAt is null) "
            + "and exists (select other.id from ReferralRequestResponse other where other.request = r "
                + "and other.status in ('accepted_elsewhere', 'pending')) "
                + "and not exists (select active.id from ReferralRequestResponse active where active.request = r "
                + "and active.status in ('accepted', 'submitted', 'confirmed')) order by r.id")
        List<Long> findContinuationRequestsDueForExpiry(@Param("decisionBefore") Instant decisionBefore);

        @Query("select r from CompanyReferralRequest r where r.seeker.id = :seekerId and r.company.id = :companyId "
                + "and lower(r.roleTitle) = lower(:roleTitle) and (r.status = 'open' or (r.status = 'closed' and exists "
                + "(select continued.id from ReferralRequestResponse continued where continued.request = r "
                + "and continued.status = 'accepted_elsewhere')) or exists "
                + "(select rr.id from ReferralRequestResponse rr where rr.request = r and rr.status = 'accepted' "
                + "and rr.submittedAt is null))")
        List<CompanyReferralRequest> findActiveDuplicate(@Param("seekerId") long seekerId,
            @Param("companyId") long companyId, @Param("roleTitle") String roleTitle);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select r from CompanyReferralRequest r where r.id = :requestId")
        Optional<CompanyReferralRequest> findByIdForUpdate(@Param("requestId") long requestId);
}