package com.karvya.store.infrastructure.config;

import com.karvya.store.infrastructure.security.AppUserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karvya.store.infrastructure.security.CsrfCookieFilter;
import com.karvya.store.infrastructure.security.PasswordChangeRequiredFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Security wiring for the whole application.
 *
 * <p>Sessions in an HttpOnly cookie rather than bearer tokens, so disabling an
 * account or changing a password takes effect immediately rather than when a
 * token happens to expire. Unauthenticated API calls answer 401 rather than
 * redirecting to a login page, because every consumer is a fetch call to which
 * a 302 carrying HTML is useless.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // delegating, so the algorithm can be upgraded without a data migration
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AppUserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // always run the hash comparison, even for an unknown user, so response
        // time does not reveal whether the address is registered
        provider.setHideUserNotFoundExceptions(true);
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        // opt out of the BREACH-mitigating deferred token, which a fetch client
        // cannot resolve on its own
        csrfHandler.setCsrfRequestAttributeName(null);

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfHandler))

                .addFilterAfter(new CsrfCookieFilter(), LogoutFilter.class)

                // runs after authentication, so it can see who the caller is
                .addFilterAfter(new PasswordChangeRequiredFilter(objectMapper),
                        org.springframework.security.web.access.intercept.AuthorizationFilter.class)

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId()))

                .authorizeHttpRequests(auth -> auth
                        // public catalogue
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/products/**",
                                "/api/v1/categories",
                                // guest confirmation: the opaque token in the
                                // query string is what authorises it, not a session
                                "/api/v1/orders/*",
                                "/api/v1/settings/public",
                                "/api/v1/payment-methods").permitAll()

                        // sign-in and account recovery
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/password/forgot",
                                "/api/v1/auth/password/reset").permitAll()

                        // guest checkout and enquiries need no account
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/cart/validate",
                                "/api/v1/orders",
                                "/api/v1/enquiries").permitAll()

                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/media/**", "/sitemap.xml", "/robots.txt").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/account/**").authenticated()

                        .anyRequest().authenticated())

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))

                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())

                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                        .ReferrerPolicy.SAME_ORIGIN)));

        return http.build();
    }
}
