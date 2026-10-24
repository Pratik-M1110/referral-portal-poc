package com.referralportal.repository.jpa;

import com.referralportal.entity.Company;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Spring Data JPA repository backing {@link com.referralportal.repository.CompanyRepository} and
 * {@link com.referralportal.repository.ReferrerRepository}. */
public interface CompanyJpaRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByEmailDomain(String emailDomain);

    List<Company> findAllByOrderBySponsorDescNameAsc();

    @Query("select distinct c from Company c join ReferrerProfile rp on rp.company = c "
            + "where rp.verifiedAt is not null and rp.referrer = true and rp.acceptsReferralRequests = true "
            + "order by c.sponsor desc, c.name asc")
    List<Company> findAllAcceptingReferralRequests();
}
