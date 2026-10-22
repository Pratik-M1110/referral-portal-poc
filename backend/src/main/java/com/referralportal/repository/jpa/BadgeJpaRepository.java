package com.referralportal.repository.jpa;

import com.referralportal.entity.Badge;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository backing {@link com.referralportal.repository.BadgeRepository}. */
public interface BadgeJpaRepository extends JpaRepository<Badge, Long> {
}
