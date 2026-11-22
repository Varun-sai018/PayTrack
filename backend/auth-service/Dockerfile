FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

# Install Maven
RUN apk add --no-cache maven

# Copy auth-service pom and source
COPY auth-service/pom.xml ./auth-service/
COPY auth-service/src ./auth-service/src

# Build application
RUN mvn -f auth-service/pom.xml clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy jar from build stage
COPY --from=build /app/auth-service/target/auth-service-0.0.1-SNAPSHOT.jar app.jar

# Environment variables
ENV SPRING_DATASOURCE_URL=jdbc:mysql://mysql-auth:3306/paytrack_auth
ENV SPRING_RABBITMQ_HOST=rabbitmq
ENV SPRING_DATA_REDIS_HOST=redis

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD ["curl", "-f", "http://localhost:8081/actuator/health"] || exit 1

# Expose port
EXPOSE 8081

# Entry point
ENTRYPOINT ["java", "-jar", "app.jar"]