package com.referralportal.repository.jpa;

import com.referralportal.entity.Wishlist;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA repository backing {@link com.referralportal.repository.CompanyRepository}. */
public interface WishlistJpaRepository extends JpaRepository<Wishlist, Wishlist.Id> {

    List<Wishlist> findBySeekerId(Long seekerId);

    long countBySeekerId(Long seekerId);

    boolean existsBySeekerIdAndCompanyId(Long seekerId, Long companyId);

    void deleteBySeekerIdAndCompanyId(Long seekerId, Long companyId);
}
