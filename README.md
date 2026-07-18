# PayFlow — Digital Payment Platform (Backend)

Dự án portfolio cá nhân mô phỏng nền tảng thanh toán số theo kiến trúc microservices
(Saga, Outbox, Idempotency, Event-driven qua Kafka, Observability, Resilience).

Tài liệu chi tiết:
- Business Requirement Document: [`docs/brd/BRD_PayFlow.html`](documentations/BRD_PayFlow.html)
- Roadmap task-level từng phase: [`docs/phase/PayFlow_Roadmap.html`](documentations/PayFlow_Roadmap.html)

## Yêu cầu môi trường

- Docker & Docker Compose v2
- JDK 21 (chỉ cần nếu muốn build/chạy service ngoài Docker)
- Maven 3.9+ (chỉ cần nếu muốn build/chạy service ngoài Docker)

## Chạy toàn bộ hệ thống (Phase 0)

Toàn bộ hạ tầng và API Gateway chạy được chỉ với **một lệnh duy nhất**:

```bash
cd infrastructure
docker compose up --build
```

Lệnh trên sẽ khởi động:

| Container | Port (host) | Mô tả |
|---|---|---|
| `payflow-postgres` | 5432 | PostgreSQL 16 |
| `payflow-kafka` | 9092 | Kafka (KRaft mode, không cần Zookeeper) |
| `payflow-kafka-ui` | 8090 | Giao diện quan sát topic/message Kafka |
| `payflow-redis` | 6379 | Redis (cache, TTL cho QR code, session) |
| `payflow-api-gateway` | 8080 | Spring Cloud Gateway — entry point duy nhất |

Sau khi tất cả container ở trạng thái `healthy` (kiểm tra bằng `docker compose ps`),
xác nhận Gateway đã chạy:

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

Kafka UI xem tại: http://localhost:8090

## Dừng hệ thống

```bash
docker compose down
```

Thêm `-v` nếu muốn xoá luôn volume dữ liệu Postgres:

```bash
docker compose down -v
```

## Build & test bằng Maven (không qua Docker)

```bash
mvn verify
```

CI (GitHub Actions) tự động chạy `mvn verify` trên mỗi lần push/PR vào nhánh `main`
— xem [`.github/workflows/ci.yml`](.github/workflows/ci.yml).

## Cấu trúc thư mục

```
.
├── common/                # Module dùng chung giữa các service
├── infrastructure/        # docker-compose.yml cho toàn bộ hạ tầng local
├── services/
│   └── api-gateway/       # Spring Cloud Gateway — entry point duy nhất
├── docs/
│   ├── brd/                # Business Requirement Document
│   └── phase/               # Roadmap chi tiết theo từng phase
└── pom.xml                # Maven reactor gốc (multi-module)
```

## Ghi chú thiết kế (Phase 0)

- Các service gọi nhau qua hostname nội bộ Docker (VD: `http://wallet-service:8081`),
  **không dùng Eureka/Config Server** — cấu hình theo profile (`application.yml` /
  `application-docker.yml`) là đủ ở quy mô dự án này.
- Ở Phase 0, Gateway chưa có route nghiệp vụ nào; chỉ đảm bảo chạy được và trả `200`
  ở `/actuator/health`. Route thật sẽ được thêm dần khi các service business ra đời
  (User, Wallet, Transaction, ...).