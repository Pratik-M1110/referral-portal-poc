package com.referralportal.user;

import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AccountService {

    private final UserService users;
    private final UserRepository userRepository;
    private final TransactionTemplate transactions;

    AccountService(UserService users, UserRepository userRepository, TransactionTemplate transactions) {
        this.users = users;
        this.userRepository = userRepository;
        this.transactions = transactions;
    }

    public void deleteCurrent(Authentication authentication) {
        CurrentUser user = users.current(authentication);
        Objects.requireNonNull(transactions.execute(status -> {
            userRepository.deleteAccount(user.id());
            return Boolean.TRUE;
        }));
    }
}