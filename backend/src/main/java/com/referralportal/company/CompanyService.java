package com.referralportal.company;

import com.referralportal.user.CurrentUser;
import com.referralportal.user.UserService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static com.referralportal.company.CompanyDtos.CompaniesResponse;

@Service
class CompanyService {

    static final int WISHLIST_MAX = 5;

    private final UserService users;
    private final CompanyRepository companies;
    private final RequestBudget budget;

    CompanyService(UserService users, CompanyRepository companies, RequestBudget budget) {
        this.users = users;
        this.companies = companies;
        this.budget = budget;
    }

    CompaniesResponse list(Authentication auth) {
        CurrentUser user = users.current(auth);
        Set<Long> wishlisted = companies.findWishlistedCompanyIds(user.id());
        List<CompanyDtos.CompanyView> items = companies.findCompanies(user.id(), wishlisted);
        return new CompaniesResponse(wishlisted.size(), WISHLIST_MAX, budget.left(user.id()), RequestBudget.REQUESTS_MAX, items);
    }

    Map<String, Object> addToWishlist(Authentication auth, long companyId) {
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

    Map<String, Object> removeFromWishlist(Authentication auth, long companyId) {
        CurrentUser user = users.current(auth);
        companies.removeFromWishlist(user.id(), companyId);
        return Map.of("wishlisted", false);
    }
}
