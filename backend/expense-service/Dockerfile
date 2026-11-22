FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

RUN apk add --no-cache maven

COPY expense-service/pom.xml ./expense-service/
COPY expense-service/src ./expense-service/src

RUN mvn -f expense-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=build /app/expense-service/target/expense-service-0.0.1-SNAPSHOT.jar app.jar

ENV SPRING_DATASOURCE_URL=jdbc:mysql://mysql-expense:3306/paytrack_expense
ENV SPRING_RABBITMQ_HOST=rabbitmq

HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD ["curl", "-f", "http://localhost:8084/actuator/health"] || exit 1

EXPOSE 8084

ENTRYPOINT ["java", "-jar", "app.jar"]