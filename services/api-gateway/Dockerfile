# syntax=docker/dockerfile:1

# ===== Stage 1: Build =====
# Build ngay từ context gốc của repo (root pom.xml) vì đây là Maven multi-module project
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy toàn bộ reactor (root pom + các module) để Maven resolve đúng dependency giữa các module
COPY pom.xml .
COPY common ./common
COPY services/api-gateway ./services/api-gateway

# Build riêng module api-gateway kèm theo module common mà nó phụ thuộc (-am),
# bỏ qua test để build image nhanh hơn (test đã chạy ở bước CI riêng)
RUN mvn -q -pl services/api-gateway -am -DskipTests package

# ===== Stage 2: Runtime =====
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Chạy bằng user thường, không dùng root, cho chuẩn bảo mật
RUN addgroup -S payflow && adduser -S payflow -G payflow
USER payflow

COPY --from=build /workspace/services/api-gateway/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]