package com.referralportal.repository.jpa;

import com.referralportal.entity.OpenRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository backing {@link com.referralportal.repository.CompanyRepository}. */
public interface OpenRoleJpaRepository extends JpaRepository<OpenRole, Long> {

    List<OpenRole> findByCompanyIdOrderById(Long companyId);

    long countByCompanyId(Long companyId);

    Optional<OpenRole> findFirstByCompanyIdAndTitle(Long companyId, String title);
}
