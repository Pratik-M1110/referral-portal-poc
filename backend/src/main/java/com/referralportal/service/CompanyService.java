package com.referralportal.service;

import com.referralportal.dto.CompanyDtos;
import com.referralportal.repository.CompanyRepository;
import com.referralportal.user.CurrentUser;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static com.referralportal.dto.CompanyDtos.CompaniesResponse;

@Service
public class CompanyService {

    static final int WISHLIST_MAX = 5;

    @Autowired
    private UserService users;
    @Autowired
    private CompanyRepository companies;
    @Autowired
    private ReferralRequestBudget requestBudget;

    public CompaniesResponse list(Authentication auth) {
        CurrentUser user = users.current(auth);
        Set<Long> wishlisted = companies.findWishlistedCompanyIds(user.id());
        List<CompanyDtos.CompanyView> items = companies.findCompanies(user.id(), wishlisted);
        ReferralRequestBudget.Allowance allowance = requestBudget.allowance(user.id(), java.time.Instant.now());
        return new CompaniesResponse(wishlisted.size(), WISHLIST_MAX, allowance.left(), allowance.baseMax(),
            allowance.bonusAvailable(), items);
    }

    public Map<String, Object> addToWishlist(Authentication auth, long companyId) {
        CurrentUser user = users.current(auth);
        if (!companies.existsById(companyId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found.");
        }
        if (companies.isVerifiedOwnCompany(user.id(), companyId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot wishlist your own company.");
        }
        if (!companies.isWishlisted(user.id(), companyId)) {
            if (companies.countWishlistItems(user.id()) >= WISHLIST_MAX) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Your wishlist is full (" + WISHLIST_MAX + " companies).");
            }
            companies.addToWishlist(user.id(), companyId);
        }
        return Map.of("wishlisted", true);
    }

    public Map<String, Object> removeFromWishlist(Authentication auth, long companyId) {
        CurrentUser user = users.current(auth);
        companies.removeFromWishlist(user.id(), companyId);
        return Map.of("wishlisted", false);
    }
}
