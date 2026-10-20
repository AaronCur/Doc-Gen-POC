package com.example.letters.security;

import static com.example.letters.security.Roles.ADMIN;
import static com.example.letters.security.Roles.EXTERNAL;
import static com.example.letters.security.Roles.STAFF;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * POC security: in-memory users and HTTP Basic. Every endpoint's role is declared here, in one
 * place, and checked on the server regardless of what the UI hides. Anything not listed is denied.
 *
 * <p>See the README section "Swapping in the real identity provider".
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Let Spring Boot's error page render the real status for errors raised
                        // outside MVC handlers instead of turning them into 403.
                        .requestMatchers("/error").permitAll()

                        // OpenAPI document and Swagger UI. They are only served when springdoc is
                        // enabled (dev profile); otherwise these paths return 404. The API calls made
                        // from Swagger UI still need credentials.
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

                        .requestMatchers(HttpMethod.GET, "/api/letter-types").hasAnyRole(ADMIN, STAFF)
                        .requestMatchers(HttpMethod.GET, "/api/letter-types/*/eligible-count").hasRole(ADMIN)

                        .requestMatchers(HttpMethod.GET, "/api/letter-runs", "/api/letter-runs/*", "/api/letter-runs/*/pending")
                        .hasAnyRole(ADMIN, STAFF)
                        .requestMatchers(HttpMethod.POST, "/api/letter-runs").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/letter-runs/*/release").hasRole(STAFF)

                        .requestMatchers(HttpMethod.GET, "/api/external/applications/*/documents", "/api/external/documents/*")
                        .hasRole(EXTERNAL)
                        .requestMatchers(HttpMethod.GET, "/api/staff/applications/*/documents").hasRole(STAFF)
                        .requestMatchers(HttpMethod.DELETE, "/api/staff/documents/*").hasRole(STAFF)

                        .anyRequest().denyAll())
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // POC ONLY: CSRF is disabled because this stateless API is called with HTTP Basic
                // from the Angular dev server. Revisit for the real IdP and session model: with
                // cookie-based sessions, CSRF protection must be re-enabled.
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    /**
     * POC ONLY: mock users with plain-text passwords ({noop}). Replace with the real identity
     * provider; nothing else in the application depends on how users are stored.
     */
    @Bean
    UserDetailsService users() {
        return new InMemoryUserDetailsManager(
                User.withUsername("admin").password("{noop}admin").roles(ADMIN, STAFF).build(),
                User.withUsername("staff").password("{noop}staff").roles(STAFF).build(),
                User.withUsername("external").password("{noop}external").roles(EXTERNAL).build());
    }
}
