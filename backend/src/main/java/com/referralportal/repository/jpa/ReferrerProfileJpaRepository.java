package com.referralportal.repository.jpa;

import com.referralportal.entity.ReferrerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository backing {@link com.referralportal.repository.ReferrerRepository} and
 * {@link com.referralportal.repository.MeRepository}. */
public interface ReferrerProfileJpaRepository extends JpaRepository<ReferrerProfile, Long> {

    boolean existsByWorkEmailAndUserIdNot(String workEmail, Long userId);
}
