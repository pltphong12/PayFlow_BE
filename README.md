# PayFlow Backend (Fresher Edition)

PayFlow là backend microservices cho bài toán ví điện tử/transfer, tập trung vào:
- Saga orchestration cho transfer.
- Transactional Outbox + Kafka publish an toàn.
- Idempotency, optimistic locking và regression test cho race condition.

## Tài liệu nguồn

- BRD Fresher: [`documentations/v2/BRD_PayFlow_Fresher.md`](documentations/v2/BRD_PayFlow_Fresher.md)
- Roadmap Fresher: [`documentations/v2/Roadmap_PayFlow_Fresher.md`](documentations/v2/Roadmap_PayFlow_Fresher.md)

## Kiến trúc hiện tại

Maven multi-module:
- `common`
- `services/api-gateway` (`8080`)
- `services/user-service` (`8081`)
- `services/wallet-service` (`8082`)
- `services/transaction-service` (`8083`)

Hạ tầng local qua Docker Compose:
- PostgreSQL `5432`
- Kafka (KRaft) `9092`
- Kafka UI `8090`
- Redis `6379`

## Chạy local bằng Docker

```bash
cd infrastructure
copy .env.example .env
docker compose up --build
```

Kiểm tra nhanh:
- Gateway health: `http://localhost:8080/actuator/health`
- Kafka UI: `http://localhost:8090`

Dừng hệ thống:

```bash
docker compose down
```

Xóa dữ liệu local (chủ động):

```bash
docker compose down -v
```

## Build và test

Chạy toàn bộ reactor:

```bash
mvn -B -ntp verify
```

CI workflow: [`.github/workflows/ci.yml`](.github/workflows/ci.yml)

## Lưu ý quan trọng

- Gateway là entry point public duy nhất.
- Internal Wallet API (`/api/v1/wallets/internal/**`) không route qua Gateway.
- Mỗi service sở hữu database riêng; không query/join chéo DB.
- Không commit `.env`, `application-local.yml`, hoặc artifact trong `target/`.