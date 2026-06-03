package com.nefodov.oneline.ratelimit;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;

@Configuration
public class RateLimitConfig {

    @Bean(destroyMethod = "shutdown")
    public RedisClient rateLimitRedisClient(LettuceConnectionFactory factory) {
        RedisURI.Builder uri = RedisURI.builder()
                .withHost(factory.getHostName())
                .withPort(factory.getPort())
                .withSsl(factory.isUseSsl())
                .withDatabase(factory.getDatabase());

        RedisStandaloneConfiguration standalone = factory.getStandaloneConfiguration();
        String username = standalone.getUsername();
        standalone.getPassword().toOptional().ifPresent(password -> {
            if (username != null && !username.isBlank()) {
                uri.withAuthentication(username, password);
            } else {
                uri.withPassword(password);
            }
        });
        return RedisClient.create(uri.build());
    }

    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<byte[], byte[]> rateLimitRedisConnection(RedisClient client) {
        return client.connect(ByteArrayCodec.INSTANCE);
    }

    @Bean
    public ProxyManager<byte[]> rateLimitProxyManager(StatefulRedisConnection<byte[], byte[]> connection) {
        return LettuceBasedProxyManager.builderFor(connection).withExpirationStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(2))).build();
    }
}
