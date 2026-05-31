package com.nefodov.oneline.config;

import com.nefodov.oneline.support.OneLineProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
