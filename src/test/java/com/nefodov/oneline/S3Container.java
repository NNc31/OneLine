package com.nefodov.oneline;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

import java.time.Duration;

public class S3Container extends GenericContainer<S3Container> {

    private static final String IMAGE = "chrislusf/seaweedfs:3.98";
    private static final int S3_PORT = 8333;
    private static final String ACCESS_KEY = "oneline";
    private static final String SECRET_KEY = "oneline_secret";

    private static final String IDENTITIES = """
            {
              "identities": [
                {
                  "name": "oneline",
                  "credentials": [{"accessKey": "%s", "secretKey": "%s"}],
                  "actions": ["Admin", "Read", "Write", "List", "Tagging"]
                }
              ]
            }
            """.formatted(ACCESS_KEY, SECRET_KEY);

    public S3Container() {
        super(IMAGE);
        withCopyToContainer(Transferable.of(IDENTITIES), "/etc/seaweedfs/s3.json");
        withCommand("server", "-dir=/data", "-s3", "-s3.port=" + S3_PORT, "-s3.config=/etc/seaweedfs/s3.json");
        withExposedPorts(S3_PORT);
        waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)));
    }

    public String getS3URL() {
        return "http://" + getHost() + ":" + getMappedPort(S3_PORT);
    }

    public String getUserName() {
        return ACCESS_KEY;
    }

    public String getPassword() {
        return SECRET_KEY;
    }
}
