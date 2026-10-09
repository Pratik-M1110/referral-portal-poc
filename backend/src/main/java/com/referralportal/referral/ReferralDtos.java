package com.referralportal.referral;

public final class ReferralDtos {

    private ReferralDtos() {}

    public record ReferralView(long id, String companyName, String referrerName, String roleTitle, String status,
            String updatedAt) {}
}
