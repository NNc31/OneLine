package com.nefodov.oneline.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketLifecycleArgs;
import io.minio.messages.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@Configuration
public class StorageConfig {

    private static final String FIXED_REGION = "us-east-1";

    @Bean
    public MinioClient minioClient(OneLineProperties properties) {
        OneLineProperties.Storage storage = properties.storage();
        MinioClient client = MinioClient.builder()
                .endpoint(storage.endpoint())
                .credentials(storage.accessKey(), storage.secretKey())
                .region(FIXED_REGION)
                .build();
        ensureBucket(client, storage.bucket());
        applyLifecycle(client, properties);
        return client;
    }

    @Bean
    public MinioClient presignMinioClient(OneLineProperties properties) {
        OneLineProperties.Storage storage = properties.storage();
        return MinioClient.builder()
                .endpoint(storage.publicEndpoint())
                .credentials(storage.accessKey(), storage.secretKey())
                .region(FIXED_REGION)
                .build();
    }

    private void ensureBucket(MinioClient client, String bucket) {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket '{}'", bucket);
            }
        } catch (Exception e) {
            log.warn("Could not create or verify MinIO bucket '{}' at startup", bucket, e);
        }
    }

    private void applyLifecycle(MinioClient client, OneLineProperties properties) {
        String bucket = properties.storage().bucket();
        try {
            int days = (int) Math.max(1, properties.attachments().ttl().toDays() + 1);
            LifecycleRule rule = new LifecycleRule(
                    Status.ENABLED,
                    null,
                    new Expiration((ZonedDateTime) null, days, null),
                    new RuleFilter(""),
                    "oneline-object-expiry",
                    null, null, null);
            client.setBucketLifecycle(SetBucketLifecycleArgs.builder()
                    .bucket(bucket)
                    .config(new LifecycleConfiguration(List.of(rule)))
                    .build());
            log.info("Applied lifecycle to bucket '{}': expire objects after {} days", bucket, days);
        } catch (Exception e) {
            log.warn("Could not apply lifecycle policy to bucket '{}'", bucket, e);
        }
    }
}
