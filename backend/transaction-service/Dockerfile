FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

RUN apk add --no-cache maven

COPY transaction-service/pom.xml ./transaction-service/
COPY transaction-service/src ./transaction-service/src

RUN mvn -f transaction-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=build /app/transaction-service/target/transaction-service-0.0.1-SNAPSHOT.jar app.jar

ENV SPRING_DATASOURCE_URL=jdbc:mysql://mysql-transaction:3306/paytrack_transaction
ENV SPRING_RABBITMQ_HOST=rabbitmq

HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD ["curl", "-f", "http://localhost:8083/actuator/health"] || exit 1

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]