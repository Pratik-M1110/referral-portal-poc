package com.referralportal.company;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.referralportal.company.CompanyDtos.CompaniesResponse;

@RestController
class CompanyController {

        private final CompanyService companies;

        CompanyController(CompanyService companies) {
                this.companies = companies;
    }

    @GetMapping("/api/companies")
    CompaniesResponse list(Authentication auth) {
        return companies.list(auth);
    }

    @PutMapping("/api/wishlist/{companyId}")
    Map<String, Object> add(Authentication auth, @PathVariable long companyId) {
                return companies.addToWishlist(auth, companyId);
    }

    @DeleteMapping("/api/wishlist/{companyId}")
    Map<String, Object> remove(Authentication auth, @PathVariable long companyId) {
                return companies.removeFromWishlist(auth, companyId);
    }
}
