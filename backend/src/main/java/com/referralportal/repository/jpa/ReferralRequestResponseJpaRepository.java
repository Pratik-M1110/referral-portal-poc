package com.referralportal.repository.jpa;

import com.referralportal.entity.ReferralRequestResponse;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReferralRequestResponseJpaRepository extends JpaRepository<ReferralRequestResponse, Long> {

    @Query("select rr from ReferralRequestResponse rr join fetch rr.request r join fetch r.company join fetch r.seeker "
            + "where rr.referrer.id = :referrerId order by rr.createdAt desc")
    List<ReferralRequestResponse> findInboxByReferrerId(@Param("referrerId") long referrerId);

    @Query("select rr from ReferralRequestResponse rr join fetch rr.request r join fetch r.company join fetch rr.referrer "
            + "where r.seeker.id = :seekerId order by rr.createdAt desc")
    List<ReferralRequestResponse> findAssignmentsBySeekerId(@Param("seekerId") long seekerId);

    List<ReferralRequestResponse> findByRequestIdOrderByCreatedAt(Long requestId);

    Optional<ReferralRequestResponse> findByRequestIdAndReferrerId(Long requestId, Long referrerId);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select rr from ReferralRequestResponse rr where rr.id = :responseId")
        Optional<ReferralRequestResponse> findByIdForUpdate(@Param("responseId") long responseId);

    @Query("select count(rr) from ReferralRequestResponse rr where rr.referrer.id = :referrerId "
            + "and rr.status = 'accepted' and rr.submittedAt is null")
    long countActiveAssignments(@Param("referrerId") long referrerId);

    @Query("select count(rr) from ReferralRequestResponse rr where rr.referrer.id = :referrerId "
            + "and rr.confirmedAt is not null and rr.confirmedAt >= :since")
    long countConfirmedSince(@Param("referrerId") long referrerId, @Param("since") Instant since);

    @Query("select rr from ReferralRequestResponse rr join fetch rr.request r join fetch r.company join fetch r.seeker "
            + "join fetch rr.referrer where rr.status = 'accepted' and rr.submittedAt is null "
            + "and rr.remindersSent < 3 and ((rr.remindersSent = 0 and rr.acceptedAt <= :dueBefore) "
            + "or (rr.remindersSent > 0 and rr.lastRemindedAt <= :dueBefore))")
    List<ReferralRequestResponse> findDueForReminder(@Param("dueBefore") Instant dueBefore);

    @Query("select rr from ReferralRequestResponse rr where rr.status = 'accepted' and rr.submittedAt is null "
            + "and rr.acceptedAt <= :staleBefore")
    List<ReferralRequestResponse> findStaleAccepted(@Param("staleBefore") Instant staleBefore);

        @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ReferralRequestResponse rr set rr.status = 'closed', rr.closedAt = :closedAt "
            + "where rr.status = 'accepted' and rr.submittedAt is null and rr.acceptedAt <= :staleBefore")
    int closeStaleAccepted(@Param("staleBefore") Instant staleBefore, @Param("closedAt") Instant closedAt);
}