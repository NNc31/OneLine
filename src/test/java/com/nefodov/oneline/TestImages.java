package com.nefodov.oneline;

import org.testcontainers.utility.DockerImageName;

public final class TestImages {

    public static final DockerImageName MINIO = DockerImageName
            .parse("quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z")
            .asCompatibleSubstituteFor("minio/minio");

    private TestImages() {
    }
}
