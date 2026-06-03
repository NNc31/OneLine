package com.nefodov.oneline.security;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class TokenHasherTest {

    private final TokenHasher hasher = new TokenHasher();

    @Test
    void emits32ByteSha256Digest() {
        assertEquals(32, hasher.hash("anything").length);
    }

    @Test
    void isDeterministic() {
        assertArrayEquals(hasher.hash("same-input"), hasher.hash("same-input"));
    }

    @Test
    void distinguishesDifferentInputs() {
        assertFalse(Arrays.equals(hasher.hash("a"), hasher.hash("b")));
    }
}
