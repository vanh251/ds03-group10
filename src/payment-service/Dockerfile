# Multi-stage build for payment-service (Independent Gradle Project)
FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /app

COPY . .
RUN chmod +x ./gradlew
RUN ./gradlew bootJar --no-daemon -x test && rm -f build/libs/*-plain.jar

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8086

ENTRYPOINT ["java", "-jar", "app.jar"]
