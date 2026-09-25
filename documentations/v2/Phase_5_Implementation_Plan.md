# Phase 5 — Kế hoạch triển khai theo thread

## Mục đích

Phase 5 xây luồng Merchant + QR Payment + Settlement đơn giản + SSE. File này là
nguồn handoff giữa các thread: mỗi thread chỉ thực hiện **một bước**, xác nhận
Definition of Done của bước đó, cập nhật trạng thái bên dưới rồi dừng.

Nguồn yêu cầu chi tiết: `documentations/v2/Roadmap_PayFlow_Fresher.md`.
Code, Flyway migration và test hiện có luôn là nguồn sự thật nếu khác tài liệu.

## Quy tắc xuyên suốt

- Merchant Service sở hữu Merchant DB; không tạo FK hoặc query sang User/Wallet DB.
- Money dùng `BigDecimal` và PostgreSQL `DECIMAL(19,2)`; không dùng `double`.
- Money mutation phải idempotent, có database constraint và optimistic locking khi
  có cạnh tranh ghi.
- Chỉ Gateway là entry point public. Internal API participant không được route qua
  Gateway.
- Không hardcode secret. `QR_HMAC_SECRET` đến từ environment / local ignored config.
- Giữ response `ApiResponse`, Bean Validation, `BusinessException` và HTTP status
  theo convention hiện có.
- Không tự thêm Notification Service, Ledger Service, batch/cron settlement,
  circuit breaker toàn hệ thống hoặc observability stack.
- Sau mỗi bước: chạy test đúng scope, `git diff --check`, và ghi Journal khi bước
  tạo ra quyết định kỹ thuật/bug đáng lưu ý.

## Trạng thái hiện tại

| Bước | Trạng thái | Ghi chú |
|---|---|---|
| 1. Bootstrap Merchant Service | Hoàn thành | Module, Flyway schema, JPA entity/repository và persistence test đã có. |
| 2. Merchant registration & approval | Chưa làm | Bước tiếp theo. |
| 3. Merchant balance participant | Chưa làm | Internal API nhận credit pending balance. |
| 4. QR payment Saga | Chưa làm | Mở rộng Transaction Service cho `QR_PAYMENT`. |
| 5. QR generate & inspect | Chưa làm | Redis TTL, HMAC và ZXing. |
| 6. Atomic QR confirm | Chưa làm | Invariant concurrency trọng tâm. |
| 7. SSE notification | Chưa làm | In-memory emitter, bắt buộc cleanup. |
| 8. Settlement & dashboard | Chưa làm | Optimistic lock cho settlement. |
| 9. End-to-end hardening | Chưa làm | Test integration bắt buộc, compose/demo/JOURNAL. |

## Bước 1 — Bootstrap Merchant Service

### Mục tiêu

Tạo module `merchant-service` chạy độc lập với database riêng; schema khớp JPA và
được kiểm tra bằng PostgreSQL Testcontainers.

### Phạm vi đã hoàn thành

- Module Maven, Spring Boot application, Dockerfile, profile local/docker.
- Database `payflow_merchant_db`, cấu hình PostgreSQL và Redis.
- Migration tạo `merchants` và `merchant_balances`.
- Entity/repository `Merchant`, `MerchantBalance`, `MerchantStatus`.
- Constraint: merchant user duy nhất, balance duy nhất theo merchant, số dư không
  âm, FK nội bộ Merchant DB và optimistic version.
- Persistence test: Flyway/JPA mapping, unique constraint, FK và non-negative check.

### Đã kiểm chứng

```text
mvn -pl services/merchant-service -am test
Tests run: 5, Failures: 0, Errors: 0
```

## Bước 2 — Merchant registration & approval

### Mục tiêu

Cho Customer đăng ký hồ sơ Merchant; Admin xem, approve hoặc reject. Chỉ Merchant
`APPROVED` mới được dùng các chức năng QR ở bước sau.

### Luồng

```text
Customer register
  -> merchants(PENDING_APPROVAL)

Admin approve
  -> merchants(APPROVED, approved_at)
  -> merchant_balances(pending=0, settled=0)

Admin reject
  -> merchants(REJECTED, rejected_reason)
```

### Cần triển khai

- Request/response DTO cho register, profile, admin list và reject reason.
- `MerchantService` chứa toàn bộ business rule.
- Controller:
  - `POST /api/v1/merchants/register`
  - `GET /api/v1/merchants/me`
  - `GET /api/v1/admin/merchants?status=PENDING_APPROVAL`
  - `PUT /api/v1/admin/merchants/{id}/approve`
  - `PUT /api/v1/admin/merchants/{id}/reject`
