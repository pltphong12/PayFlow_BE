# PayFlow — Development Roadmap (Fresher Edition, chi tiết task-level)

*Bổ sung endpoint, event, bảng DB cụ thể cho từng phase · Đi kèm với BRD_PayFlow_Fresher.md*

> **Nguyên tắc xuyên suốt:** Chỉ 6 phase, nhưng Phase 4 và Phase 5 phải làm **kỹ nhất có thể** — đây là 2 phase quyết định toàn bộ giá trị phỏng vấn của dự án. Các phase còn lại (0-3) chỉ cần chạy đúng, không cần đầu tư quá nhiều thời gian vì kiến thức đã khá phổ biến (JWT, CRUD, event consumer cơ bản).
>
> **Quy tắc bắt buộc riêng cho bản Fresher:** Ở mỗi phase, sau khi code xong, viết ngay 3-5 dòng "nhật ký kỹ thuật" (bug gặp, quyết định, điều học được) vào file `JOURNAL.md` ở gốc repo. Đừng đợi xong hết mới viết — sẽ quên mất chi tiết.

## Mục lục

- [Phase 0 — Infrastructure](#phase0)
- [Phase 1 — Authentication](#phase1)
- [Phase 2 — Wallet + Ledger](#phase2)
- [Phase 3 — Top-up](#phase3)
- [Phase 4 ⭐⭐⭐ — Transfer P2P (Saga)](#phase4)
- [Phase 5 ⭐⭐⭐ — Merchant + QR Payment + Settlement đơn giản + SSE](#phase5)
- [Phụ lục — Danh sách để dành làm sau](#phulucsau)

---

<a id="phase0"></a>
## Phase 0 — Infrastructure `Nền tảng`

**Mục tiêu:** Có bộ khung hạ tầng chạy được bằng một lệnh, chưa cần business logic.

### Task cụ thể

- Viết `docker-compose.yml` gồm: PostgreSQL (mỗi service 1 database logic riêng), Kafka (KRaft mode, không cần Zookeeper), Kafka UI, Redis
- Setup Spring Cloud Gateway làm entry point duy nhất — chưa cần route gì, chỉ cần chạy được và trả 200 ở health check
- Các service gọi nhau qua hostname nội bộ Docker (VD: `http://wallet-service:8081`) — dùng `application.yml`/`application-docker.yml` theo profile
- Setup CI cơ bản (GitHub Actions): build + chạy unit test khi push code

### Danh sách container

| Container | Image gợi ý | Port |
|---|---|---|
| postgres | `postgres:16` | 5432 |
| kafka | `confluentinc/cp-kafka` (KRaft mode) | 9092 |
| kafka-ui | `provectuslabs/kafka-ui` | 8090 |
| redis | `redis:7-alpine` | 6379 |
| api-gateway | build từ source | 8080 |

**Demo:** chạy `docker compose up`, tất cả container `healthy`, Gateway trả 200 ở `GET /actuator/health`.

**Definition of Done:** README có hướng dẫn chạy 1 lệnh; chưa cần service business nào ở bước này.

**Thời gian gợi ý:** không cần đầu tư nhiều — đây là phần "chép lại" từ kinh nghiệm setup Docker Compose thông thường, không phải trọng tâm.

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
- Sinh JWT chứa claim `userId`, `role`, ký bằng secret key (HS256) — **không cần RS256 hay OAuth2** ở bản Fresher
- Spring Security filter chain: validate JWT ở mọi request trừ `/auth/**`
- Cấu hình Gateway route `/api/v1/auth/**` và `/api/v1/users/**` về User Service, forward JWT claims qua header (VD `X-User-Id`) cho service phía sau đọc mà không cần verify lại token

**Demo:** đăng ký → đăng nhập → gọi `GET /api/v1/users/me` thành công.

**Definition of Done:** Unit test: trùng email → 409; sai mật khẩu → 401; JWT hết hạn → 401.

**Thời gian gợi ý:** vừa phải — đây là kiến thức nền tảng quan trọng nhưng khá phổ biến, không phải điểm nhấn phỏng vấn chính.

---

<a id="phase2"></a>
## Phase 2 — Wallet + Ledger (gộp) `Wallet Service`

**Mục tiêu:** Có ví, xem được số dư = 0, có sẵn bảng ledger để dùng cho Phase 3, 4.

> **Khác biệt so với bản gốc:** bảng `ledger_entries` giữ nguyên trong Wallet Service, **không tách thành Ledger Service riêng**. Việc này đơn giản hoá kiến trúc mà vẫn giữ được khái niệm "ghi sổ bút toán kép" — giá trị domain fintech không đổi.

### Bảng dữ liệu (Wallet Service DB)

| Bảng | Cột chính |
|---|---|
| `wallets` | `id (UUID, PK)`, `user_id (unique)`, `balance (decimal)`, `currency (default VND)`, `version (int, optimistic lock)`, `status (ACTIVE/FROZEN)`, `created_at` |
| `ledger_entries` | `id`, `wallet_id (FK)`, `transaction_id`, `entry_type (DEBIT/CREDIT)`, `amount`, `balance_after`, `created_at` |

### API Endpoint

| Endpoint | Mô tả |
|---|---|
| `GET` `/api/v1/wallets/me` | Xem số dư ví của user hiện tại |
| `GET` `/api/v1/wallets/me/ledger` | Xem lịch sử bút toán (phân trang) |
| `GET` `/api/v1/wallets/internal/{userId}` | API nội bộ cho service khác truy vấn |

### Event lắng nghe

```
Consume topic: user-events
  ← UserRegistered → tự động tạo wallet mới, balance = 0
```

### Task cụ thể

- Consumer Kafka lắng nghe `UserRegistered`, tạo wallet tương ứng (làm async ngay từ đầu để quen event-driven)
- Migration DB dùng Flyway

**Demo:** đăng ký tài khoản mới → Dashboard thấy ví đã được tạo tự động, số dư 0đ.

**Definition of Done:** Test consumer Kafka: publish event `UserRegistered` giả, verify wallet được tạo đúng.

**Thời gian gợi ý:** ngắn — logic đơn giản, chỉ là 1 consumer + tạo record.

---

<a id="phase3"></a>
## Phase 3 — Top-up `Event-driven đầu tiên, gộp Payment Gateway`

**Mục tiêu:** Nạp tiền thành công, số dư tăng đúng, không cộng đôi khi retry.

> **Khác biệt so với bản gốc:** không tách `Payment Gateway Fake Service` thành 1 service network riêng. Thay vào đó, viết 1 class `FakePaymentGatewayClient` (hoặc tương tự) ngay trong Wallet Service, giả lập random SUCCESS/FAILED sau 1-2 giây bằng `CompletableFuture`/`@Async` — vẫn giữ được tính bất đồng bộ mà không cần thêm 1 service + Dockerfile + network call mới.

### Bảng dữ liệu (Wallet Service DB — bổ sung)

| Bảng | Cột chính |
|---|---|
| `topup_requests` | `id`, `user_id`, `amount`, `status (PENDING/SUCCESS/FAILED)`, `idempotency_key (unique)`, `created_at` |
| `outbox_events` | `id`, `aggregate_id`, `event_type`, `payload (jsonb)`, `status (PENDING/SENT)`, `created_at` |
| `processed_events` | `event_id (PK)`, `processed_at` — chống xử lý trùng khi consume event nhiều lần |

### API Endpoint

| Endpoint | Mô tả |
|---|---|
| `POST` `/api/v1/topup` | Header bắt buộc: `Idempotency-Key`. Tạo yêu cầu top-up, trạng thái `PENDING` |

### Luồng xử lý

```
1. Client → POST /api/v1/topup (Idempotency-Key: xxx)
2. Wallet Service: check idempotency_key đã tồn tại chưa
     - Nếu đã tồn tại: trả lại kết quả cũ, KHÔNG tạo request mới
     - Nếu chưa: tạo topup_request PENDING
3. Gọi FakePaymentGatewayClient (async, giả lập random SUCCESS/FAILED sau 1-2s)
4. Khi có kết quả:
     - SUCCESS: cộng balance + ghi ledger_entries (CREDIT) + insert outbox_events (WalletCredited)
       — TẤT CẢ trong CÙNG 1 transaction DB
     - FAILED: topup_request = FAILED, không đổi balance
5. Outbox poller (Spring @Scheduled mỗi 2 giây) đọc outbox_events PENDING → publish Kafka → đánh dấu SENT
6. Log console (hoặc lưu bảng notifications) khi WalletCredited được publish
```

### Task cụ thể

- Implement Outbox Pattern đúng chuẩn: save entity + insert outbox record trong **cùng 1 transaction**, có poller đọc và publish, đánh dấu `SENT` sau khi publish thành công
- Bảng `processed_events` — idempotency ở tầng consumer nếu có consumer khác đọc lại topic này sau này
- **Không cần** setup DLQ topic riêng, không cần Retry nâng cao cho Kafka consumer ở bản Fresher — nếu muốn thử, chỉ cần try-catch đơn giản + log lỗi là đủ

**Demo:** nạp tiền → số dư tăng đúng sau vài giây → xem Kafka UI thấy message trên topic `wallet-events` → gọi lại API với cùng Idempotency-Key, số dư không tăng thêm lần 2.

**Definition of Done:** Test case idempotency (gọi 2 lần cùng key, chỉ cộng tiền 1 lần); test Outbox không mất event khi giả lập crash giữa transaction (dùng Testcontainers).

**Thời gian gợi ý:** trung bình-cao — đây là lần đầu tiên implement Outbox thật, cần hiểu kỹ tại sao phải save trong cùng transaction.

---

<a id="phase4"></a>
## Phase 4 ⭐⭐⭐ — Transfer P2P (Saga) `TRỌNG TÂM SỐ 1 — đầu tư nhiều thời gian nhất`

**Mục tiêu:** Chuyển tiền giữa 2 user, đảm bảo không mất/nhân đôi tiền kể cả khi có lỗi giữa chừng. Đây là phase quan trọng nhất để nói chuyện trong phỏng vấn kỹ thuật — **nên dành phần lớn thời gian của cả dự án cho phase này**.

### Service mới: Transaction Service

| Bảng | Cột chính |
|---|---|
| `transactions` | `id`, `type (TRANSFER)`, `sender_user_id`, `receiver_user_id`, `amount`, `status (PENDING/COMPLETED/FAILED/COMPENSATING)`, `idempotency_key (unique)`, `created_at` |
| `saga_steps` | `id`, `transaction_id (FK)`, `step_name (DEBIT_SENDER/CREDIT_RECEIVER)`, `status (PENDING/SUCCESS/FAILED/COMPENSATED)`, `executed_at` |
| `outbox_events` | giống Phase 3 |

### Bảng dữ liệu (Wallet Service DB — bổ sung)

| Cột bổ sung ở bảng `wallets` | Mục đích |
|---|---|
| `version (int)` | Optimistic Locking — mỗi lần update balance phải kiểm tra version, nếu không khớp thì retry hoặc trả `409 Conflict` |

### API Endpoint

| Endpoint | Mô tả |
|---|---|
| `POST` `/api/v1/transfers` | Header `Idempotency-Key` bắt buộc. Body: `receiverUserId, amount` |
| `GET` `/api/v1/transfers/{id}` | Xem trạng thái 1 giao dịch chuyển tiền |
| `PUT` `/api/v1/wallets/internal/{walletId}/debit` | API nội bộ Wallet Service — trừ tiền, kiểm tra `version` |
| `PUT` `/api/v1/wallets/internal/{walletId}/credit` | API nội bộ Wallet Service — cộng tiền |

### Thiết kế Saga (Orchestration-based)

```
1. POST /api/v1/transfers → Transaction Service tạo record PENDING
2. [Saga Step 1] Gọi Wallet Service: DEBIT sender
     - FAIL (không đủ tiền / lỗi version) → transaction FAILED, dừng, KHÔNG cần compensate
     - SUCCESS → ghi saga_steps DEBIT_SENDER = SUCCESS
3. [Saga Step 2] Gọi Wallet Service: CREDIT receiver
     - SUCCESS → transaction COMPLETED, ghi saga_steps CREDIT_RECEIVER = SUCCESS
     - FAIL → transaction chuyển COMPENSATING
       → [Compensating action] Gọi lại Wallet Service: CREDIT sender (hoàn tiền)
       → transaction FAILED sau khi compensate xong
4. Ghi ledger 2 bút toán (DEBIT sender, CREDIT receiver) khi transaction COMPLETED
5. Publish TransferCompleted / TransferFailed qua Outbox → Kafka
```

### Task cụ thể — làm kỹ từng mục dưới đây

- Chọn Saga Orchestration (Transaction Service gọi trực tiếp Wallet Service qua REST nội bộ) — dễ trace, dễ debug hơn Choreography
- Optimistic Locking trên bảng `wallets`: dùng `@Version` (JPA), retry tối đa 3 lần nếu conflict — **hiểu rõ**: nếu vẫn conflict sau 3 lần thì trả lỗi rõ ràng cho client, không để treo
- **Viết integration test mô phỏng race condition**: 2 request transfer cùng lúc trừ vào cùng 1 ví → xác nhận không có ví nào bị âm tiền (dùng `CompletableFuture` hoặc `ExecutorService` bắn 2 request song song trong test)
- **Viết test mô phỏng lỗi giữa chừng**: debit sender thành công nhưng credit receiver fail (throw exception giả lập) → xác nhận compensating transaction hoàn tiền đúng, số dư sender không đổi so với trước giao dịch
- **Không cần** OpenTelemetry/Zipkin/Prometheus ở bản Fresher — thay vào đó, ghi log rõ ràng từng bước Saga (VD: `log.info("[SAGA] step=DEBIT_SENDER status=SUCCESS transactionId={}", ...)`) kèm correlation ID để có thể trace bằng mắt qua console/log file

**Demo:** chuyển tiền giữa 2 tài khoản demo → số dư cập nhật đúng cả 2 bên → giả lập lỗi (throw exception giả trong code khi credit receiver) → xem transaction tự rollback đúng, xem log các bước Saga.

**Definition of Done — bắt buộc có ít nhất 3 test case:**
1. Transfer thành công bình thường
2. Transfer fail do không đủ tiền
3. Transfer fail giữa chừng và compensate đúng (số dư sender về đúng như trước khi transfer)
4. (Khuyến khích thêm) 2 transfer đồng thời vào cùng 1 ví, verify không lost update

> **Ghi chú riêng cho Fresher:** Đây là phase bạn sẽ bị hỏi sâu nhất khi phỏng vấn. Hãy tự vẽ tay (không nhìn tài liệu) được sequence diagram của luồng thành công và luồng compensate trước khi cho là "xong phase này". Nếu chưa vẽ được, nghĩa là chưa thực sự hiểu.

---

<a id="phase5"></a>
## Phase 5 ⭐⭐⭐ — Merchant + QR Payment + Settlement đơn giản + SSE `TRỌNG TÂM SỐ 2`

**Mục tiêu:** Luồng thanh toán merchant hoàn chỉnh, tái sử dụng Saga đã xây ở Phase 4, xử lý đúng race condition khi confirm QR, có khái niệm settlement đơn giản và thông báo real-time nhẹ.

> **Khác biệt so với bản gốc:** gộp 4 phần (Merchant, QR Payment, Settlement, Notification) vào **1 service duy nhất** — `merchant-service`. Đây là service phức tạp nhất trong bản Fresher vì gộp nhiều trách nhiệm, nhưng tránh được việc phải thêm 3 service + Dockerfile + network call riêng biệt.

### Service mới: Merchant Service

| Bảng | Cột chính |
|---|---|
| `merchants` | `id`, `user_id (FK, unique)`, `business_name`, `category`, `bank_account_number`, `bank_name`, `status (PENDING_APPROVAL/APPROVED/REJECTED)`, `rejected_reason`, `created_at`, `approved_at` |
| `merchant_balances` | `id`, `merchant_id (FK)`, `pending_balance`, `settled_balance`, `version (int, optimistic lock)` |

| Lưu trữ Redis | Cột chính |
|---|---|
| `qr:{qrId}` | Value (JSON): `merchantId, amount, status (PENDING/PAID/EXPIRED), createdAt`. TTL = 300 giây |

### API Endpoint

| Endpoint | Mô tả |
|---|---|
| `POST` `/api/v1/merchants/register` | Đăng ký hồ sơ merchant, trạng thái `PENDING_APPROVAL` |
| `GET` `/api/v1/merchants/me` | Xem hồ sơ merchant của chính mình |
| `GET` `/api/v1/admin/merchants?status=PENDING_APPROVAL` | [ADMIN] Danh sách merchant chờ duyệt |
| `PUT` `/api/v1/admin/merchants/{id}/approve` | [ADMIN] Duyệt merchant |
| `PUT` `/api/v1/admin/merchants/{id}/reject` | [ADMIN] Từ chối, kèm lý do |
| `POST` `/api/v1/qr/generate` | [MERCHANT] Body: `amount`. Trả `qrId`, payload đã ký, ảnh QR |
| `GET` `/api/v1/qr/{qrId}` | Xem thông tin QR trước khi Customer xác nhận |
| `POST` `/api/v1/qr/{qrId}/confirm` | [CUSTOMER] Xác nhận thanh toán — **endpoint quan trọng nhất phase này** |
| `POST` `/api/v1/admin/merchants/{id}/settlement/trigger` | [ADMIN] Chuyển toàn bộ `pending_balance` → `settled_balance` cho 1 merchant |
| `GET` `/api/v1/merchants/me/dashboard` | Xem `pending_balance`, `settled_balance` |
| `GET` `/api/v1/notifications/subscribe/{qrId}` | [MERCHANT] Mở kết nối SSE, chờ event QR được thanh toán |

### Thiết kế payload QR & chữ ký (giữ nguyên bản gốc)

```
qrPayload = base64( merchantId + "|" + amount + "|" + qrId + "|" + expiredAt )
signature = HMAC-SHA256(qrPayload, secretKey)
QR content = qrPayload + "." + signature

Khi confirm: verify lại signature trước khi xử lý,
  đồng thời check status trong Redis phải là PENDING
  → nếu hợp lệ: set status = PAID ngay lập tức (ATOMIC — đây là phần quan trọng nhất)
```

### Xử lý race condition — phần bắt buộc phải hiểu kỹ

```
Vấn đề: 2 request POST /qr/{qrId}/confirm gửi gần như cùng lúc (VD do double-click,
hoặc 2 tab trình duyệt). Nếu chỉ check "if status == PENDING then set PAID" bằng
2 câu lệnh riêng (GET rồi SET), sẽ có race condition: cả 2 request đều đọc thấy
PENDING trước khi request nào kịp ghi PAID → cả 2 đều tưởng mình thành công →
trừ tiền Customer 2 lần.

Giải pháp: dùng Redis SETNX hoặc Lua script để gộp "check + set" thành 1 thao tác
atomic — chỉ 1 trong 2 request được phép tiếp tục xử lý, request còn lại nhận
lỗi rõ ràng ngay lập tức ("QR đã được thanh toán").
```

### Luồng thanh toán (tái sử dụng Saga Phase 4)

```
1. Merchant: POST /qr/generate → tạo qrId, lưu Redis, trả ảnh QR (dùng zxing)
2. Customer quét QR (html5-qrcode) → GET /qr/{qrId} xem trước
3. Customer: POST /qr/{qrId}/confirm
4. Merchant Service verify signature + atomic check-and-set status PENDING → PAID
5. Merchant Service gọi Transaction Service: tạo transaction type = QR_PAYMENT
     - Tái sử dụng Saga: [Step 1] DEBIT ví Customer → [Step 2] CREDIT vào
       merchant_balances.pending_balance (KHÔNG cộng thẳng vào ví thường)
     - Nếu Step 2 fail → compensate: hoàn tiền Customer, set lại QR status = PENDING
6. Merchant Service tìm SseEmitter tương ứng qrId trong Map (in-memory) → gửi "PAID"
   → Merchant dashboard tự động cập nhật, không cần F5
```

### Settlement đơn giản

```
Admin gọi POST /admin/merchants/{id}/settlement/trigger:
  1. Đọc merchant_balances hiện tại (kèm version để optimistic lock)
  2. pending_balance = 0, settled_balance += pending_balance cũ
  3. Update với WHERE version = :version — nếu conflict, trả lỗi 409, Admin thử lại
```

> Không cần bảng `settlement_batches`/`settlement_requests`, không cần retry tự động, không cần cron job. Mục tiêu chỉ là thể hiện đúng khái niệm 2 loại số dư khác nhau.

### Task cụ thể

- Dùng `zxing` để generate ảnh QR
- Circuit Breaker: **không bắt buộc** ở bản Fresher. Nếu muốn thử, chỉ áp dụng ở đúng 1 chỗ (gọi Transaction Service từ Merchant Service) và phải hiểu kỹ CLOSED/OPEN/HALF-OPEN nếu bị hỏi
- **SseEmitter cleanup**: khi merchant đóng tab hoặc timeout, phải remove emitter khỏi Map để tránh memory leak — đây là chi tiết nhỏ nhưng hay bị hỏi ("nếu không cleanup thì hậu quả gì?")
- **Test case bắt buộc, quan trọng nhất dự án**: viết integration test bắn 2 request `POST /qr/{qrId}/confirm` đồng thời (dùng `ExecutorService` với 2 thread), verify chỉ đúng 1 request trả về thành công, request còn lại nhận lỗi rõ ràng, và ví Customer chỉ bị trừ tiền đúng 1 lần

**Demo (rất ấn tượng, nên quay video):** mở 2 tab trình duyệt (1 tab Merchant, 1 tab Customer) → Merchant tạo QR → Customer quét bằng camera điện thoại thật (hoặc nhập tay qrId để test nhanh) → xác nhận → Merchant tab tự động đổi trạng thái "Đã thanh toán" trong 1-2 giây, không cần F5.

**Definition of Done:**
1. Test case QR hết hạn không xác nhận được
2. **Test case xác nhận 2 lần cùng lúc 1 QR chỉ 1 lần thành công (bắt buộc, quan trọng nhất)**
3. Test case chữ ký bị sửa đổi (giả mạo) bị từ chối
4. Test case settlement không bị cộng trùng khi gọi trigger 2 lần liên tiếp

> **Ghi chú riêng cho Fresher:** Nếu chỉ được chọn 1 đoạn code để "khoe" trong phỏng vấn, hãy chọn đoạn xử lý race condition này (Lua script/SETNX + test 2 thread đồng thời). Đây là bằng chứng rõ ràng nhất cho việc bạn hiểu concurrency thật, không phải học thuộc khái niệm.

---

<a id="phulucsau"></a>
## Phụ lục — Danh sách để dành làm sau (khi rảnh, đã có nền tảng vững)

Khi muốn mở rộng, quay lại tham khảo bản Roadmap gốc (`Roadmap_Payload.md`) cho phần chi tiết task-level của các mục dưới đây:

| Mục | Lấy từ Phase nào trong bản gốc |
|---|---|
| Tách Ledger Service riêng | Phase 9 |
| Settlement đầy đủ (batch job, retry, DLQ, báo cáo đối soát) | Phase 10 |
| Notification Service tách riêng, quản lý SSE tập trung | Phase 8 |
| Observability đầy đủ (Zipkin, Prometheus, Grafana) | Phase 5 |
| Circuit Breaker + Retry nâng cao toàn hệ thống | Phase 5 |
| OAuth2/OIDC thay JWT tự chế | Phase 11 |
| Rate Limiting, Audit Log, Load Test | Phase 11 |

**Gợi ý thứ tự làm thêm nếu rảnh:** Observability (dễ thấy giá trị ngay, học được nhiều) → Settlement đầy đủ → Tách Ledger Service → OAuth2 (khó nhất, để cuối).

---

## Bảng tổng hợp Roadmap Fresher Edition

| Phase | Tên | Service | Điểm kỹ thuật chính | Mức đầu tư thời gian |
|---|---|---|---|---|
| 0 | Infrastructure | — | Docker Compose, Kafka, Redis, Gateway | Thấp |
| 1 | Authentication | User Service | JWT, Refresh Token | Trung bình |
| 2 | Wallet + Ledger | Wallet Service | Event-driven tạo ví, ledger gộp | Thấp |
| 3 | Top-up | Wallet Service (gộp Payment Gateway) | Outbox, Idempotency | Trung bình-Cao |
| 4 ⭐⭐⭐ | Transfer P2P | Transaction Service | **Saga, Optimistic Lock** | **Cao nhất** |
| 5 ⭐⭐⭐ | Merchant + QR + Settlement + SSE | Merchant Service | **Race condition, HMAC, SSE** | **Cao nhất** |
