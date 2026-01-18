package com.nefodov.oneline.config;

import com.nefodov.oneline.message.Aes256GcmMessageContentCodec;
import com.nefodov.oneline.message.MessageContentCodec;
import com.nefodov.oneline.message.PlainTextMessageContentCodec;
import com.nefodov.oneline.support.OneLineProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessageCryptoConfig {

    @Bean
    public MessageContentCodec messageContentCodec(OneLineProperties properties) {
        OneLineProperties.Crypto.Algorithm algorithm = properties.crypto().algorithm();
        return switch (algorithm) {
            case AES_GCM -> new Aes256GcmMessageContentCodec();
            case PLAINTEXT -> new PlainTextMessageContentCodec();
        };
    }
}
