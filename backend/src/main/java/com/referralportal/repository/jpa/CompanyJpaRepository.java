package com.referralportal.repository.jpa;

import com.referralportal.entity.Company;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository backing {@link com.referralportal.repository.CompanyRepository} and
 * {@link com.referralportal.repository.ReferrerRepository}. */
public interface CompanyJpaRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByEmailDomain(String emailDomain);

    List<Company> findAllByOrderBySponsorDescNameAsc();
}
