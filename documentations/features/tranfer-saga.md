# Transfer Saga: chuyển tiền, bồi hoàn và phục hồi

## 1. Luồng chính (happy path)

```mermaid
sequenceDiagram
    autonumber
    actor C as Client
    participant GW as API Gateway
    participant TS as Transaction Service
    participant TDB as Transaction DB
    participant WS as Wallet Service
    participant WDB as Wallet DB
    participant OP as Outbox Poller
    participant K as Kafka

    C->>GW: POST /transfers (JWT, Idempotency-Key)
    GW->>GW: Xác thực JWT
    GW->>TS: Forward + X-User-Id

    TS->>TDB: Tìm Idempotency-Key
    alt Key đã tồn tại, payload giống
        TDB-->>TS: Transaction cũ
        TS-->>C: Trả transaction cũ (không chạy lại Saga)
    else Key đã tồn tại, payload khác
        TS-->>C: 409 Conflict
    else Key chưa tồn tại
        TS->>WS: Lấy ví người gửi và người nhận
        WS-->>TS: Thông tin ví

        TS->>TDB: Tạo Transaction (PENDING)
        TS->>TDB: Tạo step DEBIT_SENDER, CREDIT_RECEIVER

        Note over TS,WDB: Bước 1: Trừ tiền người gửi
        TS->>WS: Debit người gửi
        WS->>WDB: Kiểm tra ledger (chống trừ lặp)
        WS->>WDB: Kiểm tra trạng thái ví, số dư
        WS->>WDB: Cập nhật balance (optimistic locking) + ghi ledger
        WS-->>TS: Debit OK
        TS->>TDB: DEBIT_SENDER = SUCCESS

        Note over TS,WDB: Bước 2: Cộng tiền người nhận
        TS->>WS: Credit người nhận
        WS->>WDB: Ghi ledger + cập nhật balance
        WS-->>TS: Credit OK

        TS->>TDB: CREDIT_RECEIVER = SUCCESS, Transaction = COMPLETED
        TS->>TDB: Tạo outbox event TransferCompleted (cùng DB transaction)
        TS-->>C: 200 Thành công

        Note over OP,K: Bất đồng bộ
        OP->>TDB: Đọc outbox PENDING
        OP->>K: Publish TransferCompleted
        OP->>TDB: Đánh dấu outbox SENT
    end
```

## 2. Nhánh lỗi: debit thất bại và bồi hoàn

```mermaid
sequenceDiagram
    autonumber
    actor C as Client
    participant TS as Transaction Service
    participant TDB as Transaction DB
    participant WS as Wallet Service

    Note over TS,WS: Trường hợp A: Debit thất bại (thiếu số dư / ví bị khóa)
    TS->>WS: Debit người gửi
    WS-->>TS: Lỗi (INSUFFICIENT_FUNDS / WALLET_LOCKED)
    TS->>TDB: DEBIT_SENDER = FAILED
    TS->>TDB: Transaction = FAILED
    TS->>TDB: Tạo outbox TransferFailed
    TS-->>C: Lỗi nghiệp vụ (không gọi credit)

    Note over TS,WS: Trường hợp B: Debit OK, Credit thất bại
    TS->>WS: Debit người gửi
    WS-->>TS: OK
    TS->>WS: Credit người nhận
    WS-->>TS: Lỗi
    TS->>TDB: Transaction = COMPENSATING
    TS->>WS: Credit lại tiền cho người gửi (hoàn tiền)

    alt Hoàn tiền thành công
        WS-->>TS: OK
        TS->>TDB: DEBIT_SENDER = COMPENSATED
        TS->>TDB: Transaction = FAILED
        TS->>TDB: Tạo outbox TransferFailed
        TS-->>C: Lỗi (đã hoàn tiền)
    else Hoàn tiền thất bại hoặc timeout
        WS--xTS: Lỗi / Timeout
        Note over TS,TDB: Giữ nguyên COMPENSATING
        TS-->>C: Lỗi tạm thời (sẽ được xử lý sau)
    end
```

## 3. Phục hồi: Saga Recovery Scheduler

```mermaid
sequenceDiagram
    autonumber
    participant SCH as Saga Recovery Scheduler
    participant TDB as Transaction DB
    participant WS as Wallet Service
    participant WDB as Wallet DB

    loop Định kỳ
        SCH->>TDB: Quét transaction kẹt (PENDING / COMPENSATING quá hạn)
        TDB-->>SCH: Danh sách transaction kẹt

        alt Transaction PENDING (debit/credit timeout)
            SCH->>WS: Retry debit/credit (cùng idempotency key của step)
            WS->>WDB: Kiểm tra ledger
            Note over WS,WDB: Ledger idempotency: đã xử lý thì trả kết quả cũ, không debit/credit lần 2
            WS-->>SCH: Kết quả
            SCH->>TDB: Cập nhật step và tiếp tục Saga
        else Transaction COMPENSATING
            SCH->>WS: Retry hoàn tiền cho người gửi
            WS->>WDB: Kiểm tra ledger (chống hoàn lặp)
            WS-->>SCH: OK
            SCH->>TDB: DEBIT_SENDER = COMPENSATED, Transaction = FAILED
            SCH->>TDB: Tạo outbox TransferFailed
        end
    end
```

## 4. Vòng đời trạng thái Transaction

```mermaid
stateDiagram-v2
    [*] --> PENDING: Tạo transaction
    PENDING --> COMPLETED: Debit OK và Credit OK
    PENDING --> FAILED: Debit thất bại
    PENDING --> COMPENSATING: Debit OK, Credit thất bại
    COMPENSATING --> FAILED: Hoàn tiền thành công
    COMPENSATING --> COMPENSATING: Hoàn tiền lỗi/timeout (retry)
    COMPLETED --> [*]
    FAILED --> [*]
```

## 5. Trạng thái của từng Saga step

```mermaid
stateDiagram-v2
    [*] --> PENDING
    PENDING --> SUCCESS: Wallet Service xử lý OK
    PENDING --> FAILED: Wallet Service từ chối
    SUCCESS --> COMPENSATED: Hoàn tiền (chỉ DEBIT_SENDER)
    SUCCESS --> [*]
    FAILED --> [*]
    COMPENSATED --> [*]
```

## 6. Xử lý đồng thời

```mermaid
flowchart TD
    subgraph A["Hai request cùng Idempotency-Key"]
        A1[Request 1 và Request 2 đến cùng lúc] --> A2[Cả hai INSERT transaction]
        A2 --> A3{Unique constraint}
        A3 -->|Thắng| A4[Tạo transaction, chạy Saga]
        A3 -->|Thua| A5[Đọc transaction đã có, trả lại kết quả]
    end

    subgraph B["Hai cập nhật balance đồng thời"]
        B1[Đọc balance + version] --> B2[UPDATE ... WHERE version = đã đọc]
        B2 --> B3{Cập nhật thành công?}
        B3 -->|Có| B4[Hoàn tất]
        B3 -->|Không, version đã đổi| B5{Đã thử tối đa 3 lần?}
        B5 -->|Chưa| B1
        B5 -->|Rồi| B6[Trả lỗi]
        B2 --> B7{Số dư sau cập nhật âm?}
        B7 -->|Có| B8[Từ chối, không cho số dư âm]
    end
```