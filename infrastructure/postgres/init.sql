-- Chạy một lần khi Postgres khởi tạo volume lần đầu (docker-entrypoint-initdb.d).
-- Mỗi microservice dùng database riêng; Flyway trong service tạo bảng trong database đó.

CREATE DATABASE payflow_user_db;
