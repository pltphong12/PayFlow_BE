# JOURNAL - PayFlow Fresher Edition

## Phase 0 - Infrastructure
- Chuẩn hóa local stack theo Docker Compose với healthcheck và profile docker cho từng service.
- Bài học: giữ cấu hình port/topic nhất quán giữa compose và `application-docker.yml` giúp giảm lỗi bootstrap.
- Rủi ro ghi nhận: tài liệu README từng lệch trạng thái thực tế service/module nên cần cập nhật cùng code.

## Phase 1 - Authentication
- Hoàn thiện register/login/refresh/logout với JWT access token + refresh token hash trong DB.
- Quyết định kỹ thuật: chuyển `UserRegistered` sang transactional outbox để tránh dual-write trực tiếp DB + Kafka.
- Bài học: dù business pass test, thiếu regression cho token expired có thể làm DoD chưa đủ.

## Phase 2 - Wallet + Ledger
- Bổ sung consumer `UserRegistered` theo hướng idempotent bằng bảng `processed_events`.
- Quyết định kỹ thuật: xử lý tạo ví bằng `INSERT ... ON CONFLICT DO NOTHING` để giảm race check-then-insert.
- Bài học: duplicate event cần coi là luồng bình thường, không phải lỗi.

## Phase 3 - Top-up
- Giữ invariant local transaction: credit wallet + ledger + outbox + topup status trong cùng transaction.
- Nâng nhánh complete top-up với retry optimistic lock tối đa 3 lần để an toàn khi callback đồng thời.
- Bài học: test publish lỗi phải xác nhận outbox vẫn `PENDING`, không mark `SENT` sớm.

## Phase 4 - Transfer Saga
- Hardening saga orchestration: idempotency race-safe, phân loại lỗi chưa xác định trả `503`, giữ recoverable state.
- Chốt terminal outbox một lần bằng guard trong service + unique constraint DB.
- Bài học: recovery/compensation cần no-op an toàn khi nhận replay để tránh phát event trùng.
