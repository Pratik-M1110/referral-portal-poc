package com.referralportal.service;

import com.referralportal.repository.jpa.UserJpaRepository;
import com.referralportal.user.AppUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads email/password accounts for authentication. OAuth-only accounts (no password hash) are
 * rejected here, which DaoAuthenticationProvider reports as bad credentials — so the login form
 * cannot be used to tell which emails exist.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    @Autowired
    private UserJpaRepository users;

    @Override
    public UserDetails loadUserByUsername(String username) {
        String email = username == null ? "" : username.trim().toLowerCase();
        return users.findByEmail(email)
                .filter(user -> user.getPasswordHash() != null)
                .map(user -> new AppUserDetails(user.getId(), user.getEmail(), user.getName(),
                        user.getPasswordHash(), user.isAdmin()))
                .orElseThrow(() -> new UsernameNotFoundException("No password account for this email."));
    }
}
