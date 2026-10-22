package com.referralportal.service;

import com.referralportal.repository.UserRepository;
import com.referralportal.user.CurrentUser;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AccountService {

    @Autowired
    private UserService users;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TransactionTemplate transactions;

    public void deleteCurrent(Authentication authentication) {
        CurrentUser user = users.current(authentication);
        Objects.requireNonNull(transactions.execute(status -> {
            userRepository.deleteAccount(user.id());
            return Boolean.TRUE;
        }));
    }
}