# BRD — PayFlow Digital Payment Platform

*Business Requirement Document · Phiên bản chi tiết · Dự án portfolio cá nhân, domain Banking/Fintech*

## Mục lục

1. [Giới thiệu & bối cảnh dự án](#gioithieu)
2. [Mục đích & mục tiêu nghiệp vụ](#mucdich)
3. [Đối tượng sử dụng & quyền hạn](#doituong)
4. [Giá trị hệ thống mang lại](#giatri)
5. [Use case chi tiết](#usecase)
6. [Phạm vi chức năng — MVP](#phamvi-mvp)
7. [Phạm vi mở rộng](#phamvi-mo)
8. [Ngoài phạm vi (Out of scope)](#khongthuoc)
9. [Kiến trúc mức cao](#kientruc)
10. [Yêu cầu phi chức năng](#phichucnang)
11. [Roadmap tổng quan](#roadmap)
12. [Rủi ro & giả định](#rui-ro)
13. [Tiêu chí thành công](#tieuchi)

<a id="gioithieu"></a>
## 1. Giới thiệu & bối cảnh dự án

**PayFlow** là nền tảng thanh toán số (digital payment platform) mô phỏng lại mô hình nghiệp vụ của các ví điện tử và cổng thanh toán merchant thực tế tại Việt Nam (tương tự MoMo, ZaloPay, VNPay ở quy mô thu nhỏ). Dự án được xây dựng với mục đích portfolio cá nhân nhằm minh chứng năng lực thiết kế và triển khai hệ thống theo kiến trúc **microservices**, áp dụng các pattern xử lý dữ liệu phân tán (Saga, Outbox, Idempotency), giao tiếp bất đồng bộ qua **Kafka**, và các thực hành vận hành hiện đại (Observability, Resilience).

Điểm khác biệt của PayFlow so với một ứng dụng CRUD thông thường là việc mô phỏng đúng các khái niệm cốt lõi của ngành fintech: **sổ cái giao dịch (ledger)** theo nguyên tắc bút toán kép, **đối soát (reconciliation)**, và **settlement** — luồng nghiệp vụ đặc trưng khi tiền merchant nhận được không "về tay" ngay lập tức mà cần một quy trình xử lý theo lô (batch) để chuyển về ngân hàng.

<a id="mucdich"></a>
## 2. Mục đích & mục tiêu nghiệp vụ

### 2.1 Mục đích dự án

- Xây dựng một hệ thống thanh toán số hoàn chỉnh, có thể demo end-to-end từ đăng ký người dùng đến thanh toán merchant qua QR và settlement.
- Làm chủ và minh chứng khả năng áp dụng các pattern kiến trúc microservice thường gặp trong phỏng vấn backend/fintech: Saga, Outbox, CQRS-lite, Event-driven, Idempotency, Circuit Breaker.
- Tạo ra một dự án có câu chuyện kỹ thuật rõ ràng, có thể trình bày 20–30 phút trong buổi phỏng vấn kỹ thuật.

### 2.2 Mục tiêu nghiệp vụ

| Mục tiêu | Mô tả chi tiết | Actor liên quan |
|---|---|---|
| Quản lý tài khoản & ví điện tử | Người dùng đăng ký, xác thực, sở hữu duy nhất 1 ví điện tử gắn với tài khoản, xem số dư và lịch sử giao dịch | `Customer` |
| Nạp tiền (Top-up) | Người dùng nạp tiền vào ví thông qua một cổng thanh toán ngân hàng giả lập (fake bank gateway), hỗ trợ xử lý bất đồng bộ và cơ chế chống trùng lặp giao dịch | `Customer` |
| Chuyển tiền P2P | Chuyển tiền giữa 2 người dùng bất kỳ trong hệ thống, đảm bảo tính nhất quán dữ liệu khi 2 ví thuộc 2 service/database khác nhau | `Customer` |
| Thanh toán QR cho merchant | Merchant tạo mã QR ứng với 1 giao dịch, khách hàng quét và xác nhận thanh toán ngay trên trình duyệt, merchant nhận cập nhật trạng thái theo thời gian thực | `Customer` `Merchant` |
| Theo dõi doanh thu & Settlement | Merchant xem báo cáo doanh thu theo ngày/tháng, yêu cầu rút tiền (settlement) về tài khoản ngân hàng giả lập theo quy trình đối soát | `Merchant` |
| Quản trị hệ thống | Admin duyệt/từ chối merchant đăng ký, giám sát toàn bộ giao dịch, tra cứu audit log | `Admin` |

<a id="doituong"></a>
## 3. Đối tượng sử dụng & quyền hạn

| Actor | Mô tả | Quyền hạn chính |
|---|---|---|
| `Customer` | Người dùng cá nhân sử dụng ví điện tử | Đăng ký/đăng nhập · Nạp tiền · Chuyển tiền · Quét QR thanh toán · Xem lịch sử giao dịch của chính mình |
| `Merchant` | Cửa hàng/đơn vị kinh doanh đã đăng ký và được duyệt | Tạo mã QR thanh toán · Xem dashboard doanh thu · Yêu cầu settlement · Xem lịch sử giao dịch của cửa hàng mình (không xem được của merchant khác) |
| `Admin` | Quản trị viên hệ thống | Duyệt/từ chối đăng ký merchant · Xem toàn bộ giao dịch trong hệ thống · Xem audit log · Vô hiệu hóa tài khoản vi phạm |

> **Ghi chú thiết kế:** Một user có thể vừa là Customer vừa sở hữu 1 merchant profile (giống thực tế: chủ quán vẫn có ví cá nhân riêng để tiêu dùng). Merchant profile và Customer wallet là 2 entity tách biệt, cùng thuộc về 1 `userId`.

<a id="giatri"></a>
## 4. Giá trị hệ thống mang lại

| Chỉ số | Ý nghĩa |
|---|---|
| **< 3s** | Thời gian xác nhận thanh toán QR real-time qua SSE |
| **100%** | Giao dịch được ghi sổ ledger, không thất thoát khi có lỗi hệ thống |
| **0** | Giao dịch bị trừ/cộng tiền trùng lặp (idempotent) |
| **Có thể mở rộng** | Thêm service mới không ảnh hưởng service hiện có |

- Thanh toán nhanh, không cần cài đặt ứng dụng (chạy trên trình duyệt web).
- Toàn bộ dòng tiền được ghi nhận minh bạch qua ledger dạng bút toán kép, có thể truy vết mọi giao dịch.
- Merchant nhận thông báo thanh toán tức thời, không cần thao tác thủ công (refresh trang).
- Kiến trúc tách rời theo domain, cho phép mở rộng thêm nghiệp vụ (vay tiêu dùng, đầu tư...) mà không phải viết lại hệ thống.

<a id="usecase"></a>
## 5. Use case chi tiết

### UC-01: Đăng ký & kích hoạt tài khoản

| | |
|---|---|
| **Actor chính** | Customer |
| **Điều kiện tiên quyết** | Email/số điện thoại chưa tồn tại trong hệ thống |
| **Luồng chính** | 1. Nhập email, mật khẩu, họ tên → 2. Hệ thống tạo user với trạng thái `ACTIVE` → 3. Hệ thống tự động khởi tạo 1 ví với số dư 0 → 4. Trả về JWT access token + refresh token |
| **Luồng thay thế** | Email đã tồn tại → trả lỗi 409 Conflict |

### UC-02: Nạp tiền vào ví

| | |
|---|---|
| **Actor chính** | Customer |
| **Điều kiện tiên quyết** | Đã đăng nhập, có JWT hợp lệ |
| **Luồng chính** | 1. Nhập số tiền muốn nạp → 2. Hệ thống tạo yêu cầu top-up với trạng thái `PENDING` → 3. Gọi Payment Gateway giả lập → 4. Payment Gateway phản hồi thành công (ngẫu nhiên có thể fail để mô phỏng thực tế) → 5. Ví được cộng tiền, trạng thái chuyển `COMPLETED` → 6. Notification gửi thông báo |
| **Luồng thay thế** | Payment Gateway trả về fail → trạng thái chuyển `FAILED`, ví không thay đổi, thông báo lỗi cho user |
| **Yêu cầu đặc biệt** | Idempotency key bắt buộc — retry cùng request không được cộng tiền 2 lần |

### UC-03: Chuyển tiền P2P

| | |
|---|---|
| **Actor chính** | Customer |
| **Điều kiện tiên quyết** | Người gửi có đủ số dư, người nhận tồn tại và đang `ACTIVE` |
| **Luồng chính** | 1. Nhập người nhận + số tiền → 2. Transaction Service khởi tạo Saga → 3. Bước 1: trừ tiền ví người gửi (kèm khóa optimistic lock) → 4. Bước 2: cộng tiền ví người nhận → 5. Ghi ledger 2 bút toán (nợ/có) → 6. Thông báo cho cả 2 bên |
| **Luồng thay thế** | Bước 4 thất bại (ví người nhận lỗi) → thực hiện compensating transaction: hoàn tiền lại ví người gửi, trạng thái giao dịch `FAILED` |
| **Yêu cầu đặc biệt** | Không cho phép số dư âm; xử lý đúng khi 2 giao dịch cùng lúc động vào 1 ví |

### UC-04: Đăng ký & duyệt Merchant

| | |
|---|---|
| **Actor chính** | Merchant, Admin |
| **Luồng chính** | 1. User đăng ký thông tin merchant (tên cửa hàng, ngành hàng, số tài khoản ngân hàng nhận settlement) → 2. Hồ sơ ở trạng thái `PENDING_APPROVAL` → 3. Admin xem danh sách, duyệt hoặc từ chối → 4. Nếu duyệt, trạng thái chuyển `APPROVED`, cấp quyền truy cập Merchant Dashboard |
| **Luồng thay thế** | Admin từ chối → trạng thái `REJECTED`, kèm lý do |

### UC-05: Thanh toán QR

| | |
|---|---|
| **Actor chính** | Merchant, Customer |
| **Điều kiện tiên quyết** | Merchant đã `APPROVED` |
| **Luồng chính** | 1. Merchant nhập số tiền cần thu → 2. Hệ thống sinh mã QR (có chữ ký, thời hạn 5 phút) → 3. Customer quét mã bằng camera trình duyệt → 4. Customer xác nhận thanh toán → 5. Hệ thống trừ ví Customer, cộng vào số dư chờ đối soát của Merchant → 6. Merchant Dashboard cập nhật trạng thái tức thời qua SSE |
| **Luồng thay thế** | QR hết hạn → yêu cầu tạo mã mới; Customer không đủ số dư → từ chối giao dịch, không ảnh hưởng trạng thái QR |
| **Yêu cầu đặc biệt** | 1 mã QR chỉ được xác nhận thanh toán đúng 1 lần |

### UC-06: Settlement cho Merchant

| | |
|---|---|
| **Actor chính** | Merchant, hệ thống (batch job) |
| **Luồng chính** | 1. Cuối ngày, batch job tổng hợp toàn bộ giao dịch `SETTLED_PENDING` của từng merchant → 2. Tạo settlement request → 3. Gọi Fake Bank Service để "chuyển khoản" → 4. Thành công: trạng thái `SETTLED`; thất bại: retry theo lịch, tối đa N lần |
| **Yêu cầu đặc biệt** | Không được settlement trùng lặp cho cùng 1 giao dịch; có báo cáo đối soát để phát hiện lệch số liệu |

<a id="phamvi-mvp"></a>
## 6. Phạm vi chức năng — MVP `MVP`

- Đăng ký / đăng nhập, xác thực bằng JWT
- Quản lý ví: xem số dư, lịch sử giao dịch
- Nạp tiền (Top-up) qua Payment Gateway giả lập
- Chuyển tiền P2P giữa 2 người dùng
- Thông báo (Notification) khi có giao dịch phát sinh
- Ghi nhận sổ cái (Ledger) cho mọi giao dịch tiền

<a id="phamvi-mo"></a>
## 7. Phạm vi mở rộng `Extension`

- Đăng ký & duyệt Merchant, Merchant Dashboard
- Thanh toán QR cho merchant
- Settlement — đối soát và chuyển tiền merchant về ngân hàng giả lập
- Dashboard thời gian thực (SSE) cho Merchant
- Fraud Detection cơ bản (rule-based, phát hiện giao dịch bất thường)
- Observability đầy đủ: Metrics, Tracing, Alerting

<a id="khongthuoc"></a>
## 8. Ngoài phạm vi (Out of scope)

- Rút tiền (withdraw) trực tiếp từ ví Customer về ngân hàng — người dùng tự chuyển khoản qua kênh khác nếu cần
- Tích hợp cổng thanh toán ngân hàng thật (VNPay, Napas, Stripe) — chỉ dùng service giả lập
- Chấm điểm tín dụng, cho vay, đầu tư
- Ứng dụng di động native (chỉ triển khai trên Web)
- Đa ngôn ngữ, đa tiền tệ

<a id="kientruc"></a>
## 9. Kiến trúc mức cao

**Luồng tổng quan:**

`React Client (Web)` → `API Gateway` → `Microservices` (User, Wallet, Transaction, Merchant, Payment Gateway Fake, QR Payment, Notification, Ledger, Settlement) → giao tiếp bất đồng bộ qua `Kafka` → lưu trữ tại `PostgreSQL` (mỗi service 1 database riêng) và `Redis` (cache, TTL cho QR code, session).

| Thành phần | Vai trò |
|---|---|
| API Gateway | Điểm vào duy nhất, xác thực JWT, routing, rate limiting |
| User Service | Đăng ký, đăng nhập, quản lý thông tin người dùng |
| Wallet Service | Quản lý số dư, ví điện tử |
| Payment Gateway Fake Service | Giả lập cổng ngân hàng cho nạp tiền và settlement |
| Transaction Service | Điều phối giao dịch (Saga), xử lý transfer và thanh toán QR |
| Ledger Service | Sổ cái append-only, ghi nhận mọi bút toán |
| Merchant Service | Đăng ký, duyệt, hồ sơ merchant |
| QR Payment Service | Sinh mã QR, xác thực chữ ký, quản lý TTL |
| Settlement Service | Đối soát, chuyển tiền merchant về ngân hàng theo lô |
| Notification Service | Gửi thông báo, đẩy cập nhật real-time qua SSE |

<a id="phichucnang"></a>
## 10. Yêu cầu phi chức năng

| Hạng mục | Yêu cầu cụ thể |
|---|---|
| Bảo mật | JWT cho xác thực (giai đoạn đầu), có thể nâng cấp OAuth2/OIDC; mã hóa mật khẩu bằng BCrypt; QR payload ký bằng HMAC-SHA256 |
| Tính nhất quán dữ liệu | Idempotency key bắt buộc cho mọi API tạo giao dịch tiền; Saga pattern cho giao dịch xuyên service; Outbox pattern khi publish event |
| Độ tin cậy | Retry có giới hạn khi gọi service khác thất bại; Dead Letter Queue (DLQ) cho message xử lý lỗi; Circuit Breaker cho lời gọi Payment Gateway |
| Khả năng quan sát | Distributed tracing (OpenTelemetry + Zipkin); metrics (Prometheus + Grafana); centralized logging |
| Hiệu năng | Xác nhận thanh toán QR hiển thị cho merchant trong vòng 3 giây qua SSE |
| Khả năng triển khai | Toàn bộ hệ thống chạy được bằng `docker compose up` trên máy local |
| Kiểm thử | Unit test cho business logic quan trọng (Saga, tính toán số dư); Integration test bằng Testcontainers cho các luồng chính |

<a id="roadmap"></a>
## 11. Roadmap tổng quan

Roadmap chi tiết task-level được trình bày trong tài liệu riêng: `PayFlow_Roadmap.html`. Tóm tắt các giai đoạn lớn:

| Nhóm phase | Nội dung |
|---|---|
| Phase 0–4 | E-Wallet MVP: hạ tầng, auth, ví, top-up, transfer với Saga |
| Phase 5–9 | Payment Platform: merchant, QR payment, real-time dashboard, tách Ledger Service, Settlement |
| Phase 10 | Hardening: observability, resilience, bảo mật nâng cao, kiểm thử |

<a id="rui-ro"></a>
## 12. Rủi ro & giả định

| Loại | Nội dung | Cách xử lý |
|---|---|---|
| Rủi ro kỹ thuật | Saga và Outbox là pattern phức tạp, dễ triển khai sai dẫn đến mất tiền/nhân đôi tiền | Viết integration test riêng cho các kịch bản lỗi giữa chừng (network timeout, service crash) |
| Rủi ro tiến độ | Dự án làm dài hạn, không giới hạn thời gian, dễ bị nản giữa chừng ở các phase khó (Saga, Settlement) | Mỗi phase đều phải demo được, tạo động lực hoàn thành từng bước nhỏ |
| Giả định | Payment Gateway và Fake Bank chỉ là service giả lập, không xử lý tiền thật | Ghi rõ trong README để người đọc/nhà tuyển dụng hiểu đây là mô phỏng học thuật |

<a id="tieuchi"></a>
## 13. Tiêu chí thành công

- Demo được đầy đủ các luồng chính: đăng ký → nạp tiền → chuyển tiền → đăng ký merchant → thanh toán QR → settlement.
- Toàn bộ service khởi chạy bằng một lệnh `docker compose up`.
- Có tài liệu kiến trúc (architecture diagram), sequence diagram cho các luồng Saga/QR/Settlement, và README hướng dẫn chạy dự án.
- Có test tự động cho các nghiệp vụ quan trọng: transfer tiền, idempotency, Saga rollback.
- Có thể trình bày kiến trúc và các quyết định kỹ thuật một cách mạch lạc trong 20–30 phút phỏng vấn.
