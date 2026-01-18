package com.nefodov.oneline.message;

import java.nio.charset.StandardCharsets;

public class PlainTextMessageContentCodec implements MessageContentCodec {

    @Override
    public byte[] encode(byte[] key, String plaintext) {
        return plaintext.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String decode(byte[] key, byte[] stored) {
        return new String(stored, StandardCharsets.UTF_8);
    }
}
