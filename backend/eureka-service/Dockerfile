FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

RUN apk add --no-cache maven

COPY eureka-service/pom.xml ./eureka-service/
COPY eureka-service/src ./eureka-service/src

RUN mvn -f eureka-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=build /app/eureka-service/target/eureka-service-0.0.1-SNAPSHOT.jar app.jar

ENV SPRING_PROFILES_ACTIVE=docker

EXPOSE 8761

HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD ["curl", "-f", "http://localhost:8761/actuator/health"] || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]