- Đọc `X-User-Id` và `X-User-Role` chỉ như identity do Gateway cấp.
- Authorization rõ ràng: Customer tự đăng ký/xem hồ sơ; Admin mới list/approve/reject.
- Chuyển trạng thái chỉ hợp lệ:
  - `PENDING_APPROVAL -> APPROVED`
  - `PENDING_APPROVAL -> REJECTED`
  - Không được approve/reject lần hai.
- Approve phải tạo đúng một `MerchantBalance`, kể cả retry/race.

### Test bắt buộc

- Register tạo Merchant `PENDING_APPROVAL`.
- Một user không đăng ký được hai Merchant.
- Non-admin bị `403` ở admin endpoint.
- Approve tạo một balance zero duy nhất.
- Approve/reject Merchant không còn pending bị `409`.
- Reject không có lý do hợp lệ bị `400`.

### Definition of Done

- API contract dùng `ApiResponse`, validation và status code đúng.
- Không thêm balance trước khi approve.
- Test service/controller liên quan pass.

## Bước 3 — Merchant balance participant

### Mục tiêu

Merchant Service trở thành Saga participant: Transaction Service có thể credit
`pending_balance`, nhưng không thể thao tác public qua Gateway.

### Cần triển khai

- Internal endpoint chỉ trong Merchant Service, ví dụ:
  `PUT /api/v1/merchants/internal/{merchantId}/balances/pending/credit`.
- Command chứa `transactionId` và `amount`.
- Lưu idempotency của money mutation trong Merchant DB trước khi/đồng thời với
  balance mutation; retry cùng transaction không được cộng lần hai.
- Optimistic-lock conflict có retry giới hạn hoặc trả `409` rõ ràng.
- Mỗi credit chỉ dành cho Merchant `APPROVED`.

### Test bắt buộc

- Credit thành công tăng pending balance đúng một lần.
- Cùng `transactionId` retry không cộng thêm.
- Merchant chưa approved/rejected không nhận credit.
- Hai mutation cạnh tranh không lost update.

### Definition of Done

- Internal route không có trong Gateway.
- Không có public endpoint trực tiếp sửa Merchant balance.
- Money mutation idempotent và concurrency-safe.

## Bước 4 — QR payment Saga

### Mục tiêu

Mở rộng Transaction Service từ P2P transfer sang `QR_PAYMENT`: debit Customer,
credit `merchant_balances.pending_balance`, và compensate Customer nếu credit
merchant thất bại xác định.

### Cần triển khai

- Migration forward-only Transaction DB: type/constraint/schema cần thiết cho
  Merchant receiver.
- `TransactionType.QR_PAYMENT` và Saga step cho merchant credit.
- Contract HTTP Transaction Service -> Merchant Service; không import entity giữa
  services.
- Idempotency key QR payment: cùng operation trả cùng transaction/result.
- Recovery phân biệt outcome xác định (4xx) với không xác định (timeout/5xx):
  - 4xx ở credit merchant: compensate Customer.
  - timeout/5xx: giữ recoverable state và trả `503`.

### Test bắt buộc

- Success: Customer debit một lần, Merchant pending credit một lần.
- Credit merchant 4xx: Customer được refund, Merchant không credit.
- timeout/5xx: không mark terminal sai và recovery có thể tiếp tục.
- Retry/recovery không double debit/credit/refund.

### Definition of Done

- Transfer P2P regression vẫn pass.
- Không mark `COMPLETED` trước khi merchant credit được xác nhận.

## Bước 5 — QR generate & inspect

### Mục tiêu

Merchant approved sinh QR có TTL 300 giây, chữ ký HMAC-SHA256 và ảnh QR.
Customer có thể xem QR trước khi confirm.

### Cần triển khai

- Thêm ZXing chỉ trong Merchant Service.
- Redis data `qr:{qrId}` chứa tối thiểu: merchantId, amount, expiry/status và
  payload/signature cần thiết để verify.
- Payload:

```text
base64(merchantId|amount|qrId|expiredAt).HMAC_SHA256_signature
```

- API:
  - `POST /api/v1/qr/generate`
  - `GET /api/v1/qr/{qrId}`
- Return `qrId`, signed payload và QR image theo contract đã chốt trong thread đó.

### Test bắt buộc

- Merchant chưa approved không generate QR.
- Amount không dương bị từ chối.
- QR hết TTL không còn xác nhận được.
- Payload/signature bị sửa bị từ chối.

### Definition of Done

- Secret không xuất hiện trong log/response.
- TTL thực sự do Redis thực thi, không chỉ so sánh thời gian ở Java.

## Bước 6 — Atomic QR confirm

### Mục tiêu

Đảm bảo hai request confirm cùng QR không thể cùng debit Customer. Đây là invariant
quan trọng nhất Phase 5.

### State và luồng đề xuất

```text
PENDING --atomic claim--> PROCESSING --Saga success--> PAID
                                  └--determinate fail + refund--> PENDING
```

