package com.referralportal.controller;

import com.referralportal.service.CompanyService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import static com.referralportal.dto.CompanyDtos.CompaniesResponse;

@RestController
@RequestMapping("/api")
public class CompanyController {

    @Autowired
    private CompanyService companies;

    @GetMapping("/companies")
    public CompaniesResponse list(Authentication auth) {
        return companies.list(auth);
    }

    @PutMapping("/wishlist/{companyId}")
    public Map<String, Object> add(Authentication auth, @PathVariable long companyId) {
                return companies.addToWishlist(auth, companyId);
    }

    @DeleteMapping("/wishlist/{companyId}")
    public Map<String, Object> remove(Authentication auth, @PathVariable long companyId) {
                return companies.removeFromWishlist(auth, companyId);
    }
}
