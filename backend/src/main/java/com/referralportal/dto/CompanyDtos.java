package com.referralportal.dto;

import java.util.List;

public final class CompanyDtos {

    private CompanyDtos() {}

    public record Person(long userId, String name, String jobTitle, boolean fullyBooked) {}

    public record RoleView(long id, String title, String level, List<String> skills) {}

    public record CompanyView(long id, String name, String domain, String boothLabel, boolean sponsor, boolean wishlisted,
            List<RoleView> roles, List<Person> people) {}

    public record CompaniesResponse(int wishlistCount, int wishlistMax, int requestsLeft, int requestsMax,
            List<CompanyView> companies) {}
}
