# Top-up bất đồng bộ với Fake Payment Gateway và Outbox

## 1. Luồng chính: 202 trước, payment và credit sau commit

```mermaid
sequenceDiagram
    autonumber
    actor C as Client
    participant GW as API Gateway
    participant WS as Wallet Service
    participant WDB as Wallet DB
    participant PS as Top-up Payment Starter
    participant FG as Fake Payment Gateway
    participant OP as Outbox Poller
    participant K as Kafka

    rect
    Note over C,WDB: Pha 1: Đồng bộ (client nhận 202 ngay)
    C->>GW: POST /topups (JWT, amount, Idempotency-Key)
    GW->>GW: Xác thực JWT
    GW->>WS: Forward + X-User-Id

    WS->>WDB: Tìm Idempotency-Key
    alt Key đã tồn tại
        WDB-->>WS: TopupRequest cũ
        WS-->>C: Trả top-up cũ (không gọi gateway lần nữa)
    else Key chưa tồn tại
        WS->>WDB: Tạo TopupRequest (PENDING)
        WS->>WS: Phát internal event TopupCreated
        WS-->>C: 202 Accepted (payment chưa hoàn tất)
    end
    end

    rect
    Note over WS,FG: Pha 2: Bất đồng bộ, sau khi transaction tạo top-up commit
    WS-)PS: TopupCreated (AFTER_COMMIT)
    PS->>FG: Yêu cầu thanh toán
    FG-->>PS: SUCCESS hoặc FAILED
    PS->>WS: Xử lý kết quả thanh toán

    alt FAILED
        WS->>WDB: TopupRequest = FAILED
        Note over WS,WDB: Không thay đổi balance
    else SUCCESS
        WS->>WDB: Kiểm tra TopupRequest vẫn PENDING
        WS->>WDB: Load wallet
        WS->>WDB: Cộng balance (optimistic locking)
        WS->>WDB: Ghi ledger credit
        WS->>WDB: Tạo outbox WalletCredited
        WS->>WDB: TopupRequest = SUCCESS
        Note over WS,WDB: Các bước trên nằm trong cùng một DB transaction
    end
    end

    rect
    Note over OP,K: Pha 3: Publish event
    OP->>WDB: Đọc outbox PENDING
    OP->>K: Publish lên topic wallet-events
    OP->>WDB: Đánh dấu outbox SENT
    end
```

## 2. Nhánh lỗi khi xử lý kết quả thanh toán

```mermaid
sequenceDiagram
    autonumber
    participant PS as Top-up Payment Starter
    participant WS as Wallet Service
    participant WDB as Wallet DB

    rect
    Note over PS,WDB: Trường hợp A: Callback SUCCESS đến hai lần
    PS->>WS: SUCCESS (lần 1)
    WS->>WDB: TopupRequest PENDING? Có
    WS->>WDB: Credit, ledger, outbox, TopupRequest = SUCCESS
    PS->>WS: SUCCESS (lần 2)
    WS->>WDB: TopupRequest PENDING? Không (đã SUCCESS)
    WS-->>PS: Bỏ qua, không credit lần nữa
    end

    rect
    Note over PS,WDB: Trường hợp B: Không tìm thấy wallet
    PS->>WS: SUCCESS
    WS->>WDB: Load wallet
    WDB-->>WS: Không tìm thấy
    Note over WS,WDB: Không credit được, top-up chưa thể hoàn tất (giữ PENDING)
    end
```

## 3. Optimistic lock conflict khi credit

```mermaid
flowchart TD
    A[Nhận kết quả SUCCESS] --> B{TopupRequest còn PENDING?}
    B -->|Không| C[Bỏ qua, không credit]
    B -->|Có| D[Load wallet, đọc balance + version]
    D --> E{Wallet tồn tại?}
    E -->|Không| F[Không credit được, top-up giữ PENDING]
    E -->|Có| G[UPDATE balance WHERE version = đã đọc]
    G --> H{Cập nhật thành công?}
    H -->|Có| I[Ghi ledger + outbox WalletCredited + TopupRequest = SUCCESS]
    H -->|Không, version đã đổi| J{Đã thử tối đa 3 lần?}
    J -->|Chưa| D
    J -->|Rồi| K[Trả lỗi, top-up chưa hoàn tất]
```

## 4. Kafka lỗi: outbox giữ PENDING để retry

```mermaid
sequenceDiagram
    autonumber
    participant OP as Outbox Poller
    participant WDB as Wallet DB
    participant K as Kafka

    Note over WDB: Top-up = SUCCESS, balance đã cộng, outbox = PENDING

    loop Mỗi chu kỳ poll
        OP->>WDB: Đọc outbox PENDING
        OP->>K: Publish WalletCredited
        alt Kafka lỗi
            K--xOP: Lỗi
            Note over OP,WDB: Outbox vẫn PENDING, thử lại ở chu kỳ sau
        else Publish thành công
            K-->>OP: ack
            OP->>WDB: Outbox = SENT
        end
    end
```

## 5. Vòng đời trạng thái TopupRequest

```mermaid
stateDiagram-v2
    [*] --> PENDING: Tạo top-up, trả 202
    PENDING --> SUCCESS: Gateway SUCCESS, đã credit wallet
    PENDING --> FAILED: Gateway FAILED (không đổi balance)
    PENDING --> PENDING: Callback trùng, wallet không tồn tại, hoặc lỗi lock (bỏ qua/retry)
    SUCCESS --> [*]
    FAILED --> [*]
```

## 6. Vòng đời trạng thái Outbox event

```mermaid
stateDiagram-v2
    [*] --> PENDING: Tạo cùng DB transaction với credit
    PENDING --> SENT: Publish Kafka thành công
    PENDING --> PENDING: Kafka lỗi (poller retry)
    SENT --> [*]
```

## 7. Xử lý đồng thời

```mermaid
flowchart TD
    subgraph A["Hai request cùng Idempotency-Key"]
        A1[Request 1 và 2 đến cùng lúc] --> A2[Cả hai INSERT TopupRequest]
        A2 --> A3{Unique constraint}
        A3 -->|Thắng| A4[Lưu top-up, trả 202, gọi gateway]
        A3 -->|Thua| A5[Đọc top-up đã có, trả lại, không gọi gateway]
    end

    subgraph B["Hai callback SUCCESS chạy đồng thời"]
        B1[Callback 1 và 2 cùng vào] --> B2[Cả hai thấy PENDING]
        B2 --> B3[Cả hai cố cập nhật balance với cùng version]
        B3 --> B4{Optimistic locking}
        B4 -->|Thắng| B5[Credit, ledger, outbox, SUCCESS]
        B4 -->|Thua| B6[Retry: đọc lại, thấy top-up đã SUCCESS]
        B6 --> B7[Bỏ qua, không credit lần hai]
    end
```