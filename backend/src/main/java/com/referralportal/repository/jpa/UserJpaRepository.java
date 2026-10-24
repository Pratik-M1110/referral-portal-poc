package com.referralportal.repository.jpa;

import com.referralportal.entity.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository backing {@link com.referralportal.repository.UserRepository}. */
public interface UserJpaRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") long userId);

    /**
     * Bulk-deletes the user row directly via JPQL instead of load-then-remove. This avoids
     * Hibernate eagerly loading the user's one-to-one associations (badge, profile,
     * referrer profile, resume file) into the persistence context before deletion, which can
     * trip Hibernate's transient-reference checks on an otherwise unrelated flush.
     */
    @Modifying
    @Query("delete from User u where u.id = :id")
    int deleteUserById(Long id);
}
