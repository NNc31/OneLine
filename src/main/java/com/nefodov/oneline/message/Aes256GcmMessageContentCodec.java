package com.nefodov.oneline.message;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

public class Aes256GcmMessageContentCodec implements MessageContentCodec {

    static final byte VERSION = 0x01;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final int KEY_LENGTH_BYTES = 32;
    private static final int NONCE_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecureRandom random = new SecureRandom();

    @Override
    public byte[] encode(byte[] key, String plaintext) {
        validateKey(key);
        byte[] nonce = new byte[NONCE_LENGTH_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, KEY_ALGORITHM), new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] output = new byte[1 + NONCE_LENGTH_BYTES + ciphertext.length];
            output[0] = VERSION;
            System.arraycopy(nonce, 0, output, 1, NONCE_LENGTH_BYTES);
            System.arraycopy(ciphertext, 0, output, 1 + NONCE_LENGTH_BYTES, ciphertext.length);
            return output;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    @Override
    public String decode(byte[] key, byte[] stored) {
        validateKey(key);
        if (stored == null || stored.length < 1 + NONCE_LENGTH_BYTES + TAG_LENGTH_BITS / 8) {
            throw new IllegalStateException("Stored ciphertext is too short");
        }
        if (stored[0] != VERSION) {
            throw new IllegalStateException("Unsupported message content version: " + stored[0]);
        }
        byte[] nonce = new byte[NONCE_LENGTH_BYTES];
        System.arraycopy(stored, 1, nonce, 0, NONCE_LENGTH_BYTES);
        int cipherOffset = 1 + NONCE_LENGTH_BYTES;
        int cipherLength = stored.length - cipherOffset;
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, KEY_ALGORITHM), new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            byte[] plaintext = cipher.doFinal(stored, cipherOffset, cipherLength);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM decryption failed", e);
        }
    }

    private static void validateKey(byte[] key) {
        if (key == null || key.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException("AES-256 key must be 32 bytes");
        }
    }
}
