FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

RUN apk add --no-cache maven

COPY wallet-service/pom.xml ./wallet-service/
COPY wallet-service/src ./wallet-service/src

RUN mvn -f wallet-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=build /app/wallet-service/target/wallet-service-0.0.1-SNAPSHOT.jar app.jar

ENV SPRING_DATASOURCE_URL=jdbc:mysql://mysql-wallet:3306/paytrack_wallet
ENV SPRING_RABBITMQ_HOST=rabbitmq
ENV SPRING_DATA_REDIS_HOST=redis
ENV SPRING_DATASOURCE_USERNAME=${SPRING_DATASOURCE_USERNAME}
ENV SPRING_DATASOURCE_PASSWORD=${SPRING_DATASOURCE_PASSWORD}

HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD ["curl", "-f", "http://localhost:8082/actuator/health"] || exit 1

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]