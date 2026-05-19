package com.nefodov.oneline.support;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenGeneratorTest {

    private static final Pattern URL_SAFE_BASE64 = Pattern.compile("^[A-Za-z0-9_-]+$");

    private final TokenGenerator generator = new TokenGenerator();

    @Test
    void emitsUrlSafeBase64Strings() {
        for (int i = 0; i < 50; i++) {
            String token = generator.newToken();
            assertTrue(URL_SAFE_BASE64.matcher(token).matches(), () -> "token " + token + " is not URL-safe base64");
        }
    }

    @Test
    void emitsUniqueTokens() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            seen.add(generator.newToken());
        }
        assertEquals(10_000, seen.size());
    }

    @Test
    void carriesAtLeast256BitsOfEntropy() {
        assertEquals(43, generator.newToken().length());
    }
}
