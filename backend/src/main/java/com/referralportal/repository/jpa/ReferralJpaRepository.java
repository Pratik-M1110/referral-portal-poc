package com.referralportal.repository.jpa;

import com.referralportal.entity.Referral;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository backing {@link com.referralportal.repository.ReferralRepository}. */
public interface ReferralJpaRepository extends JpaRepository<Referral, Long> {

    @Query("select r from Referral r join fetch r.company join fetch r.referrer left join fetch r.role "
            + "where r.candidate.id = :candidateId order by r.updatedAt desc")
    List<Referral> findReceivedByCandidateId(@Param("candidateId") long candidateId);

    @Modifying
    @Query("delete from Referral r where r.referrer.id = :userId or r.candidate.id = :userId")
    void deleteByReferrerIdOrCandidateId(@Param("userId") long userId);

    boolean existsByReferrerIdAndCandidateIdAndCompanyId(long referrerId, long candidateId, long companyId);
}
