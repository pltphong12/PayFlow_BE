FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY common ./common
COPY services/transaction-service ./services/transaction-service
COPY services/api-gateway/pom.xml ./services/api-gateway/pom.xml
COPY services/user-service/pom.xml ./services/user-service/pom.xml
COPY services/wallet-service/pom.xml ./services/wallet-service/pom.xml

RUN mvn -q -pl services/transaction-service -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S payflow && adduser -S payflow -G payflow \
    && apk add --no-cache wget

USER payflow
COPY --from=build /workspace/services/transaction-service/target/*.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "/app/app.jar"]