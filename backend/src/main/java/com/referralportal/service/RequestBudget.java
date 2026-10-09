package com.referralportal.service;

import com.referralportal.repository.RequestBudgetRepository;
import org.springframework.stereotype.Service;

@Service
public class RequestBudget {

    public static final int REQUESTS_MAX = 3;

    private final RequestBudgetRepository requests;

    public RequestBudget(RequestBudgetRepository requests) {
        this.requests = requests;
    }

    /** 3 plus credits (always 0 for now) minus meet requests this person started themselves. */
    public int left(long seekerId) {
        long used = requests.countRequestsStartedBy(seekerId);
        long credits = requests.totalCredits(seekerId);
        return (int) Math.max(0, REQUESTS_MAX + credits - used);
    }
}
