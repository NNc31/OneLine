package com.nefodov.oneline.config;

import com.nefodov.oneline.security.MagicLinkAuthenticationFilter;
import com.nefodov.oneline.support.OneLineProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import java.net.URI;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, MagicLinkAuthenticationFilter authFilter, OneLineProperties properties) {
        String storageOrigin = origin(properties.storage().publicEndpoint());
        String csp = "default-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'; img-src 'self' blob:; connect-src 'self' " + storageOrigin;

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .referrerPolicy(rp -> rp.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .contentSecurityPolicy(csp2 -> csp2.policyDirectives(csp)))
                .exceptionHandling(eh -> eh.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/chats/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/chats").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/chats/*/join").permitAll()
                        .requestMatchers("/api/chats/*/**").authenticated()
                        .anyRequest().permitAll()
                )
                .addFilterBefore(authFilter, AuthorizationFilter.class)
                .build();
    }

    private static String origin(String endpoint) {
        URI uri = URI.create(endpoint);
        StringBuilder origin = new StringBuilder(uri.getScheme()).append("://").append(uri.getHost());
        if (uri.getPort() != -1) {
            origin.append(':').append(uri.getPort());
        }
        return origin.toString();
    }
}
