package com.referralportal.repository.jpa;

import com.referralportal.entity.EmailOtp;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository backing {@link com.referralportal.repository.ReferrerRepository}. */
public interface EmailOtpJpaRepository extends JpaRepository<EmailOtp, Long> {

    long countByUserIdAndCreatedAtAfter(Long userId, Instant since);

    boolean existsByUserIdAndCreatedAtAfter(Long userId, Instant since);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from EmailOtp e where e.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update EmailOtp e set e.attempts = e.attempts + 1 where e.id = :id")
    void incrementAttempts(@Param("id") long otpId);

    /** Row-locked (FOR UPDATE) read of the most recent OTP for a user; call inside a transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EmailOtp e where e.user.id = :userId order by e.createdAt desc, e.id desc")
    List<EmailOtp> findLatestForUpdate(@Param("userId") long userId, Pageable pageable);
}
