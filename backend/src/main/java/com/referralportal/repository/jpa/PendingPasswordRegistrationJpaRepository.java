package com.referralportal.repository.jpa;

import com.referralportal.entity.PendingPasswordRegistration;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PendingPasswordRegistrationJpaRepository
        extends JpaRepository<PendingPasswordRegistration, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select registration from PendingPasswordRegistration registration where registration.email = :email")
    Optional<PendingPasswordRegistration> findByEmailForUpdate(@Param("email") String email);

    @Modifying
    @Transactional
    @Query("delete from PendingPasswordRegistration registration where registration.expiresAt <= :now")
    int deleteExpiredBefore(@Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO pending_password_registrations
              (email, name, password_hash, code_hash, expires_at, attempts, created_at)
            VALUES
              (:email, :name, :passwordHash, :codeHash, :expiresAt, 0, :createdAt)
            ON DUPLICATE KEY UPDATE
              name = VALUES(name),
              password_hash = VALUES(password_hash),
              code_hash = VALUES(code_hash),
              expires_at = VALUES(expires_at),
              attempts = 0,
              created_at = VALUES(created_at)
            """, nativeQuery = true)
    void upsert(@Param("email") String email,
                @Param("name") String name,
                @Param("passwordHash") String passwordHash,
                @Param("codeHash") String codeHash,
                @Param("expiresAt") Instant expiresAt,
                @Param("createdAt") Instant createdAt);
}