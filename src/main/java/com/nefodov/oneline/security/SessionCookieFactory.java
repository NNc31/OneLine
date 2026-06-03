package com.nefodov.oneline.security;

import com.nefodov.oneline.config.OneLineProperties;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class SessionCookieFactory {

    private final OneLineProperties properties;

    public ResponseCookie build(String sessionToken) {
        OneLineProperties.Session s = properties.session();
        return ResponseCookie.from(s.cookieName(), sessionToken)
                .httpOnly(true)
                .secure(s.cookieSecure())
                .sameSite(s.cookieSameSite())
                .path("/")
                .maxAge(s.cookieMaxAge())
                .build();
    }
}
