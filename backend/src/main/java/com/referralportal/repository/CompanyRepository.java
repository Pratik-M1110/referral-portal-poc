package com.referralportal.repository;

import com.referralportal.common.ChipLists;
import com.referralportal.entity.Company;
import com.referralportal.entity.OpenRole;
import com.referralportal.entity.ReferrerProfile;
import com.referralportal.entity.User;
import com.referralportal.entity.Wishlist;
import com.referralportal.repository.jpa.CompanyJpaRepository;
import com.referralportal.repository.jpa.ConversationJpaRepository;
import com.referralportal.repository.jpa.OpenRoleJpaRepository;
import com.referralportal.repository.jpa.ReferrerProfileJpaRepository;
import com.referralportal.repository.jpa.UserJpaRepository;
import com.referralportal.repository.jpa.WishlistJpaRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import static com.referralportal.dto.CompanyDtos.CompanyView;
import static com.referralportal.dto.CompanyDtos.Person;
import static com.referralportal.dto.CompanyDtos.RoleView;

@Repository
public class CompanyRepository {

    private static final List<String> ACTIVE_STATUSES = List.of("accepted", "completed");

    @Autowired
    private CompanyJpaRepository companies;
    @Autowired
    private OpenRoleJpaRepository openRoles;
    @Autowired
    private ReferrerProfileJpaRepository referrerProfiles;
    @Autowired
    private WishlistJpaRepository wishlists;
    @Autowired
    private ConversationJpaRepository conversations;
    @Autowired
    private UserJpaRepository users;

    public Set<Long> findWishlistedCompanyIds(long seekerId) {
        return wishlists.findBySeekerId(seekerId).stream().map(w -> w.getCompany().getId()).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public List<CompanyView> findCompanies(long seekerId, Set<Long> wishlistedIds) {
        List<CompanyView> result = new ArrayList<>();
        List<ReferrerProfile> openReferrers = referrerProfiles.findAll().stream()
                .filter(rp -> rp.getVerifiedAt() != null && rp.isReferrer() && rp.isOpenToMeet() && rp.getUserId() != seekerId)
                .toList();
        for (Company company : companies.findAllByOrderBySponsorDescNameAsc()) {
            List<RoleView> roles = openRoles.findByCompanyIdOrderById(company.getId()).stream()
                    .map(CompanyRepository::roleView).toList();
            List<Person> people = openReferrers.stream()
                    .filter(rp -> rp.getCompany().getId().equals(company.getId()))
                    .sorted(Comparator.comparing(rp -> rp.getUser().getName()))
                    .map(rp -> {
                        long used = conversations.countByReferrerIdAndStatusIn(rp.getUserId(), ACTIVE_STATUSES);
                        return new Person(rp.getUserId(), rp.getUser().getName(), rp.getJobTitle(),
                                used >= rp.getSlotCapacity());
                    })
                    .toList();
            result.add(new CompanyView(company.getId(), company.getName(), company.getEmailDomain(),
                    company.getBoothLabel(), company.isSponsor(), wishlistedIds.contains(company.getId()), roles, people));
        }
        return result;
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

    private static RoleView roleView(OpenRole role) {
        return new RoleView(role.getId(), role.getTitle(), role.getLevel(), ChipLists.fromJson(role.getSkills()));
    }
}
