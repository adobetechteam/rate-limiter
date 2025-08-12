# Multi-stage build for optimal image size
FROM maven:3.8-openjdk-8 AS build

# Set working directory
WORKDIR /app

# Copy pom.xml first for better Docker layer caching
COPY pom.xml .

# Download dependencies (this layer will be cached if pom.xml doesn't change)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean compile -B

# Runtime stage
FROM openjdk:8-jre

# Set working directory
WORKDIR /app

# Copy compiled classes from build stage
COPY --from=build /app/target/classes ./classes

# Copy dependencies (if any runtime dependencies exist)
COPY --from=build /root/.m2/repository /root/.m2/repository

# Set classpath
ENV CLASSPATH="/app/classes:/root/.m2/repository/*"

# Create a non-root user for security
RUN groupadd -g 1000 appgroup && \
    useradd -u 1000 -g appgroup -s /bin/sh -m appuser

USER appuser

# Default command to run ExampleUsage
CMD ["java", "-cp", "/app/classes", "com.example.ratelimiter.ExampleUsage"]

# Expose port if needed (for future web interface)
EXPOSE 8080 