`PROCESSING` an toàn hơn ghi `PAID` trước khi Saga hoàn tất. Với outcome không xác
định, giữ `PROCESSING` để tránh charge lần hai cho đến khi transaction recovery có
kết luận.

### Cần triển khai

- `POST /api/v1/qr/{qrId}/confirm`.
- Verify QR signature, expiry và customer identity.
- Claim phải là **một thao tác Redis atomic** (Lua script hoặc equivalent); cấm
  `GET` status rồi `SET` status tách rời.
- Chỉ winner được gọi Transaction Service; loser nhận lỗi `409` rõ ràng.
- Compensation thành công mới được trả QR về `PENDING`.
- Saga transaction ID/key phải liên kết được với QR để trace/recovery.

### Test bắt buộc

- Dùng `ExecutorService` + `CountDownLatch`/barrier, không dùng `sleep`.
- Hai confirm đồng thời: chính xác một success.
- Customer debit đúng một lần; Merchant pending credit đúng một lần.
- QR expired và tampered đều bị từ chối.
- Merchant credit fail xác định: Customer refund và QR return `PENDING`.

### Definition of Done

- Không còn race `check-then-set`.
- Concurrency integration test là test bắt buộc, không được mock mất Redis atomicity.

## Bước 7 — SSE notification

### Mục tiêu

Merchant nhận event payment QR thành công gần như ngay lập tức.

### Cần triển khai

- `GET /api/v1/notifications/subscribe/{qrId}` trả `SseEmitter`.
- Xác minh Merchant sở hữu QR trước khi subscribe.
- Registry in-memory theo `qrId`.
- Sau QR Saga thành công, gửi `PAID`.
- Cleanup emitter tại `onCompletion`, `onTimeout`, `onError`; không rò memory.

### Test bắt buộc

- Merchant khác không subscribe được QR.
- Paid event gửi đúng QR.
- Emitter bị remove sau timeout/error/completion.

### Definition of Done

- Tài liệu/Journal ghi rõ giới hạn: in-memory SSE chỉ phù hợp một instance trong
  Fresher scope.

## Bước 8 — Settlement & dashboard

### Mục tiêu

Admin chuyển toàn bộ pending balance sang settled balance; Merchant xem dashboard.

### Cần triển khai

- `GET /api/v1/merchants/me/dashboard`.
- `POST /api/v1/admin/merchants/{id}/settlement/trigger`.
- Trong local transaction:

```text
pending = 0
settled = settled + pending_cũ
```

- Dùng `@Version`; optimistic conflict trả `409`, không giả thành công.

### Test bắt buộc

- Settlement đúng số tiền.
- Trigger liên tiếp khi pending đã bằng 0 không cộng trùng.
- Concurrent settlement không làm mất/cộng trùng tiền.
- Non-admin bị `403`; Merchant chỉ xem dashboard của mình.

### Definition of Done

- Không thêm settlement batch, cron, retry/DLQ hoặc table ngoài scope.

## Bước 9 — End-to-end hardening và bàn giao

### Mục tiêu

Xác nhận toàn bộ Phase 5 hoạt động qua Gateway/Compose và ghi lại các quyết định
kỹ thuật có thể trình bày trong phỏng vấn.

### Cần thực hiện

- Chạy merchant-service test, Transaction Service regression và reactor verify:

```powershell
mvn -B -ntp verify
git diff --check
```

- Kiểm tra Docker Compose có Merchant Service, Redis dependency, Merchant DB và
  Gateway routes cần thiết.
- Chạy manual smoke flow: register Merchant -> Admin approve -> generate QR ->
  confirm -> SSE paid -> dashboard -> settlement.
- Cập nhật `JOURNAL.md` 3–5 dòng: quyết định atomic claim, lỗi/thách thức thật,
  cách test concurrency và giới hạn SSE in-memory.

### Definition of Done Phase 5

- QR expired/tampered bị từ chối.
- Hai confirm đồng thời có đúng một success; Customer bị trừ đúng một lần.
- Failure merchant credit compensate/refund đúng.
- Settlement không cộng trùng.
- SSE cleanup đầy đủ.
- Không commit `target/`, `.env`, `application-local.yml` hay artifact.

## Mẫu mở thread mới

```text
ĐÃ RÕ. Hãy đọc documentations/v2/Phase_5_Implementation_Plan.md.
Chỉ hướng dẫn, không tự chỉnh file. Chúng ta đang làm Bước <N>.
Trước khi đưa snippet, hãy kiểm tra code/migration/test hiện có và:
1) giải thích luồng,
2) chia thành checkpoint nhỏ,
3) nêu file tạo/sửa và test cần chạy,
4) không triển khai chức năng của bước sau.
```
