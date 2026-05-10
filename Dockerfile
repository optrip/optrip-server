# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew \
    && ./gradlew dependencies --no-daemon || true

COPY src ./src
RUN ./gradlew clean bootJar -x test --no-daemon \
    && cp build/libs/*.jar app.jar

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=builder /workspace/app.jar ./app.jar

EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="\
-XX:MaxRAMPercentage=60.0 \
-XX:InitialRAMPercentage=30.0 \
-XX:+UseSerialGC \
-XX:MaxMetaspaceSize=128m \
-XX:ReservedCodeCacheSize=64m \
-Xss512k \
-Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["java", "-jar", "app.jar"]
