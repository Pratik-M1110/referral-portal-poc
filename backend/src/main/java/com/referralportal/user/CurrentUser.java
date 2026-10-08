package com.referralportal.user;

public record CurrentUser(long id, String name, String email, String provider) {}
