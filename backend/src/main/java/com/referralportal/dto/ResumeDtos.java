package com.referralportal.dto;

public final class ResumeDtos {

    private ResumeDtos() {}

    /** Shown on the profile so the seeker sees their current resume without downloading it. */
    public record ResumeInfo(String filename, String contentType, long sizeBytes, String uploadedAt) {}
}
