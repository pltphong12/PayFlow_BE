# PayFlow — Development Roadmap (Chi tiết task-level)

*Bổ sung endpoint, event, bảng DB cụ thể cho từng phase · Đi kèm với BRD_PayFlow.html*

> **Nguyên tắc xuyên suốt:** Mỗi phase phải chạy được, demo được, và xây dựng trên nền của phase trước. Không có phase nào code xong mà không nhìn thấy kết quả cụ thể. Các phase đánh dấu ⭐ là trọng tâm kỹ thuật, nên đầu tư thời gian và chất lượng code kỹ nhất.

## Mục lục

- [Phase 0 — Infrastructure](#phase0)
- [Phase 1 — Authentication](#phase1)
- [Phase 2 — Wallet](#phase2)
- [Phase 3 — Top-up](#phase3)
- [Phase 4 ⭐ — Transfer P2P (Saga)](#phase4)
- [Phase 5 — Observability & Resilience](#phase5)
- [Phase 6 — Merchant](#phase6)
- [Phase 7 ⭐⭐⭐ — QR Payment](#phase7)
- [Phase 8 — Real-time (SSE)](#phase8)
- [Phase 9 — Refactor: Ledger Service](#phase9)
- [Phase 10 — Settlement](#phase10)
- [Phase 11 — Hardening & Security nâng cao](#phase11)

---

<a id="phase0"></a>
## Phase 0 — Infrastructure `Nền tảng`

**Mục tiêu:** Có bộ khung hạ tầng chạy được bằng một lệnh, chưa cần business logic.

### Task cụ thể

- Viết `docker-compose.yml` gồm: PostgreSQL (mỗi service 1 database logic riêng, có thể dùng chung 1 container Postgres nhưng khác database name), Kafka + Zookeeper (hoặc Kafka KRaft mode), Kafka UI để quan sát topic/message, Redis
- Setup Spring Cloud Gateway làm entry point duy nhất — chưa cần route gì, chỉ cần chạy được và trả 200 ở endpoint health check
- Các service gọi nhau qua hostname nội bộ Docker (VD: `http://wallet-service:8081`) — **không dùng Eureka/Config Server** vì số lượng service còn ít, dùng `application.yml`/`application-docker.yml` theo profile là đủ
- Setup CI cơ bản (GitHub Actions): build + chạy unit test khi push code

### Danh sách container

| Container | Image gợi ý | Port |
|---|---|---|
| postgres | `postgres:16` | 5432 |
| kafka | `confluentinc/cp-kafka` hoặc `bitnami/kafka` (KRaft mode) | 9092 |
| kafka-ui | `provectuslabs/kafka-ui` | 8090 |
| redis | `redis:7-alpine` | 6379 |
| api-gateway | build từ source | 8080 |

**Demo:** chạy `docker compose up`, tất cả container ở trạng thái `healthy`, Gateway trả 200 ở `GET /actuator/health`.

**Definition of Done:** README có hướng dẫn chạy toàn bộ hạ tầng chỉ với 1 lệnh; không service business nào cần chạy ở bước này.

---

<a id="phase1"></a>
## Phase 1 — Authentication `User Service`

**Mục tiêu:** Đăng ký, đăng nhập, nhận JWT, gọi được API qua Gateway.

### Bảng dữ liệu (User Service DB)

| Bảng | Cột chính |
|---|---|
| `users` | `id (UUID, PK)`, `email (unique)`, `password_hash`, `full_name`, `role (USER/ADMIN)`, `status (ACTIVE/DISABLED)`, `created_at` |
| `refresh_tokens` | `id`, `user_id (FK)`, `token_hash`, `expires_at`, `revoked (boolean)` |

### API Endpoint

| Endpoint | Mô tả |
|---|---|
| `POST` `/api/v1/auth/register` | Đăng ký tài khoản mới, publish event `UserRegistered` |
| `POST` `/api/v1/auth/login` | Đăng nhập, trả JWT access token (15 phút) + refresh token (7 ngày) |
| `POST` `/api/v1/auth/refresh` | Cấp access token mới từ refresh token còn hạn |
| `POST` `/api/v1/auth/logout` | Thu hồi refresh token |
| `GET` `/api/v1/users/me` | Lấy thông tin user hiện tại (yêu cầu JWT hợp lệ) |

### Event phát ra

```
Topic: user-events
  → UserRegistered { userId, email, fullName, timestamp }
```

### Task cụ thể

- Hash mật khẩu bằng BCrypt
- Sinh JWT chứa claim `userId`, `role`, ký bằng secret key (HS256) hoặc RSA (RS256) nếu muốn nâng cao sau này
- Spring Security filter chain: validate JWT ở mọi request trừ `/auth/**`
- Cấu hình Gateway route `/api/v1/auth/**` và `/api/v1/users/**` về User Service, kèm filter forward JWT claims qua header (VD `X-User-Id`) cho service phía sau đọc mà không cần verify lại token
- Frontend React: trang đăng ký, đăng nhập, lưu access token (in-memory/state, không dùng localStorage cho token nhạy cảm nếu muốn chuẩn bảo mật, hoặc dùng httpOnly cookie nếu nâng cao)

**Demo:** đăng ký tài khoản mới trên giao diện React → đăng nhập → gọi `GET /api/v1/users/me` thành công, trả đúng thông tin user.

**Definition of Done:** Unit test cho luồng đăng ký (trùng email → lỗi 409), login sai mật khẩu → lỗi 401, JWT hết hạn → lỗi 401 khi gọi API.

---

<a id="phase2"></a>
## Phase 2 — Wallet `Wallet Service`

**Mục tiêu:** Có ví, xem được số dư = 0 (chưa có tiền vào ra thật).

### Bảng dữ liệu (Wallet Service DB)

| Bảng | Cột chính |
|---|---|
| `wallets` | `id (UUID, PK)`, `user_id (unique)`, `balance (decimal)`, `currency (default VND)`, `version (int, dùng cho optimistic lock)`, `status (ACTIVE/FROZEN)`, `created_at` |
| `ledger_entries` | `id`, `wallet_id (FK)`, `transaction_id`, `entry_type (DEBIT/CREDIT)`, `amount`, `balance_after`, `created_at` — bảng ledger tạm thời là 1 bảng con trong Wallet Service, sẽ tách thành service riêng ở Phase 9 |

### API Endpoint

| Endpoint | Mô tả |
|---|---|
| `GET` `/api/v1/wallets/me` | Xem số dư ví của user hiện tại |
| `GET` `/api/v1/wallets/me/ledger` | Xem lịch sử bút toán (phân trang) |
| `GET` `/api/v1/wallets/internal/{userId}` | API nội bộ cho service khác truy vấn (không public qua Gateway) |

### Event lắng nghe

```
Consume topic: user-events
  ← UserRegistered → tự động tạo wallet mới, balance = 0
```

### Task cụ thể

- Consumer Kafka lắng nghe `UserRegistered`, tạo wallet tương ứng (nếu muốn đơn giản hóa ở phase này có thể gọi đồng bộ, nhưng nên làm async ngay từ đầu để quen dần với event-driven)
- Migration DB dùng Flyway hoặc Liquibase để quản lý schema version
- Frontend: trang Dashboard hiển thị số dư (0đ ban đầu)

**Demo:** đăng ký tài khoản mới ở Phase 1 → chuyển qua trang Dashboard → thấy ví đã được tạo tự động với số dư 0đ.

**Definition of Done:** Test consumer Kafka: publish thử event `UserRegistered` giả, verify wallet được tạo đúng.

---

<a id="phase3"></a>
## Phase 3 — Top-up `Event-driven bắt đầu`

**Mục tiêu:** Nạp tiền thành công, số dư tăng đúng, không bị cộng đôi khi retry, có luồng async hoàn chỉnh đầu tiên.

### Service mới: Payment Gateway Fake Service

| Bảng | Cột chính |
|---|---|
| `fake_payment_requests` | `id`, `user_id`, `amount`, `status (PENDING/SUCCESS/FAILED)`, `idempotency_key (unique)`, `created_at` |

| Endpoint | Mô tả |
|---|---|
| `POST` `/api/v1/topup` | Header bắt buộc: `Idempotency-Key`. Tạo yêu cầu top-up, trạng thái `PENDING` |

### Bảng dữ liệu (Wallet Service DB — bổ sung)

| Bảng | Cột chính |
|---|---|
| `outbox_events` | `id`, `aggregate_id`, `event_type`, `payload (jsonb)`, `status (PENDING/SENT)`, `created_at` — bảng Outbox, đọc bởi 1 job/poller riêng để publish lên Kafka, đảm bảo event không mất khi service crash giữa lúc save DB và publish |

### Luồng & Event

```
1. Client → POST /api/v1/topup (Idempotency-Key: xxx)
2. Payment Gateway Fake: tạo request PENDING → giả lập random SUCCESS/FAILED sau 1-2s
3. Payment Gateway Fake publish → Topic: payment-events
     → TopUpConfirmed { userId, amount, requestId } (nếu SUCCESS)
     → TopUpFailed { userId, amount, requestId, reason } (nếu FAILED)
4. Wallet Service consume TopUpConfirmed:
     - Check idempotency_key đã xử lý chưa (bảng processed_events)
     - Nếu chưa: cộng balance, ghi outbox_events → WalletCredited
     - Nếu đã xử lý rồi: bỏ qua (chống retry trùng)
5. Wallet Service outbox poller publish → Topic: wallet-events
     → WalletCredited { walletId, amount, newBalance }
6. Notification Service consume WalletCredited → gửi email/log thông báo
```

### Task cụ thể

- Implement Outbox Pattern đúng chuẩn: save entity + insert outbox record trong **cùng 1 transaction DB**, có job/poller (VD Spring `@Scheduled` mỗi 2 giây hoặc dùng Debezium CDC nếu muốn nâng cao) đọc outbox và publish Kafka, đánh dấu `SENT` sau khi publish thành công
- Bảng `processed_events` (idempotency ở tầng consumer) — lưu `event_id` đã xử lý để tránh xử lý trùng khi Kafka deliver message nhiều lần (at-least-once delivery)
- Notification Service: consumer đơn giản, log ra console hoặc gửi qua Mailhog (SMTP giả lập) dạng "Đã nạp thành công X đ"
- Cấu hình Retry cho Kafka consumer (VD dùng `RetryTemplate` hoặc Spring Kafka `DefaultErrorHandler`) — nếu xử lý event lỗi, retry tối đa N lần trước khi đẩy vào Dead Letter Queue
- Setup DLQ topic riêng (VD `payment-events.DLQ`) cho message xử lý lỗi liên tục
- Frontend: form nhập số tiền nạp, hiển thị trạng thái đang xử lý → thành công/thất bại

**Demo:** nạp tiền → số dư tăng đúng sau vài giây → xem Kafka UI thấy các message trên topic `payment-events` và `wallet-events` → thử gọi lại API với cùng Idempotency-Key, số dư không tăng thêm lần 2.

**Definition of Done:** Test case retry idempotency; test case DLQ khi consumer throw exception liên tục; test Outbox không mất event khi giả lập crash giữa transaction.

---

<a id="phase4"></a>
## Phase 4 ⭐ — Transfer P2P (Saga) `Trọng tâm kỹ thuật`

**Mục tiêu:** Chuyển tiền giữa 2 user, đảm bảo không mất/nhân đôi tiền kể cả khi có lỗi giữa chừng. Đây là phase quan trọng nhất để nói chuyện trong phỏng vấn kỹ thuật.

### Service mới: Transaction Service

| Bảng | Cột chính |
|---|---|
| `transactions` | `id`, `type (TRANSFER)`, `sender_user_id`, `receiver_user_id`, `amount`, `status (PENDING/COMPLETED/FAILED/COMPENSATING)`, `idempotency_key (unique)`, `created_at` |
| `saga_steps` | `id`, `transaction_id (FK)`, `step_name (DEBIT_SENDER/CREDIT_RECEIVER)`, `status (PENDING/SUCCESS/FAILED/COMPENSATED)`, `executed_at` — dùng để theo dõi từng bước Saga, phục vụ debug và audit |
| `outbox_events` | giống Phase 3 |

### Bảng dữ liệu (Wallet Service DB — bổ sung)

| Cột bổ sung ở bảng `wallets` | Mục đích |
|---|---|
| `version (int)` | Optimistic Locking — mỗi lần update balance phải kiểm tra version, nếu version không khớp (do request khác đã update trước) thì retry hoặc trả lỗi `409 Conflict` |

### API Endpoint

| Endpoint | Mô tả |
|---|---|
| `POST` `/api/v1/transfers` | Header `Idempotency-Key` bắt buộc. Body: `receiverUserId, amount` |
| `GET` `/api/v1/transfers/{id}` | Xem trạng thái 1 giao dịch chuyển tiền |
| `PUT` `/api/v1/wallets/internal/{walletId}/debit` | API nội bộ Wallet Service — trừ tiền, có kiểm tra `version` |
| `PUT` `/api/v1/wallets/internal/{walletId}/credit` | API nội bộ Wallet Service — cộng tiền |

### Thiết kế Saga (Orchestration-based)

```
1. POST /api/v1/transfers → Transaction Service tạo record PENDING
2. [Saga Step 1] Gọi Wallet Service: DEBIT sender
     - Nếu FAIL (không đủ tiền / lỗi version) → transaction FAILED, dừng, KHÔNG cần compensate vì chưa có gì để hoàn
     - Nếu SUCCESS → ghi saga_steps DEBIT_SENDER = SUCCESS
3. [Saga Step 2] Gọi Wallet Service: CREDIT receiver
     - Nếu SUCCESS → transaction COMPLETED, ghi saga_steps CREDIT_RECEIVER = SUCCESS
     - Nếu FAIL (VD receiver bị khóa) → transaction chuyển COMPENSATING
       → [Compensating action] Gọi lại Wallet Service: CREDIT sender (hoàn tiền)
       → transaction FAILED sau khi hoàn tất compensate
4. Ghi ledger 2 bút toán (DEBIT sender, CREDIT receiver) khi transaction COMPLETED
5. Publish TransferCompleted / TransferFailed qua Outbox → Kafka
6. Notification Service consume → thông báo cho cả 2 bên
```

### Task cụ thể

- Chọn Saga Orchestration (Transaction Service là orchestrator điều phối, gọi trực tiếp Wallet Service qua REST nội bộ) — dễ trace, dễ debug hơn Choreography ở giai đoạn học tập
- Optimistic Locking trên bảng `wallets`: dùng `@Version` (JPA) hoặc tự viết `WHERE version = :version` trong câu update, retry tối đa 3 lần nếu conflict
- Viết integration test mô phỏng: 2 request transfer cùng lúc trừ vào cùng 1 ví → xác nhận không có ví nào bị âm tiền
- Viết test mô phỏng lỗi giữa chừng: debit sender thành công nhưng credit receiver fail (throw exception giả) → xác nhận compensating transaction hoàn tiền đúng, số dư sender không đổi so với trước giao dịch
- Setup OpenTelemetry + Zipkin: trace toàn bộ request đi qua Gateway → Transaction Service → Wallet Service, xem được từng bước Saga mất bao lâu
- Setup Prometheus + Micrometer: expose metrics số lượng transfer thành công/thất bại/đang compensate
- Frontend: form chuyển tiền, trang lịch sử giao dịch hiển thị trạng thái (Đang xử lý/Thành công/Thất bại)

**Demo:** chuyển tiền giữa 2 tài khoản demo → số dư cập nhật đúng cả 2 bên → xem Zipkin thấy trace đầy đủ các bước Saga → giả lập lỗi (VD tắt Wallet Service giữa chừng) → xem transaction tự rollback đúng.

**Definition of Done:** Có ít nhất 3 test case: (1) transfer thành công bình thường, (2) transfer fail do không đủ tiền, (3) transfer fail giữa chừng và compensate đúng. Đây là phần nên viết kỹ nhất vì là trọng tâm phỏng vấn.

---

<a id="phase5"></a>
## Phase 5 — Observability & Resilience (sớm) `Củng cố nền Phase 4`

**Mục tiêu:** Trước khi mở rộng sang domain Merchant, củng cố khả năng quan sát và chống lỗi cho phần Core (Transfer) vừa xây — tránh nợ kỹ thuật dồn về cuối.

### Task cụ thể

- Circuit Breaker (Resilience4j) khi Transaction Service gọi Wallet Service — nếu Wallet Service down liên tục, mở circuit, trả lỗi nhanh thay vì để request treo (timeout chậm)
- Cấu hình Retry với backoff (VD exponential backoff) khi gọi nội bộ giữa các service, phân biệt rõ lỗi nên retry (timeout, 503) và lỗi không nên retry (400, business validation fail)
- Grafana dashboard cơ bản: số giao dịch/phút, tỷ lệ lỗi, latency p95/p99
- Centralized logging (đơn giản: structured JSON log + correlation ID xuyên suốt request, có thể dùng ELK/Loki nếu muốn mở rộng thêm)

**Demo:** tắt Wallet Service → gọi transfer → Circuit Breaker mở, trả lỗi nhanh (không bị treo 30s chờ timeout) → xem Grafana dashboard thấy tỷ lệ lỗi tăng lên real-time.

**Definition of Done:** Circuit Breaker có test case chuyển trạng thái CLOSED → OPEN → HALF_OPEN đúng.

---

<a id="phase6"></a>
## Phase 6 — Merchant `Mở rộng domain`

**Mục tiêu:** Luồng KYB (Know Your Business) đơn giản chạy được: đăng ký → chờ duyệt → được cấp quyền truy cập dashboard.

### Service mới: Merchant Service

| Bảng | Cột chính |
|---|---|
| `merchants` | `id`, `user_id (FK, unique)`, `business_name`, `category`, `bank_account_number`, `bank_name`, `status (PENDING_APPROVAL/APPROVED/REJECTED)`, `rejected_reason`, `created_at`, `approved_at` |
| `merchant_balances` | `id`, `merchant_id (FK)`, `pending_balance (chờ settlement)`, `settled_balance (đã về ngân hàng)`, `version` |

### API Endpoint

| Endpoint | Mô tả |
|---|---|
| `POST` `/api/v1/merchants/register` | Đăng ký hồ sơ merchant, trạng thái `PENDING_APPROVAL` |
| `GET` `/api/v1/merchants/me` | Xem hồ sơ merchant của chính mình |
| `GET` `/api/v1/admin/merchants?status=PENDING_APPROVAL` | [ADMIN] Danh sách merchant chờ duyệt |
| `PUT` `/api/v1/admin/merchants/{id}/approve` | [ADMIN] Duyệt merchant |
| `PUT` `/api/v1/admin/merchants/{id}/reject` | [ADMIN] Từ chối, kèm lý do |
| `GET` `/api/v1/merchants/me/dashboard` | Xem tổng doanh thu, số dư chờ settlement |

### Event phát ra

```
Topic: merchant-events
  → MerchantApproved { merchantId, userId }
  → MerchantRejected { merchantId, userId, reason }
```

### Task cụ thể

- Role-based Authorization: thêm role `MERCHANT` (gán khi được Admin duyệt) bên cạnh `USER`/`ADMIN` đã có ở Phase 1 — cần cơ chế cập nhật role/claim sau khi JWT đã phát hành (VD: JWT ngắn hạn, refresh token sẽ mang role mới nhất khi user login lại hoặc refresh)
- Validate: 1 user chỉ được đăng ký 1 merchant profile
- Frontend: form đăng ký merchant, trang Admin duyệt (danh sách + nút Approve/Reject), trang Merchant Dashboard cơ bản (chưa có số liệu thật, hiển thị 0)

**Demo:** đăng ký merchant → đăng nhập tài khoản Admin → duyệt merchant → merchant login lại thấy được vào Dashboard.

**Definition of Done:** Test case user thường không thể truy cập route Admin; test case merchant chưa được duyệt không thể tạo QR (chuẩn bị cho Phase 7).

---

<a id="phase7"></a>
## Phase 7 ⭐⭐⭐ — QR Payment `Điểm nhấn nổi bật nhất`

**Mục tiêu:** Luồng thanh toán merchant hoàn chỉnh, tái sử dụng toàn bộ hạ tầng Saga/ledger đã xây ở Phase 4.

### Service mới: QR Payment Service

| Lưu trữ | Cột chính |
|---|---|
| `Redis key: qr:{qrId}` | Value (JSON): `merchantId, amount, status (PENDING/PAID/EXPIRED), createdAt`. TTL = 300 giây (5 phút), Redis tự xóa khi hết hạn |

### API Endpoint

| Endpoint | Mô tả |
|---|---|
| `POST` `/api/v1/qr/generate` | [MERCHANT] Body: `amount`. Trả về `qrId`, payload đã ký, ảnh QR (base64 hoặc SVG) |
| `GET` `/api/v1/qr/{qrId}` | Xem thông tin QR (merchant name, số tiền) trước khi user xác nhận — dùng khi user quét xong, hiển thị màn hình xác nhận |
| `POST` `/api/v1/qr/{qrId}/confirm` | [CUSTOMER] Xác nhận thanh toán — endpoint quan trọng nhất phase này |

### Thiết kế payload QR & chữ ký

```
qrPayload = base64( merchantId + "|" + amount + "|" + qrId + "|" + expiredAt )
signature = HMAC-SHA256(qrPayload, secretKey)
QR content (encode thành ảnh) = qrPayload + "." + signature

Khi confirm: verify lại signature trước khi xử lý,
  đồng thời check status trong Redis phải là PENDING (chưa ai xác nhận trước đó)
  → nếu hợp lệ: set status = PAID ngay lập tức (atomic, dùng Redis SETNX hoặc Lua script
    để tránh race condition khi 2 request confirm cùng lúc 1 QR)
```

### Luồng thanh toán (tái sử dụng Saga có sẵn)

```
1. Merchant: POST /api/v1/qr/generate → QR Service tạo qrId, lưu Redis, trả ảnh QR
2. Customer quét QR bằng camera trình duyệt (thư viện html5-qrcode) → GET /api/v1/qr/{qrId} xem trước
3. Customer: POST /api/v1/qr/{qrId}/confirm
4. QR Service verify signature + verify status PENDING → set PAID (atomic)
5. QR Service gọi Transaction Service: tạo transaction type = QR_PAYMENT
     - Tái sử dụng luồng Saga Phase 4:
       [Step 1] DEBIT ví Customer
       [Step 2] CREDIT vào merchant_balances.pending_balance (KHÔNG cộng thẳng vào ví thường,
                vì tiền merchant phải chờ Settlement mới "thật sự" về ngân hàng)
     - Nếu Step 2 fail → compensate: hoàn tiền Customer, đồng thời set lại QR status = PENDING
       để merchant có thể thử lại (hoặc EXPIRED nếu đã quá hạn)
6. Publish QRPaymentCompleted qua Outbox → Kafka
7. Notification Service consume → chuẩn bị dữ liệu đẩy real-time (xử lý ở Phase 8)
```

### Task cụ thể

- Dùng thư viện `zxing` (Java) để generate ảnh QR từ chuỗi payload
- Frontend: trang Merchant nhập số tiền → hiển thị ảnh QR, tự động polling hoặc chờ SSE (Phase 8) để biết đã thanh toán chưa; trang Customer quét QR bằng camera qua thư viện JS `html5-qrcode`, hiển thị màn hình xác nhận (tên merchant, số tiền) trước khi bấm Xác nhận
- Circuit Breaker khi QR Service gọi Transaction Service (tái sử dụng cấu hình Resilience4j từ Phase 5)
- Test case quan trọng: 2 request confirm cùng lúc 1 QR → chỉ 1 request thành công, request còn lại nhận lỗi rõ ràng (VD "QR đã được thanh toán")

**Demo trực tiếp rất ấn tượng:** mở 2 tab trình duyệt (1 tab Merchant, 1 tab Customer trên điện thoại thật) → Merchant tạo QR → Customer quét bằng camera điện thoại → xác nhận → Merchant tab thấy trạng thái cập nhật (tạm thời polling, sẽ tự động real-time ở Phase 8).

**Definition of Done:** Test case QR hết hạn không xác nhận được; test case xác nhận 2 lần cùng 1 QR chỉ thành công 1 lần; test case chữ ký bị sửa đổi (giả mạo) bị từ chối.

---

<a id="phase8"></a>
## Phase 8 — Real-time (SSE) `Trải nghiệm nổi bật`

**Mục tiêu:** Merchant Dashboard tự động cập nhật trạng thái thanh toán, không cần F5 — điểm nhấn demo trực tiếp trong phỏng vấn.

### Bổ sung tại Notification Service

| Thành phần | Mô tả |
|---|---|
| `Map<String qrId, SseEmitter>` | Lưu trong bộ nhớ (in-memory map), ánh xạ `qrId` đang chờ với emitter tương ứng của merchant đang mở dashboard |

### API Endpoint

| Endpoint | Mô tả |
|---|---|
| `GET` `/api/v1/notifications/subscribe/{qrId}` | [MERCHANT] Mở kết nối SSE, giữ connection cho tới khi có event hoặc timeout |

### Luồng xử lý

```
1. Merchant mở trang tạo QR → gọi luôn GET /subscribe/{qrId}, giữ SSE connection
2. Notification Service consume QRPaymentCompleted (Kafka) từ Phase 7
3. Tìm emitter tương ứng theo qrId trong map
4. emitter.send(data: "PAID") → browser nhận event, JS cập nhật UI ngay lập tức
5. Đóng emitter sau khi gửi xong (hoặc khi merchant rời trang / timeout 6 phút > TTL của QR)
```

### Task cụ thể

- Xử lý cleanup: khi merchant đóng tab hoặc SSE timeout, phải remove emitter khỏi map, tránh memory leak
- Ghi chú lại trong tài liệu kiến trúc: giới hạn hiện tại của thiết kế in-memory map là **không scale được nếu chạy nhiều instance Notification Service** (emitter ở instance A, event consume ở instance B sẽ không push được) — nêu rõ đây là điểm cần cải thiện bằng Redis Pub/Sub nếu scale thật, thể hiện tư duy hệ thống dù chưa cần implement ngay
- Frontend: dùng `EventSource` API của browser để lắng nghe SSE, cập nhật UI real-time (đổi màu, hiện thông báo "Đã nhận thanh toán")

**Demo:** Merchant tạo QR, Customer quét và xác nhận trên điện thoại → màn hình Merchant tự động đổi trạng thái "Đã thanh toán" trong vòng 1-2 giây, không cần F5.

**Definition of Done:** Test case SSE connection tự đóng đúng khi hết TTL; không rò rỉ bộ nhớ khi có nhiều emitter mở/đóng liên tục.

---

<a id="phase9"></a>
## Phase 9 — Refactor: Tách Ledger Service `Refactor có chủ đích`

**Mục tiêu:** Tách bảng `ledger_entries` (đang nằm trong Wallet Service từ Phase 2) thành 1 service độc lập, append-only, chuẩn bị cho Settlement và Reconciliation ở phase sau.

### Service mới: Ledger Service

| Bảng | Cột chính |
|---|---|
| `ledger_entries` | `id`, `transaction_id`, `account_type (WALLET/MERCHANT)`, `account_id`, `entry_type (DEBIT/CREDIT)`, `amount`, `balance_after`, `created_at` — **chỉ INSERT, không bao giờ UPDATE/DELETE** |

### API Endpoint

| Endpoint | Mô tả |
|---|---|
| `GET` `/api/v1/ledger?accountId=...&from=...&to=...` | Truy vấn lịch sử bút toán, phục vụ báo cáo và đối soát |

### Luồng xử lý mới

```
Transaction Service (sau khi Saga COMPLETED)
  → publish TransactionCompleted qua Outbox → Kafka (topic: transaction-events)
  → Ledger Service consume → INSERT 2 bút toán (DEBIT bên gửi, CREDIT bên nhận)

Câu hỏi quan trọng cần trả lời khi thiết kế:
  "Nếu Ledger Service ghi sổ thất bại thì transaction đã coi là COMPLETED chưa?"
  → Trả lời: CÓ, transaction vẫn COMPLETED (tiền đã chuyển thật ở Wallet Service).
    Ledger là số liệu ghi nhận SAU, không phải điều kiện để giao dịch thành công.
    Nếu Ledger insert fail → retry qua consumer error handler,
    nếu vẫn fail liên tục → vào DLQ, có cảnh báo (alert) để xử lý thủ công,
    KHÔNG rollback lại giao dịch tiền đã hoàn tất.
```

### Task cụ thể

- Migrate dữ liệu ledger cũ (nếu có) từ Wallet Service DB sang Ledger Service DB (viết script migration 1 lần)
- Xóa bảng `ledger_entries` khỏi Wallet Service sau khi migrate xong và xác nhận Ledger Service hoạt động ổn định
- Viết báo cáo trong tài liệu kiến trúc giải thích lý do refactor (đây là câu chuyện thật đáng kể trong phỏng vấn: "nhận ra Ledger cần tách riêng vì đặc tính append-only, cần lưu trữ dài hạn, khác chu kỳ thay đổi với Wallet Service")

**Demo:** gọi `GET /api/v1/ledger?accountId=...` trả về đầy đủ lịch sử bút toán của cả Transfer (Phase 4) lẫn QR Payment (Phase 7), dữ liệu nhất quán với số dư hiện tại của ví.

**Definition of Done:** Tổng các bút toán trong Ledger phải khớp với số dư hiện tại của từng ví/merchant (viết 1 script hoặc test đối chiếu).

---

<a id="phase10"></a>
## Phase 10 — Settlement `Điểm khác biệt lớn`

**Mục tiêu:** Merchant không nhận tiền ngay lập tức — tiền vào `pending_balance`, cuối kỳ mới được đối soát và "chuyển khoản" về ngân hàng giả lập. Đây là nghiệp vụ nhiều dự án ví điện tử bỏ qua.

### Service mới: Settlement Service

| Bảng | Cột chính |
|---|---|
| `settlement_batches` | `id`, `batch_date`, `status (PROCESSING/COMPLETED/PARTIALLY_FAILED)`, `total_merchants`, `created_at` |
| `settlement_requests` | `id`, `batch_id (FK)`, `merchant_id`, `amount`, `status (PENDING/SUCCESS/FAILED)`, `retry_count`, `bank_reference_code`, `created_at` |

### API Endpoint

| Endpoint | Mô tả |
|---|---|
| `POST` `/api/v1/admin/settlement/trigger` | [ADMIN] Kích hoạt thủ công batch settlement (thay vì chờ lịch tự động, tiện demo) |
| `GET` `/api/v1/merchants/me/settlements` | [MERCHANT] Xem lịch sử settlement của mình |
| `GET` `/api/v1/admin/settlement/batches/{id}` | [ADMIN] Xem chi tiết 1 batch, tỷ lệ thành công/thất bại |

### Luồng xử lý (Batch job)

```
1. Scheduled job (VD chạy 00:00 mỗi ngày, hoặc trigger thủ công để demo):
     - Query tất cả merchant có pending_balance > 0
     - Tạo 1 settlement_batch mới, mỗi merchant → 1 settlement_request (PENDING)
2. Với mỗi settlement_request:
     - Gọi Fake Bank Service (tái sử dụng ý tưởng Payment Gateway Fake ở Phase 3,
       có thể mở rộng chung 1 service, đổi tên "Fake Bank Integration Service")
     - Fake Bank random SUCCESS/FAILED (mô phỏng lỗi ngân hàng thật)
     - SUCCESS: settlement_request = SUCCESS,
         merchant_balances.pending_balance -= amount,
         merchant_balances.settled_balance += amount
     - FAILED: settlement_request = FAILED, retry_count += 1
         → nếu retry_count < 3: đưa vào hàng đợi retry (VD sau 10 phút)
         → nếu retry_count >= 3: FAILED vĩnh viễn, cần Admin xử lý thủ công, gửi alert
3. Sau khi batch xử lý xong toàn bộ: batch.status = COMPLETED hoặc PARTIALLY_FAILED
4. Publish SettlementCompleted / SettlementFailed → Notification Service thông báo cho merchant
```

### Task cụ thể

- Idempotency ở tầng batch: nếu job chạy trùng lặp (VD server restart giữa batch), không được tạo settlement_request 2 lần cho cùng merchant/cùng ngày (unique constraint theo `merchant_id + batch_date`)
- Circuit Breaker + Retry khi gọi Fake Bank Service (tái sử dụng Resilience4j)
- Viết báo cáo đối soát (reconciliation report): so sánh tổng `pending_balance` đã trừ với tổng tiền Ledger ghi nhận CREDIT cho merchant trong ngày — phát hiện lệch số liệu nếu có bug
- Frontend: trang Merchant xem lịch sử settlement (ngày, số tiền, trạng thái); trang Admin xem chi tiết batch, có thể trigger thủ công để demo nhanh (không cần chờ tới nửa đêm)

**Demo:** thực hiện vài giao dịch QR Payment cho 1 merchant → Admin trigger settlement thủ công → xem `pending_balance` chuyển thành `settled_balance` → xem báo cáo đối soát khớp số liệu → thử giả lập Fake Bank fail để xem cơ chế retry hoạt động.

**Definition of Done:** Test case retry đúng số lần cấu hình; test case không settlement trùng lặp; báo cáo đối soát khớp 100% giữa Ledger và Settlement.

---

<a id="phase11"></a>
## Phase 11 — Hardening & Security nâng cao `Hoàn thiện`

**Mục tiêu:** Nâng cấp các phần bảo mật và vận hành lên chuẩn gần với production, làm khi các phase nghiệp vụ đã ổn định.

### Task cụ thể

- Thay JWT tự chế bằng OAuth2/OIDC thật (Keycloak hoặc Spring Authorization Server) — Gateway trở thành Resource Server, validate token qua Authorization Server
- Rate Limiting tại API Gateway (VD dùng Redis + Bucket4j hoặc Resilience4j RateLimiter) — giới hạn số request/phút cho các endpoint nhạy cảm như `/topup`, `/transfers`, `/qr/generate`
- Audit Log: ghi lại mọi hành động nhạy cảm (login, transfer, admin approve/reject, settlement trigger) vào bảng riêng hoặc hệ thống logging tập trung, có `actorId, action, target, timestamp, ipAddress`
- Integration Test toàn diện bằng Testcontainers: spin up Postgres + Kafka thật trong container khi chạy test, kiểm thử end-to-end các luồng chính (không mock)
- Load Test bằng k6 hoặc JMeter: đo throughput của endpoint Transfer và QR confirm dưới tải đồng thời, xác nhận Optimistic Lock không làm hệ thống deadlock hoặc throughput giảm bất thường
- Viết Alert Rule cơ bản trên Prometheus/Grafana (VD: cảnh báo khi tỷ lệ lỗi Settlement > 5%, khi DLQ có message tồn đọng quá lâu)

**Demo:** chạy load test 100 request transfer đồng thời vào cùng 1 ví, xác nhận số dư cuối cùng chính xác tuyệt đối, không có transaction nào bị lost update.

**Definition of Done:** Toàn bộ luồng chính có integration test bằng Testcontainers chạy được trong CI; có báo cáo load test đính kèm vào tài liệu dự án.

---

## Bảng tổng hợp toàn bộ Roadmap

| Phase | Tên | Service mới | Điểm kỹ thuật chính |
|---|---|---|---|
| 0 | Infrastructure | — | Docker Compose, Kafka, Redis, Gateway |
| 1 | Authentication | User Service | JWT, Refresh Token |
| 2 | Wallet | Wallet Service | Event-driven tạo ví tự động |
| 3 | Top-up | Payment Gateway Fake | Outbox, Idempotency, DLQ |
| 4 ⭐ | Transfer P2P | Transaction Service | Saga, Optimistic Lock, Tracing |
| 5 | Observability sớm | — | Circuit Breaker, Grafana |
| 6 | Merchant | Merchant Service | RBAC, KYB flow |
| 7 ⭐⭐⭐ | QR Payment | QR Payment Service | HMAC signature, Redis TTL, race condition |
| 8 | Real-time | — | SSE |
| 9 | Refactor Ledger | Ledger Service | Append-only, eventual consistency |
| 10 ⭐ | Settlement | Settlement Service | Batch processing, retry, reconciliation |
| 11 | Hardening | — | OAuth2, Rate Limit, Testcontainers, Load Test |
