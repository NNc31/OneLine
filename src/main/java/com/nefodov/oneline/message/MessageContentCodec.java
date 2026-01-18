package com.nefodov.oneline.message;

public interface MessageContentCodec {

    byte[] encode(byte[] key, String plaintext);

    String decode(byte[] key, byte[] stored);
}
