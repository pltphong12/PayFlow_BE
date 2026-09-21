# Đăng ký tài khoản → Outbox → Kafka → tự động tạo ví

## 1. Luồng chính: đăng ký đồng bộ, tạo ví bất đồng bộ

```mermaid
sequenceDiagram
    autonumber
    actor C as Client
    participant GW as API Gateway
    participant US as User Service
    participant UDB as User DB
    participant OP as User Outbox Poller
    participant K as Kafka
    participant WS as Wallet Service
    participant WDB as Wallet DB

    rect
    Note over C,UDB: Pha 1: Đồng bộ (đăng ký)
    C->>GW: POST /register
    GW->>US: Forward public request
    US->>US: Validate dữ liệu
    US->>UDB: Kiểm tra email đã tồn tại?
    UDB-->>US: Chưa tồn tại
    US->>US: Hash password

    Note over US,UDB: Cùng một DB transaction
    US->>UDB: Tạo user
    US->>UDB: Tạo outbox UserRegistered (PENDING)
    US->>UDB: Commit
    US-->>C: 201 Created (không login, không cấp token)
    end

    rect
    Note over OP,K: Pha 2: Bất đồng bộ (publish event)
    OP->>UDB: Đọc outbox PENDING
    OP->>K: Publish UserRegistered lên topic user-events
    OP->>UDB: Đánh dấu outbox SENT
    end

    rect
    Note over K,WDB: Pha 3: Bất đồng bộ (tạo ví)
    K-)WS: Consume UserRegistered
    WS->>WDB: Ghi eventId vào processed_events
    WS->>WDB: Tạo ví (balance = 0, VND, ACTIVE)
    end
```

## 2. Nhánh lỗi: email đã tồn tại

```mermaid
sequenceDiagram
    autonumber
    actor C as Client
    participant GW as API Gateway
    participant US as User Service
    participant UDB as User DB

    C->>GW: POST /register
    GW->>US: Forward public request
    US->>US: Validate dữ liệu
    US->>UDB: Kiểm tra email đã tồn tại?
    UDB-->>US: Đã tồn tại
    US-->>C: 409 Conflict
    Note over US,UDB: Không tạo user, không tạo outbox
```

## 3. Kafka tạm thời lỗi: outbox giữ PENDING để retry

```mermaid
sequenceDiagram
    autonumber
    participant OP as User Outbox Poller
    participant UDB as User DB
    participant K as Kafka
    participant WS as Wallet Service

    Note over UDB: User đã được tạo, outbox = PENDING

    loop Mỗi chu kỳ poll
        OP->>UDB: Đọc outbox PENDING
        OP->>K: Publish UserRegistered
        alt Kafka lỗi
            K--xOP: Lỗi
            Note over OP,UDB: Outbox vẫn PENDING, thử lại ở chu kỳ sau
        else Publish thành công
            K-->>OP: ack
            OP->>UDB: Outbox = SENT
            K-)WS: Consume UserRegistered
            Note over WS: Ví được tạo trễ so với lúc đăng ký
        end
    end
```

## 4. Consumer: xử lý event trùng và chống tạo ví lặp

```mermaid
flowchart TD
    A[Wallet Service nhận UserRegistered] --> B[INSERT eventId vào processed_events]
    B --> C{eventId đã tồn tại?}
    C -->|Có, Kafka gửi lại cùng event| D[Bỏ qua, không tạo ví lần hai]
    C -->|Chưa| E["INSERT ví (userId, balance=0, VND, ACTIVE) ON CONFLICT DO NOTHING"]
    E --> F{Ví của userId đã tồn tại?}
    F -->|Có, do event khác cùng userId| G[Không làm gì, mỗi user chỉ có một ví]
    F -->|Chưa| H[Tạo ví thành công]
```

## 5. Hai event khác nhau cùng userId

```mermaid
sequenceDiagram
    autonumber
    participant K as Kafka
    participant WS as Wallet Service
    participant WDB as Wallet DB

    K-)WS: UserRegistered (eventId = E1, userId = U)
    K-)WS: UserRegistered (eventId = E2, userId = U)

    par Xử lý E1
        WS->>WDB: Ghi E1 vào processed_events
        WS->>WDB: INSERT ví (userId = U) ON CONFLICT DO NOTHING
    and Xử lý E2
        WS->>WDB: Ghi E2 vào processed_events
        WS->>WDB: INSERT ví (userId = U) ON CONFLICT DO NOTHING
    end

    Note over WS,WDB: Unique constraint trên userId: chỉ một INSERT thành công, mỗi user chỉ có một ví
```

## 6. Vòng đời Outbox event

```mermaid
stateDiagram-v2
    [*] --> PENDING: Tạo cùng DB transaction với user
    PENDING --> SENT: Publish Kafka thành công
    PENDING --> PENDING: Kafka lỗi (poller retry)
    SENT --> [*]
```