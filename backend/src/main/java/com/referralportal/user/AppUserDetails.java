package com.referralportal.user;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * UserDetails for email/password accounts. Carries the user row's identity so the session
 * principal can be mapped back to a user without an extra lookup (see UserService#current).
 */
public class AppUserDetails implements UserDetails, java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private final long id;
    private final String email;
    private final String name;
    private final String passwordHash;
    private final boolean admin;

    public AppUserDetails(long id, String email, String name, String passwordHash, boolean admin) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.passwordHash = passwordHash;
        this.admin = admin;
    }

    public long id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String name() {
        return name;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return admin
                ? List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"))
                : List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
