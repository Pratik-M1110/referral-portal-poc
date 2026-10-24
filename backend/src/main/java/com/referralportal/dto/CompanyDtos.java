package com.referralportal.dto;

import java.util.List;

public final class CompanyDtos {

    private CompanyDtos() {}

    public record CompanyView(long id, String name, String domain, boolean sponsor, boolean wishlisted) {}

        public record CompaniesResponse(int wishlistCount, int wishlistMax, int referralRequestsLeft,
            int referralRequestsMax, int referralBonusCredits, List<CompanyView> companies) {}
}
