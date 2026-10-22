package com.referralportal.repository.jpa;

import com.referralportal.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository backing {@link com.referralportal.repository.ProfileRepository}. */
public interface ProfileJpaRepository extends JpaRepository<Profile, Long> {
}
