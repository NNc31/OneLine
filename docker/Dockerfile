FROM eclipse-temurin:25-jdk AS builder
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src src
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon bootJar

FROM eclipse-temurin:25-jre
WORKDIR /app

RUN useradd -r -u 1001 oneline
COPY --from=builder --chown=oneline:oneline /workspace/build/libs/*.jar /app/app.jar
USER oneline

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=UTC"
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
