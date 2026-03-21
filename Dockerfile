# Multi-stage Docker build (§18.1)
# Stage 1: Build — use official Maven image that bundles JDK 21 + Maven 3.9
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Cache dependencies layer — copy pom.xml first for layer caching
COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -q

# Stage 2: Extract layers (Spring Boot layered JAR)
FROM eclipse-temurin:21-jdk-alpine AS extractor
WORKDIR /build
COPY --from=builder /build/target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# Stage 3: Runtime — minimal image
# Using Eclipse Temurin JRE (non-root user, minimal attack surface per §18.1)
FROM eclipse-temurin:21-jre-alpine

# Upgrade Alpine packages to pick up latest OS-level security patches
# (gnutls CVE-2026-1584, libexpat CVE-2026-32767, libpng CVE-2026-25646, zlib CVE-2026-22184)
RUN apk upgrade --no-cache

# Non-root user per security best practices
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy layered JAR contents in dependency order (best cache utilization)
COPY --from=extractor /build/dependencies/ ./
COPY --from=extractor /build/spring-boot-loader/ ./
COPY --from=extractor /build/snapshot-dependencies/ ./
COPY --from=extractor /build/application/ ./

USER appuser

EXPOSE 8080

# Health check for container runtime
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1

# No secrets, credentials, or environment-specific configuration in the image (§13)
# All configuration injected at runtime via Kubernetes ConfigMap / Vault CSI
ENTRYPOINT ["java", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "org.springframework.boot.loader.launch.JarLauncher"]
