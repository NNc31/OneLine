FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src src
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon bootJar

FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app

COPY --from=builder --chown=nonroot:nonroot /workspace/build/libs/*.jar /app/app.jar

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=UTC"
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
