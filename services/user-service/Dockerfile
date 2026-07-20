FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY common ./common
COPY services/user-service ./services/user-service
COPY services/api-gateway/pom.xml ./services/api-gateway/pom.xml
RUN mvn -q -pl services/user-service -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -S payflow && adduser -S payflow -G payflow \
    && apk add --no-cache wget
USER payflow
COPY --from=build /workspace/services/user-service/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]