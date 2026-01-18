package com.nefodov.oneline.support;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

@Component
public class HkdfKeyDerivation {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int HASH_LENGTH = 32;

    public byte[] derive(byte[] ikm, byte[] salt, byte[] info, int length) {
        if (length <= 0 || length > 255 * HASH_LENGTH) {
            throw new IllegalArgumentException("Invalid HKDF output length: " + length);
        }
        try {
            byte[] prk = hmac(salt == null || salt.length == 0 ? new byte[HASH_LENGTH] : salt, ikm);
            return expand(prk, info == null ? new byte[0] : info, length);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HKDF derivation failed", e);
        }
    }

    public byte[] deriveChatMessageKey(String chatToken, long chatId) {
        byte[] ikm = chatToken.getBytes(StandardCharsets.UTF_8);
        byte[] salt = ByteBuffer.allocate(Long.BYTES).putLong(chatId).array();
        byte[] info = "oneline.message.v1".getBytes(StandardCharsets.UTF_8);
        return derive(ikm, salt, info, 32);
    }

    private byte[] expand(byte[] prk, byte[] info, int length) throws GeneralSecurityException {
        int blocks = (length + HASH_LENGTH - 1) / HASH_LENGTH;
        byte[] result = new byte[length];
        byte[] previous = new byte[0];
        int written = 0;
        for (int i = 1; i <= blocks; i++) {
            byte[] input = new byte[previous.length + info.length + 1];
            System.arraycopy(previous, 0, input, 0, previous.length);
            System.arraycopy(info, 0, input, previous.length, info.length);
            input[input.length - 1] = (byte) i;
            previous = hmac(prk, input);
            int copy = Math.min(HASH_LENGTH, length - written);
            System.arraycopy(previous, 0, result, written, copy);
            written += copy;
        }
        return result;
    }

    private byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
        return mac.doFinal(data);
    }
}
