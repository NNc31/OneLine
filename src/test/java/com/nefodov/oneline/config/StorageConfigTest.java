package com.nefodov.oneline.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketLifecycleArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StorageConfigTest {

    private static final String BUCKET = "oneline-test";
    private MinioClient client;
    private StorageConfig config;

    @BeforeEach
    void setUp() {
        client = mock(MinioClient.class);
        config = new StorageConfig();
    }

    @Test
    @DisplayName("Creates the bucket when it does not exist")
    void ensureBucketCreatesMissingBucket() throws Exception {
        when(client.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);
        config.ensureBucket(client, BUCKET);
        verify(client).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    @DisplayName("Leaves an existing bucket untouched")
    void ensureBucketSkipsExistingBucket() throws Exception {
        when(client.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        config.ensureBucket(client, BUCKET);
        verify(client, never()).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    @DisplayName("Sets an expiry rule on the configured bucket")
    void applyLifecycleSetsRule() throws Exception {
        config.applyLifecycle(client, properties(Duration.ofDays(31)));
        ArgumentCaptor<SetBucketLifecycleArgs> args = ArgumentCaptor.forClass(SetBucketLifecycleArgs.class);
        verify(client).setBucketLifecycle(args.capture());
        assertEquals(BUCKET, args.getValue().bucket());
    }

    @Test
    @DisplayName("Sets a rule when the attachment TTL is shorter than a day")
    void applyLifecycleHandlesSubDayTtl() throws Exception {
        config.applyLifecycle(client, properties(Duration.ofHours(6)));
        verify(client).setBucketLifecycle(any(SetBucketLifecycleArgs.class));
    }

    private static OneLineProperties properties(Duration attachmentTtl) {
        OneLineProperties.Storage storage = new OneLineProperties.Storage(
                "http://minio:9000",
                "https://storage.example.com",
                "access",
                "secret",
                BUCKET,
                Duration.ofMinutes(5),
                1024L,
                Duration.ofMinutes(30));
        return new OneLineProperties(null, null, null, storage, new OneLineProperties.Attachments(true, attachmentTtl));
    }
}
