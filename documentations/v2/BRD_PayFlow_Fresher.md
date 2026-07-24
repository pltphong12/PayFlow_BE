# BRD — PayFlow Digital Payment Platform (Fresher Edition)

*Business Requirement Document · Phiên bản rút gọn phù hợp trình độ Fresher · Dự án portfolio cá nhân, domain Banking/Fintech*

> **Ghi chú quan trọng:** Đây là bản điều chỉnh từ bản BRD gốc (11 phase, 10 service), thu gọn còn **6 phase, 4 service**, tập trung làm sâu 2 pattern giá trị nhất (Saga, Race Condition) thay vì dàn trải nhiều pattern nông. Bản gốc vẫn được giữ lại như tài liệu tham khảo — phần nào làm thêm sau này (Observability, OAuth2, Settlement batch job, Ledger Service riêng...) sẽ lấy lại từ bản gốc.

## Mục lục

1. [Giới thiệu & bối cảnh dự án](#gioithieu)
2. [Mục đích & mục tiêu nghiệp vụ](#mucdich)
3. [Đối tượng sử dụng & quyền hạn](#doituong)
4. [Giá trị hệ thống mang lại](#giatri)
5. [Use case chi tiết](#usecase)
6. [Phạm vi chức năng — MVP](#phamvi-mvp)
7. [Phạm vi mở rộng (đã làm)](#phamvi-mo)
8. [Ngoài phạm vi (để dành làm sau)](#khongthuoc)
9. [Kiến trúc mức cao](#kientruc)
10. [Yêu cầu phi chức năng](#phichucnang)
11. [Roadmap tổng quan](#roadmap)
12. [Rủi ro & giả định](#rui-ro)
13. [Tiêu chí thành công](#tieuchi)

<a id="gioithieu"></a>
## 1. Giới thiệu & bối cảnh dự án

**PayFlow** là nền tảng thanh toán số (digital payment platform) mô phỏng mô hình nghiệp vụ của ví điện tử và cổng thanh toán merchant thực tế tại Việt Nam (tương tự MoMo, ZaloPay, VNPay ở quy mô thu nhỏ). Dự án được xây dựng với mục đích portfolio cá nhân ở trình độ **Fresher**, nhằm minh chứng năng lực **hiểu sâu** một số pattern xử lý dữ liệu phân tán quan trọng nhất trong fintech (Saga, Outbox, Idempotency, Optimistic Locking, Race Condition), thay vì cố gắng phủ rộng toàn bộ hệ sinh thái microservices.

**Triết lý thiết kế của bản Fresher Edition:** *"Làm ít nhưng sâu, hiểu rõ từng dòng code, có thể trả lời được mọi câu hỏi xoáy trong phỏng vấn — thay vì làm nhiều service nhưng mỗi service chỉ hiểu bề mặt."* Đây là điểm khác biệt cốt lõi so với bản BRD gốc (11 phase, 10 service, đầy đủ Observability/OAuth2/Circuit Breaker) — bản gốc phù hợp hơn khi đã có nền tảng vững và muốn mở rộng dần sau này.

Điểm khác biệt của PayFlow so với một ứng dụng CRUD thông thường vẫn được giữ nguyên: mô phỏng đúng khái niệm cốt lõi ngành fintech — **sổ cái giao dịch (ledger)** theo nguyên tắc bút toán kép, và khái niệm **settlement** (tiền merchant nhận không "về tay" ngay mà cần quy trình xử lý riêng).

<a id="mucdich"></a>
## 2. Mục đích & mục tiêu nghiệp vụ

### 2.1 Mục đích dự án

- Xây dựng một hệ thống thanh toán số có thể demo end-to-end từ đăng ký người dùng đến thanh toán merchant qua QR, với **độ hiểu sâu 100%** ở từng dòng code.
- Chứng minh khả năng tự tay triển khai (không chỉ liệt kê tên) 2 pattern khó nhất trong fintech microservices: **Saga có compensating transaction thật**, và **xử lý race condition bằng Redis atomic operation**.
- Tạo câu chuyện kỹ thuật trung thực, có thể trình bày 15–20 phút trong phỏng vấn, kèm theo *nhật ký kỹ thuật* (bug gặp phải, quyết định đổi hướng) để chứng minh tự làm, không lắp ráp theo tutorial/AI.

### 2.2 Mục tiêu nghiệp vụ

| Mục tiêu | Mô tả chi tiết | Actor liên quan |
|---|---|---|
| Quản lý tài khoản & ví điện tử | Người dùng đăng ký, xác thực, sở hữu duy nhất 1 ví điện tử gắn với tài khoản, xem số dư và lịch sử giao dịch | `Customer` |
| Nạp tiền (Top-up) | Người dùng nạp tiền vào ví qua cổng thanh toán giả lập, có cơ chế chống trùng lặp giao dịch (Idempotency + Outbox) | `Customer` |
| Chuyển tiền P2P | Chuyển tiền giữa 2 người dùng, đảm bảo nhất quán dữ liệu bằng Saga + Optimistic Lock | `Customer` |
| Thanh toán QR cho merchant | Merchant tạo mã QR, khách hàng quét và xác nhận thanh toán, xử lý đúng khi có race condition (2 người cùng confirm 1 QR) | `Customer` `Merchant` |
| Theo dõi doanh thu & Settlement (đơn giản) | Merchant xem số dư chờ đối soát và số dư đã settle, Admin có thể trigger settlement thủ công | `Merchant` `Admin` |
| Quản trị hệ thống | Admin duyệt/từ chối merchant đăng ký | `Admin` |

<a id="doituong"></a>
## 3. Đối tượng sử dụng & quyền hạn

| Actor | Mô tả | Quyền hạn chính |
|---|---|---|
| `Customer` | Người dùng cá nhân sử dụng ví điện tử | Đăng ký/đăng nhập · Nạp tiền · Chuyển tiền · Quét QR thanh toán · Xem lịch sử giao dịch của chính mình |
| `Merchant` | Cửa hàng/đơn vị kinh doanh đã đăng ký và được duyệt | Tạo mã QR thanh toán · Xem số dư pending/settled · Yêu cầu xem lịch sử thanh toán của cửa hàng mình |
| `Admin` | Quản trị viên hệ thống | Duyệt/từ chối đăng ký merchant · Trigger settlement thủ công cho merchant |

> **Ghi chú thiết kế (giữ nguyên từ bản gốc):** Một user có thể vừa là Customer vừa sở hữu 1 merchant profile. Merchant profile và Customer wallet là 2 entity tách biệt, cùng thuộc về 1 `userId`.

<a id="giatri"></a>
## 4. Giá trị hệ thống mang lại

| Chỉ số | Ý nghĩa |
|---|---|
| **0** | Giao dịch bị trừ/cộng tiền trùng lặp (idempotent) |
| **0** | Ví bị âm số dư dù có 2 giao dịch cùng lúc động vào 1 ví (optimistic lock) |
| **1 lần duy nhất** | 1 mã QR chỉ được xác nhận thanh toán đúng 1 lần dù có nhiều request confirm cùng lúc |
| **100%** | Giao dịch được ghi sổ ledger, không thất thoát khi Saga rollback |

- Toàn bộ dòng tiền được ghi nhận minh bạch qua ledger dạng bút toán kép.
- Merchant nhận thông báo thanh toán gần như tức thời qua SSE (không cần F5).
- Kiến trúc đơn giản, dễ maintain bởi 1 người, nhưng vẫn thể hiện đúng tư duy phân tán.

<a id="usecase"></a>
## 5. Use case chi tiết

### UC-01: Đăng ký & kích hoạt tài khoản

| | |
|---|---|
| **Actor chính** | Customer |
| **Điều kiện tiên quyết** | Email chưa tồn tại trong hệ thống |
| **Luồng chính** | 1. Nhập email, mật khẩu, họ tên → 2. Hệ thống tạo user `ACTIVE` → 3. Tự động khởi tạo 1 ví balance = 0 → 4. Trả về JWT access token + refresh token |
| **Luồng thay thế** | Email đã tồn tại → lỗi 409 Conflict |

### UC-02: Nạp tiền vào ví

| | |
|---|---|
| **Actor chính** | Customer |
| **Điều kiện tiên quyết** | Đã đăng nhập, có JWT hợp lệ |
| **Luồng chính** | 1. Nhập số tiền muốn nạp → 2. Hệ thống tạo yêu cầu top-up `PENDING` → 3. Payment Gateway giả lập nội bộ (random SUCCESS/FAILED) → 4. Nếu SUCCESS: publish event qua Outbox → Wallet cộng tiền → 5. Notification (log + DB) |
| **Luồng thay thế** | Payment Gateway trả FAILED → trạng thái `FAILED`, ví không đổi |
| **Yêu cầu đặc biệt** | Idempotency key bắt buộc — retry cùng request không cộng tiền 2 lần |

### UC-03: Chuyển tiền P2P ⭐ (trọng tâm)

| | |
|---|---|
| **Actor chính** | Customer |
| **Điều kiện tiên quyết** | Người gửi đủ số dư, người nhận tồn tại và `ACTIVE` |
| **Luồng chính** | 1. Nhập người nhận + số tiền → 2. Transaction Service khởi tạo Saga → 3. Bước 1: trừ ví người gửi (optimistic lock) → 4. Bước 2: cộng ví người nhận → 5. Ghi ledger 2 bút toán (nợ/có) |
| **Luồng thay thế** | Bước 4 thất bại → compensating transaction: hoàn tiền ví người gửi, trạng thái `FAILED` |
| **Yêu cầu đặc biệt** | Không cho phép số dư âm; 2 giao dịch cùng lúc động vào 1 ví không được lost update |

### UC-04: Đăng ký & duyệt Merchant

| | |
|---|---|
| **Actor chính** | Merchant, Admin |
| **Luồng chính** | 1. User đăng ký hồ sơ merchant → 2. Trạng thái `PENDING_APPROVAL` → 3. Admin duyệt/từ chối → 4. Nếu duyệt, trạng thái `APPROVED`, được tạo QR |
| **Luồng thay thế** | Admin từ chối → `REJECTED`, kèm lý do |

### UC-05: Thanh toán QR ⭐⭐ (trọng tâm nhất)

| | |
|---|---|
| **Actor chính** | Merchant, Customer |
| **Điều kiện tiên quyết** | Merchant đã `APPROVED` |
| **Luồng chính** | 1. Merchant nhập số tiền → 2. Hệ thống sinh mã QR (ký HMAC, hạn 5 phút) → 3. Customer quét bằng camera → 4. Customer xác nhận → 5. Trừ ví Customer, cộng `pending_balance` Merchant → 6. Merchant dashboard cập nhật qua SSE |
| **Luồng thay thế** | QR hết hạn → phải tạo mã mới; Customer không đủ số dư → từ chối, không ảnh hưởng QR |
| **Yêu cầu đặc biệt** | 1 mã QR chỉ được xác nhận đúng 1 lần — **2 request confirm cùng lúc chỉ 1 request thành công** (đây là bài test race condition quan trọng nhất dự án) |

### UC-06: Settlement (đơn giản)

| | |
|---|---|
| **Actor chính** | Merchant, Admin |
| **Luồng chính** | 1. Admin gọi API trigger settlement thủ công cho 1 merchant → 2. Hệ thống chuyển toàn bộ `pending_balance` → `settled_balance` → 3. Merchant xem lại số dư đã cập nhật |
| **Yêu cầu đặc biệt** | Không settlement trùng lặp nếu gọi API 2 lần liên tiếp cho cùng thời điểm (dùng optimistic lock trên `merchant_balances`) |

> **Lưu ý:** Đây là bản Settlement **rút gọn tối đa** — không có batch job tự động, không có retry/DLQ, không có bảng `settlement_batches`/`settlement_requests` riêng như bản gốc. Mục tiêu chỉ là thể hiện hiểu đúng khái niệm "tiền chờ đối soát" khác với "tiền đã có trong ví thường".

<a id="phamvi-mvp"></a>
## 6. Phạm vi chức năng — MVP `MVP`

- Đăng ký / đăng nhập, xác thực bằng JWT
- Quản lý ví: xem số dư, lịch sử giao dịch (ledger)
- Nạp tiền (Top-up) qua Payment Gateway giả lập, có Outbox + Idempotency
- Chuyển tiền P2P giữa 2 người dùng bằng Saga + Optimistic Lock

<a id="phamvi-mo"></a>
## 7. Phạm vi mở rộng (đã đưa vào scope làm)

- Đăng ký & duyệt Merchant
- Thanh toán QR cho merchant, xử lý race condition bằng Redis atomic
- Settlement đơn giản (pending_balance → settled_balance, trigger thủ công)
- Notification real-time nhẹ qua SSE (viết trong Merchant Service, không tách service riêng)

<a id="khongthuoc"></a>
## 8. Ngoài phạm vi (để dành làm sau nếu rảnh)

> Đây là phần khác biệt lớn nhất so với bản gốc — các mục dưới đây **không phải bị loại bỏ vĩnh viễn**, mà cố tình để lại cho giai đoạn sau khi đã vững nền tảng, tham khảo bản BRD/Roadmap gốc khi triển khai.

- Tách Ledger Service thành service riêng (bản Fresher giữ ledger_entries trong Wallet Service)
- Settlement đầy đủ: batch job tự động, bảng `settlement_batches`/`settlement_requests`, retry có giới hạn, báo cáo đối soát
- Notification Service tách riêng, quản lý SseEmitter tập trung cho nhiều loại sự kiện
- Distributed Tracing (OpenTelemetry + Zipkin)
- Metrics & Dashboard (Prometheus + Grafana)
- Circuit Breaker & Retry nâng cao (Resilience4j) cho mọi lời gọi liên service
- OAuth2/OIDC (Keycloak hoặc Spring Authorization Server) thay JWT tự chế
- Rate Limiting tại API Gateway (Bucket4j)
- Audit Log tập trung
- Load Test (k6/JMeter)
- Fraud Detection cơ bản (rule-based)
- Rút tiền (withdraw) trực tiếp từ ví về ngân hàng
- Tích hợp cổng thanh toán ngân hàng thật
- Đa ngôn ngữ, đa tiền tệ
- Ứng dụng di động native

<a id="kientruc"></a>
## 9. Kiến trúc mức cao

**Luồng tổng quan:**

`React Client (Web)` → `API Gateway` → `Microservices` (User, Wallet, Transaction, Merchant) → giao tiếp bất đồng bộ qua `Kafka` (chỉ ở Outbox event & Saga event) → lưu trữ tại `PostgreSQL` (mỗi service 1 database riêng) và `Redis` (TTL cho QR code, atomic lock).

| Thành phần | Vai trò |
|---|---|
| API Gateway | Điểm vào duy nhất, xác thực JWT, routing |
| User Service | Đăng ký, đăng nhập, quản lý thông tin người dùng |
| Wallet Service | Quản lý số dư, ledger bút toán kép, xử lý top-up (Outbox) |
| Transaction Service | Điều phối Saga cho Transfer P2P và QR Payment |
| Merchant Service | Đăng ký/duyệt merchant, sinh & xác thực QR, xử lý pending/settled balance, đẩy SSE |

**So sánh với bản gốc:** 4 service thay vì 9 (không tính API Gateway), gộp Ledger vào Wallet, gộp QR Payment + Settlement + Notification vào Merchant Service, bỏ Payment Gateway Fake thành 1 class giả lập nội bộ trong Wallet Service thay vì network call riêng.

<a id="phichucnang"></a>
## 10. Yêu cầu phi chức năng

| Hạng mục | Yêu cầu cụ thể |
|---|---|
| Bảo mật | JWT cho xác thực; mã hóa mật khẩu bằng BCrypt; QR payload ký bằng HMAC-SHA256 |
| Tính nhất quán dữ liệu | Idempotency key bắt buộc cho API tạo giao dịch tiền; Saga pattern cho Transfer/QR Payment; Outbox pattern khi publish event; Optimistic Lock cho ví và merchant_balances |
| Độ tin cậy | Xử lý đúng khi Saga fail giữa chừng (compensating transaction); xử lý đúng race condition khi 2 request confirm cùng 1 QR |
| Khả năng quan sát | Structured logging + correlation ID xuyên request (KHÔNG cần Zipkin/Prometheus ở giai đoạn này) |
| Khả năng triển khai | Toàn bộ hệ thống chạy được bằng `docker compose up` trên máy local |
| Kiểm thử | Unit test cho business logic quan trọng (Saga, tính số dư); Integration test bằng Testcontainers cho Saga rollback và race condition QR — đây là 2 nơi bắt buộc phải có test, không được bỏ qua |

<a id="roadmap"></a>
## 11. Roadmap tổng quan

Roadmap chi tiết task-level trình bày trong `Roadmap_PayFlow_Fresher.md`. Tóm tắt 6 phase:

| Phase | Nội dung | Trọng tâm |
|---|---|---|
| 0 | Infrastructure | Docker Compose, Kafka, Redis, Gateway |
| 1 | Authentication | JWT, Refresh Token |
| 2 | Wallet + Ledger | Event-driven tạo ví, ledger gộp chung |
| 3 | Top-up | Outbox + Idempotency |
| 4 ⭐ | Transfer P2P | **Saga + Optimistic Lock** — đầu tư nhiều thời gian nhất |
| 5 ⭐⭐ | Merchant + QR + Settlement đơn giản + SSE | **Race condition + domain fintech** — trọng tâm thứ 2 |

<a id="rui-ro"></a>
## 12. Rủi ro & giả định

| Loại | Nội dung | Cách xử lý |
|---|---|---|
| Rủi ro kỹ thuật | Saga và race condition dễ triển khai sai dẫn đến mất tiền/nhân đôi tiền | Viết integration test riêng cho từng kịch bản lỗi giữa chừng — bắt buộc, không được bỏ qua dù ở bản rút gọn |
| Rủi ro bị nghi ngờ khi phỏng vấn | Vì bản gốc có 10 service, nhà tuyển dụng có thể hỏi "tại sao rút gọn, có phải làm không nổi không" | Chuẩn bị sẵn câu trả lời chủ động: "em thiết kế đủ 11 phase nhưng chọn làm sâu 6 phase cốt lõi trước, phần còn lại để mở rộng sau khi có nền tảng vững" — kèm theo bản BRD/Roadmap gốc như tài liệu tham khảo |
| Rủi ro tiến độ | Dễ nản ở Phase 4-5 (khó nhất) | Viết nhật ký kỹ thuật (bug + cách sửa) ngay trong lúc làm, không đợi xong mới viết — vừa giữ động lực vừa có tư liệu phỏng vấn |
| Giả định | Payment Gateway chỉ là service/class giả lập, không xử lý tiền thật | Ghi rõ trong README |

<a id="tieuchi"></a>
## 13. Tiêu chí thành công

- Demo được đầy đủ luồng chính: đăng ký → nạp tiền → chuyển tiền → đăng ký merchant → thanh toán QR → settlement thủ công.
- Toàn bộ hệ thống chạy bằng một lệnh `docker compose up`.
- **Có test tự động cho 2 kịch bản bắt buộc**: (1) Saga rollback khi credit receiver fail giữa chừng, (2) 2 request confirm cùng lúc 1 QR chỉ 1 request thành công.
- Có nhật ký kỹ thuật (technical journal) ghi lại ít nhất 3-5 bug/khó khăn thật gặp phải và cách giải quyết — dùng làm nguyên liệu trả lời phỏng vấn.
- Có thể trình bày kiến trúc và giải thích được **từng dòng code quan trọng** trong Saga/Race Condition mà không cần nhìn tài liệu.
