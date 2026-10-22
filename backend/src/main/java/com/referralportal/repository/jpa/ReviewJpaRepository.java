package com.referralportal.repository.jpa;

import com.referralportal.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data JPA repository backing {@link com.referralportal.repository.UserRepository} (account deletion). */
public interface ReviewJpaRepository extends JpaRepository<Review, Review.Id> {

    @Modifying
    @Query("delete from Review r where r.referrer.id = :userId or r.candidate.id = :userId")
    void deleteByReferrerIdOrCandidateId(@Param("userId") long userId);
}
