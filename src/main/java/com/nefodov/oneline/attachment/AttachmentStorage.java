package com.nefodov.oneline.attachment;

import com.nefodov.oneline.config.OneLineProperties;
import com.nefodov.oneline.web.exception.StorageException;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectsArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.OptionalLong;

@Component
public class AttachmentStorage {

    private final MinioClient ops;
    private final MinioClient presign;
    private final String bucket;
    private final int presignTtlSeconds;

    public AttachmentStorage(MinioClient minioClient, @Qualifier("presignMinioClient") MinioClient presignMinioClient, OneLineProperties properties) {
        this.ops = minioClient;
        this.presign = presignMinioClient;
        this.bucket = properties.storage().bucket();
        this.presignTtlSeconds = (int) properties.storage().presignTtl().toSeconds();
    }

    public String presignPut(String objectKey) {
        return presignedUrl(Method.PUT, objectKey);
    }

    public String presignGet(String objectKey) {
        return presignedUrl(Method.GET, objectKey);
    }

    private String presignedUrl(Method method, String objectKey) {
        try {
            return presign.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(method)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(presignTtlSeconds)
                    .build());
        } catch (Exception e) {
            throw new StorageException("Failed to presign " + method + " url", e);
        }
    }

    public OptionalLong objectSize(String objectKey) {
        try {
            StatObjectResponse stat = ops.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
            return OptionalLong.of(stat.size());
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return OptionalLong.empty();
            }
            throw new StorageException("Failed to stat object", e);
        } catch (Exception e) {
            throw new StorageException("Failed to stat object", e);
        }
    }

    public void remove(Collection<String> objectKeys) {
        if (objectKeys.isEmpty()) {
            return;
        }
        List<DeleteObject> targets = objectKeys.stream().map(DeleteObject::new).toList();
        try {
            for (Result<DeleteError> result : ops.removeObjects(RemoveObjectsArgs.builder()
                    .bucket(bucket)
                    .objects(targets)
                    .build())) {
                DeleteError error = result.get();
                if (error != null) {
                    throw new StorageException("Failed to remove object " + error.objectName() + ": " + error.message());
                }
            }
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            throw new StorageException("Failed to remove objects", e);
        }
    }
}
