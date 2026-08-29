package com.karvya.store.infrastructure.security;

import com.karvya.store.domain.model.AppUser;
import com.karvya.store.domain.repository.AppUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    public AppUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = users.findByEmailNormalized(AppUser.normalizeEmail(username))
                .orElseThrow(() -> new UsernameNotFoundException("No account for that identifier"));
        return new AppUserPrincipal(user);
    }
}
