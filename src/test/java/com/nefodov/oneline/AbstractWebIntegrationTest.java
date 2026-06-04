package com.nefodov.oneline;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;

public abstract class AbstractWebIntegrationTest {

    protected static final String CHAT_TOKEN_HEADER = "X-Chat-Token";

    @Autowired
    protected TestRestTemplate restTemplate;

    private String cachedCsrfToken;

    protected final String csrfToken() {
        if (cachedCsrfToken == null) {
            ResponseEntity<String> resp = restTemplate.exchange("/", HttpMethod.GET, HttpEntity.EMPTY, String.class);
            cachedCsrfToken = resp.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream()
                    .filter(c -> c.startsWith("XSRF-TOKEN="))
                    .findFirst()
                    .map(c -> {
                        int eq = c.indexOf('=');
                        int semi = c.indexOf(';', eq);
                        return c.substring(eq + 1, semi < 0 ? c.length() : semi);
                    })
                    .orElseThrow(() -> new IllegalStateException("XSRF-TOKEN cookie not set on GET /"));
        }
        return cachedCsrfToken;
    }

    protected final HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        String token = csrfToken();
        headers.add("X-XSRF-TOKEN", token);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + token);
        return headers;
    }

    protected final HttpHeaders jsonHeadersWithChatToken(String chatToken) {
        HttpHeaders headers = jsonHeaders();
        headers.add(CHAT_TOKEN_HEADER, chatToken);
        return headers;
    }

    protected final HttpHeaders jsonHeadersWithChatTokenAndSession(String chatToken, String sessionCookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        String token = csrfToken();
        headers.add(CHAT_TOKEN_HEADER, chatToken);
        headers.add("X-XSRF-TOKEN", token);
        headers.add(HttpHeaders.COOKIE, sessionCookie + "; XSRF-TOKEN=" + token);
        return headers;
    }

    protected static String extractCookie(String setCookieHeader) {
        int semi = setCookieHeader.indexOf(';');
        return semi < 0 ? setCookieHeader : setCookieHeader.substring(0, semi);
    }
}
