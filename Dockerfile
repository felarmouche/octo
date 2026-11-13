# Build stage: JDK version must match the project's toolchain (25)
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app
COPY . .
RUN chmod +x ./gradlew && ./gradlew clean build -x test

# Runtime stage
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/octo-0.0.1-SNAPSHOT.jar ./octo.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/octo.jar"]