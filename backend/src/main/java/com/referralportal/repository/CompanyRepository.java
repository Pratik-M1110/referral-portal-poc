package com.referralportal.repository;

import com.referralportal.entity.Company;
import com.referralportal.entity.User;
import com.referralportal.entity.Wishlist;
import com.referralportal.repository.jpa.CompanyJpaRepository;
import com.referralportal.repository.jpa.ReferrerProfileJpaRepository;
import com.referralportal.repository.jpa.UserJpaRepository;
import com.referralportal.repository.jpa.WishlistJpaRepository;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import static com.referralportal.dto.CompanyDtos.CompanyView;

@Repository
public class CompanyRepository {

    @Autowired
    private CompanyJpaRepository companies;
    @Autowired
    private ReferrerProfileJpaRepository referrerProfiles;
    @Autowired
    private WishlistJpaRepository wishlists;
    @Autowired
    private UserJpaRepository users;

    public Set<Long> findWishlistedCompanyIds(long seekerId) {
        return wishlists.findBySeekerId(seekerId).stream().map(w -> w.getCompany().getId()).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public List<CompanyView> findCompanies(long seekerId, Set<Long> wishlistedIds) {
        return companies.findAllAcceptingReferralRequests().stream()
            .filter(company -> !isVerifiedOwnCompany(seekerId, company.getId()))
            .map(company -> new CompanyView(company.getId(), company.getName(), company.getEmailDomain(),
                company.isSponsor(), wishlistedIds.contains(company.getId())))
            .toList();
    }

    public boolean existsById(long companyId) {
        return companies.existsById(companyId);
    }

    public boolean isVerifiedOwnCompany(long userId, long companyId) {
        return referrerProfiles.findById(userId)
                .map(rp -> rp.getVerifiedAt() != null && rp.getCompany().getId() == companyId)
                .orElse(false);
    }

    public boolean isWishlisted(long seekerId, long companyId) {
        return wishlists.existsBySeekerIdAndCompanyId(seekerId, companyId);
    }

    public long countWishlistItems(long seekerId) {
        return wishlists.countBySeekerId(seekerId);
    }

    public void addToWishlist(long seekerId, long companyId) {
        if (wishlists.existsBySeekerIdAndCompanyId(seekerId, companyId)) {
            return; // mirrors the previous INSERT IGNORE semantics
        }
        User user = users.getReferenceById(seekerId);
        Company company = companies.getReferenceById(companyId);
        Wishlist wishlist = new Wishlist();
        wishlist.setSeeker(user);
        wishlist.setCompany(company);
        wishlist.setCreatedAt(java.time.Instant.now());
        wishlists.save(wishlist);
    }

    public void removeFromWishlist(long seekerId, long companyId) {
        wishlists.deleteBySeekerIdAndCompanyId(seekerId, companyId);
    }

}
