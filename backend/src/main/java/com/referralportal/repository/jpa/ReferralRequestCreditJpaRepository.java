package com.referralportal.repository.jpa;

import com.referralportal.entity.ReferralRequestCredit;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReferralRequestCreditJpaRepository extends JpaRepository<ReferralRequestCredit, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ReferralRequestCredit c where c.user.id = :userId and c.consumedByRequest is null "
            + "and c.expiresAt > :now order by c.expiresAt asc")
    List<ReferralRequestCredit> findAvailableForUpdate(@Param("userId") long userId, @Param("now") Instant now);

    Optional<ReferralRequestCredit> findBySourceResponseId(Long responseId);

        @Query("select c from ReferralRequestCredit c where c.consumedByRequest.id = :requestId")
        Optional<ReferralRequestCredit> findConsumedByRequestId(@Param("requestId") long requestId);

    @Query("select count(c) from ReferralRequestCredit c where c.user.id = :userId and c.createdAt >= :since")
    long countEarnedSince(@Param("userId") long userId, @Param("since") Instant since);

    @Query("select count(c) from ReferralRequestCredit c where c.user.id = :userId "
            + "and c.consumedByRequest is null and c.expiresAt > :now")
    long countAvailable(@Param("userId") long userId, @Param("now") Instant now);
}