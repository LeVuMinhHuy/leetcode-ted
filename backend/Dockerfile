# Stage 1: Build
FROM gradle:8.12-jdk21 AS builder

WORKDIR /app

COPY settings.gradle build.gradle ./
COPY src ./src

RUN gradle bootJar --no-daemon

# Stage 2: Run
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
