package com.referralportal.repository.jpa;

import com.referralportal.entity.ReferrerProfile;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository backing {@link com.referralportal.repository.ReferrerRepository} and
 * {@link com.referralportal.repository.MeRepository}. */
public interface ReferrerProfileJpaRepository extends JpaRepository<ReferrerProfile, Long> {

    boolean existsByWorkEmailAndUserIdNot(String workEmail, Long userId);

    List<ReferrerProfile> findByCompany_IdAndVerifiedAtIsNotNullAndReferrerTrueAndAcceptsReferralRequestsTrue(Long companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select rp from ReferrerProfile rp where rp.userId = :userId")
    java.util.Optional<ReferrerProfile> findByUserIdForUpdate(@Param("userId") long userId);
}
