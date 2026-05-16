# Rate Limiter — LLD

> **Read once. Recall everything.**
> 2 design patterns · 7 classes · Thread-safe per-user request throttling

---

## Table of Contents

1. [Problem Statement](#1-problem-statement)
2. [Requirements](#2-requirements)
3. [Design Patterns Used](#3-design-patterns-used)
4. [Class Diagram](#4-class-diagram)
5. [Entity Diagram](#5-entity-diagram)
6. [Complete Application Flow — End to End](#6-complete-application-flow--end-to-end)
7. [Key Flows — Sequence Diagrams](#7-key-flows--sequence-diagrams)
8. [Algorithm Flowcharts](#8-algorithm-flowcharts)
9. [Thread Safety Analysis](#9-thread-safety-analysis)

---

## 1. Problem Statement

Design a **Rate Limiter** that controls how many requests a user can make to a service within a given time period. When a user exceeds the allowed rate, subsequent requests must be rejected until the limit resets or tokens are replenished.

### Context

In modern distributed systems, APIs are exposed to many concurrent users. Without rate limiting:
- A single abusive client can exhaust server resources.
- Downstream services get overwhelmed unexpectedly.
- Fair usage guarantees for other users break down.
- SLA/cost boundaries cannot be enforced.

A Rate Limiter sits in front of a service and enforces per-user request quotas. The limiting algorithm must be **configurable**, **swappable at runtime**, and **thread-safe** under concurrent access.

### Core Capabilities Required

| Capability | Description |
|---|---|
| Allow request | Let a request through if within the allowed rate |
| Reject request | Block a request if the rate limit is exceeded |
| Per-user tracking | Each user has their own independent rate-limit state |
| Strategy swap | The algorithm can be changed at runtime without changing client code |
| Concurrency safety | Correct behavior under simultaneous multi-threaded access |

---

## 2. Requirements

### Functional Requirements

- Accept or reject incoming requests per user based on a configurable rate-limit policy.
- Support two rate-limiting algorithms:
  - **Fixed Window** — Allow up to `N` requests within a fixed time window (e.g., 5 req / 10 sec). Counter resets when the window expires.
  - **Token Bucket** — Each user has a bucket of capacity `C`. Tokens refill at rate `R` per second. Each request consumes one token. Request is rejected if the bucket is empty.
- Each algorithm maintains per-user state (separate limit tracking per user ID).
- The rate-limiting strategy must be injectable and swappable at runtime.
- The service must be a singleton — one shared instance manages all requests.

### Non-Functional Requirements

- **Thread Safety** — Multiple threads may call `handleRequest()` for the same user concurrently; state must not be corrupted.
- **Performance** — Per-request overhead must be O(1).
- **Extensibility** — New algorithms can be added without modifying existing classes (Open/Closed Principle).
- **Scalability** — Per-user state is stored in a map; new users are initialized lazily on first request.

---

## 3. Design Patterns Used

| Pattern | Where Applied | Purpose |
|---|---|---|
| **Strategy** | `RateLimitingStrategy` interface + implementations | Allows the rate-limiting algorithm to be selected and swapped at runtime without changing `RateLimiterService` |
| **Singleton** | `RateLimiterService.getInstance()` | Ensures only one service instance exists; centralizes all request routing |

**Why Strategy?** Different algorithms (Fixed Window, Token Bucket, Sliding Window, Leaky Bucket) have completely different internal state and logic. Strategy decouples *policy selection* (done in config/main) from *policy enforcement* (done inside the strategy). `RateLimiterService` only ever knows the `RateLimitingStrategy` interface.

**Why Singleton?** The `RateLimiterService` holds a reference to the active strategy and is the single gate for all requests. Singleton ensures no ambiguity about which instance holds the live strategy.

---

## 4. Class Diagram

```mermaid
classDiagram
    class RateLimiterService {
        -RateLimiterService instance$
        -RateLimitingStrategy rateLimitingStrategy
        -RateLimiterService()
        +getInstance()$ RateLimiterService
        +setRateLimitingStrategy(RateLimitingStrategy) void
        +handleRequest(String userId) void
    }

    class RateLimitingStrategy {
        <<interface>>
        +allowRequest(String userId) boolean
    }

    class FixedWindowStrategy {
        -int maxRequests
        -long windowSizeInMillis
        -Map~String, UserRequestInfo~ userRequestMap
        +FixedWindowStrategy(int maxRequests, long windowSizeInSeconds)
        +allowRequest(String userId) boolean
    }

    class UserRequestInfo {
        +long windowStart
        +AtomicInteger requestCount
        +UserRequestInfo(long startTime)
        +reset(long newStart) void
    }

    class TokenBucketStrategy {
        -int capacity
        -int refillRatePerSecond
        -Map~String, TokenBucket~ userBuckets
        +TokenBucketStrategy(int capacity, int refillRatePerSecond)
        +allowRequest(String userId) boolean
    }

    class TokenBucket {
        +int tokens
        +int capacity
        +int refillRatePerSecond
        +long lastRefillTimestamp
        +TokenBucket(int capacity, int refillRatePerSecond, long currentTimeMillis)
        +refill(long currentTime) void
    }

    class RateLimiterDemo {
        +main(String[] args)$ void
        -runFixedWindowDemo(String userId)$ void
        -runTokenBucketDemo(String userId)$ void
    }

    RateLimiterService --> RateLimitingStrategy : delegates to
    RateLimitingStrategy <|.. FixedWindowStrategy : implements
    RateLimitingStrategy <|.. TokenBucketStrategy : implements
    FixedWindowStrategy +-- UserRequestInfo : inner class
    TokenBucketStrategy +-- TokenBucket : inner class
    RateLimiterDemo --> RateLimiterService : uses
    RateLimiterDemo --> FixedWindowStrategy : creates
    RateLimiterDemo --> TokenBucketStrategy : creates
```

---

## 5. Entity Diagram

The entities are in-memory data objects that hold per-user rate-limit state. Each strategy maintains its own `ConcurrentHashMap` keyed by `userId`.

```mermaid
erDiagram
    USER {
        string userId PK
    }

    USER_REQUEST_INFO {
        long windowStart "epoch ms — when current window started"
        int requestCount "requests made so far in current window"
    }

    TOKEN_BUCKET {
        int tokens "current available tokens 0..capacity"
        int capacity "max tokens — controls burst size"
        int refillRatePerSecond "tokens added per second"
        long lastRefillTimestamp "epoch ms of last refill calculation"
    }

    FIXED_WINDOW_STRATEGY {
        int maxRequests "max allowed requests per window"
        long windowSizeInMillis "window duration in milliseconds"
    }

    TOKEN_BUCKET_STRATEGY {
        int capacity "shared bucket capacity for all users"
        int refillRatePerSecond "shared refill rate for all users"
    }

    USER ||--o| USER_REQUEST_INFO : "tracked by (Fixed Window)"
    USER ||--o| TOKEN_BUCKET : "tracked by (Token Bucket)"
    FIXED_WINDOW_STRATEGY ||--o{ USER_REQUEST_INFO : "manages one entry per user"
    TOKEN_BUCKET_STRATEGY ||--o{ TOKEN_BUCKET : "manages one bucket per user"
```

---

## 6. Complete Application Flow — End to End

> The full `RateLimiterDemo.main()` call chain — both strategies, in order.

```mermaid
sequenceDiagram
    actor Demo as RateLimiterDemo
    participant Service as RateLimiterService
    participant FWS as FixedWindowStrategy
    participant TBS as TokenBucketStrategy
    participant Info as UserRequestInfo
    participant Bucket as TokenBucket
    participant Pool as ExecutorService

    Note over Demo,Pool: ── PHASE 1: Fixed Window Demo (maxRequests=5, window=10s) ──

    Demo->>FWS: new FixedWindowStrategy(5, 10)
    Demo->>Service: getInstance() [synchronized, lazy-init singleton]
    Service-->>Demo: singleton instance
    Demo->>Service: setRateLimitingStrategy(fixedWindowStrategy)
    Demo->>Pool: Executors.newFixedThreadPool(3)

    loop 10 requests, 500ms apart
        Demo->>Pool: executor.submit(→ handleRequest("user123"))
        Pool->>Service: handleRequest("user123")
        Service->>FWS: allowRequest("user123")
        FWS->>Info: putIfAbsent / get UserRequestInfo
        FWS->>Info: synchronized — check window + count
        alt requestCount < 5 and window not expired
            Info-->>FWS: true
            FWS-->>Service: true
            Service-->>Pool: print "Request allowed"
        else requestCount >= 5
            Info-->>FWS: false
            FWS-->>Service: false
            Service-->>Pool: print "Rate limit exceeded"
        else window expired
            FWS->>Info: reset(now)
            Info-->>FWS: true
            FWS-->>Service: true
            Service-->>Pool: print "Request allowed"
        end
        Note over Demo: Thread.sleep(500ms)
    end

    Demo->>Pool: executor.shutdown()

    Note over Demo,Pool: ── PHASE 2: Token Bucket Demo (capacity=5, refillRate=1/s) ──

    Demo->>TBS: new TokenBucketStrategy(5, 1)
    Demo->>Service: setRateLimitingStrategy(tokenBucketStrategy)
    Demo->>Pool: Executors.newFixedThreadPool(2)

    loop 10 requests, 300ms apart
        Demo->>Pool: executor.submit(→ handleRequest("user123"))
        Pool->>Service: handleRequest("user123")
        Service->>TBS: allowRequest("user123")
        TBS->>Bucket: putIfAbsent / get TokenBucket
        TBS->>Bucket: synchronized — refill(now)
        Note over Bucket: tokensToAdd = ⌊elapsed_ms / 1000⌋ × refillRate
        alt tokens > 0
            Bucket-->>TBS: true (tokens--)
            TBS-->>Service: true
            Service-->>Pool: print "Request allowed"
        else tokens == 0
            Bucket-->>TBS: false
            TBS-->>Service: false
            Service-->>Pool: print "Rate limit exceeded"
        end
        Note over Demo: Thread.sleep(300ms) — faster than refill rate
    end

    Demo->>Pool: executor.shutdown()
```

---

## 7. Key Flows — Sequence Diagrams

### Flow 1 — Fixed Window: Request Allowed

```mermaid
sequenceDiagram
    actor Client
    participant Service as RateLimiterService
    participant FWS as FixedWindowStrategy
    participant Info as UserRequestInfo

    Client->>Service: handleRequest("user123")
    Service->>FWS: allowRequest("user123")
    FWS->>FWS: currentTime = System.currentTimeMillis()
    FWS->>Info: putIfAbsent("user123", new UserRequestInfo(now))
    Note over Info: windowStart=now, requestCount=0
    FWS->>Info: synchronized(requestInfo)
    Note over FWS,Info: (now − windowStart) < windowSizeInMillis → still in window
    FWS->>Info: requestCount(2) < maxRequests(5) → incrementAndGet() → 3
    FWS-->>Service: true
    Service-->>Client: "Request from user123 is allowed"
```

---

### Flow 2 — Fixed Window: Request Rejected (Limit Hit)

```mermaid
sequenceDiagram
    actor Client
    participant Service as RateLimiterService
    participant FWS as FixedWindowStrategy
    participant Info as UserRequestInfo

    Client->>Service: handleRequest("user123")
    Service->>FWS: allowRequest("user123")
    FWS->>FWS: currentTime = now
    FWS->>Info: get existing UserRequestInfo
    Note over Info: windowStart=T0, requestCount=5
    FWS->>Info: synchronized(requestInfo)
    Note over FWS,Info: (now − T0) < windowSizeInMillis → still in window
    FWS->>Info: requestCount(5) >= maxRequests(5) → REJECT
    FWS-->>Service: false
    Service-->>Client: "Request from user123 is rejected: Rate limit exceeded"
```

---

### Flow 3 — Fixed Window: Window Expired, Counter Reset, Request Allowed

```mermaid
sequenceDiagram
    actor Client
    participant Service as RateLimiterService
    participant FWS as FixedWindowStrategy
    participant Info as UserRequestInfo

    Note over Client: Request arrives after window expiry
    Client->>Service: handleRequest("user123")
    Service->>FWS: allowRequest("user123")
    FWS->>FWS: currentTime = T2
    FWS->>Info: get existing UserRequestInfo
    Note over Info: windowStart=T0, requestCount=5
    FWS->>Info: synchronized(requestInfo)
    Note over FWS,Info: (T2 − T0) >= windowSizeInMillis → WINDOW EXPIRED
    FWS->>Info: reset(T2) — windowStart=T2, requestCount=0
    FWS->>Info: requestCount(0) < maxRequests(5) → incrementAndGet() → 1
    FWS-->>Service: true
    Service-->>Client: "Request from user123 is allowed"
```

---

### Flow 4 — Token Bucket: Request Allowed (Tokens Available)

```mermaid
sequenceDiagram
    actor Client
    participant Service as RateLimiterService
    participant TBS as TokenBucketStrategy
    participant Bucket as TokenBucket

    Client->>Service: handleRequest("user123")
    Service->>TBS: allowRequest("user123")
    TBS->>TBS: currentTime = System.currentTimeMillis()
    TBS->>Bucket: putIfAbsent("user123", new TokenBucket(cap=5, rate=1, now))
    Note over Bucket: tokens=5, capacity=5, lastRefillTimestamp=now
    TBS->>Bucket: synchronized(bucket)
    TBS->>Bucket: refill(now)
    Note over Bucket: elapsed < 1000ms → tokensToAdd=0 → no change
    TBS->>Bucket: tokens(3) > 0 → tokens-- → tokens=2
    TBS-->>Service: true
    Service-->>Client: "Request from user123 is allowed"
```

---

### Flow 5 — Token Bucket: Request Rejected (Bucket Empty)

```mermaid
sequenceDiagram
    actor Client
    participant Service as RateLimiterService
    participant TBS as TokenBucketStrategy
    participant Bucket as TokenBucket

    Note over Client: Rapid burst — bucket drained to 0
    Client->>Service: handleRequest("user123")
    Service->>TBS: allowRequest("user123")
    TBS->>TBS: currentTime = now
    TBS->>Bucket: get existing TokenBucket
    Note over Bucket: tokens=0, lastRefillTimestamp=T0
    TBS->>Bucket: synchronized(bucket)
    TBS->>Bucket: refill(now)
    Note over Bucket: elapsed < 1000ms → tokensToAdd=0 → no change
    TBS->>Bucket: tokens(0) == 0 → REJECT
    TBS-->>Service: false
    Service-->>Client: "Request from user123 is rejected: Rate limit exceeded"
```

---

### Flow 6 — Token Bucket: Refill Then Allow

```mermaid
sequenceDiagram
    actor Client
    participant Service as RateLimiterService
    participant TBS as TokenBucketStrategy
    participant Bucket as TokenBucket

    Note over Client: Request arrives 3 seconds after last request
    Client->>Service: handleRequest("user123")
    Service->>TBS: allowRequest("user123")
    TBS->>TBS: currentTime = T2
    TBS->>Bucket: get existing TokenBucket
    Note over Bucket: tokens=0, lastRefillTimestamp=T0
    TBS->>Bucket: synchronized(bucket)
    TBS->>Bucket: refill(T2)
    Note over Bucket: elapsed = T2−T0 = 3000ms<br/>tokensToAdd = ⌊3000/1000⌋ × 1 = 3<br/>tokens = min(5, 0+3) = 3<br/>lastRefillTimestamp = T2
    TBS->>Bucket: tokens(3) > 0 → tokens-- → tokens=2
    TBS-->>Service: true
    Service-->>Client: "Request from user123 is allowed"
```

---

## 8. Algorithm Flowcharts

### Fixed Window Counter

```mermaid
flowchart TD
    A([Request arrives for userId]) --> B{userId in\nuserRequestMap?}
    B -- No --> C[Create UserRequestInfo\nwindowStart=now\nrequestCount=0]
    B -- Yes --> D[Get existing UserRequestInfo]
    C --> D
    D --> E[synchronized on requestInfo]
    E --> F{now − windowStart\n≥ windowSizeInMillis?}
    F -- Yes — Window Expired --> G[reset\nwindowStart=now\nrequestCount=0]
    F -- No — Still in Window --> H{requestCount\n< maxRequests?}
    G --> H
    H -- Yes --> I[incrementAndGet\nreturn true ✅ ALLOW]
    H -- No --> J[return false ❌ REJECT]
```

**Boundary burst problem:** A user can make `2 × maxRequests` requests across a window boundary — e.g., 5 at T=9.9s, then 5 again at T=10.1s. Use Sliding Window to fix this.

---

### Token Bucket

```mermaid
flowchart TD
    A([Request arrives for userId]) --> B{userId in\nuserBuckets?}
    B -- No --> C[Create TokenBucket\ntokens=capacity\nlastRefill=now]
    B -- Yes --> D[Get existing TokenBucket]
    C --> D
    D --> E[synchronized on bucket]
    E --> F[refill\nelapsed = now − lastRefill\ntokensToAdd = ⌊elapsed÷1000⌋ × rate]
    F --> G{tokensToAdd > 0?}
    G -- Yes --> H[tokens = min\ncapacity, tokens+tokensToAdd\nlastRefill = now]
    G -- No --> I[no change]
    H --> J{tokens > 0?}
    I --> J
    J -- Yes --> K[tokens−−\nreturn true ✅ ALLOW]
    J -- No --> L[return false ❌ REJECT]
```

**Key properties:**
- Allows bursting up to `capacity` tokens immediately.
- Smooth continuous refill — no hard window boundary.
- Lazy refill: tokens are recalculated only when a request arrives.

---

## 9. Thread Safety Analysis

| Component | Mechanism | Reason |
|---|---|---|
| `RateLimiterService.getInstance()` | `synchronized` method | Prevents two threads from each creating a new instance during startup |
| `FixedWindowStrategy.userRequestMap` | `ConcurrentHashMap` | Lock-free concurrent reads; atomic `putIfAbsent` for new user initialization |
| `UserRequestInfo` operations | `synchronized(requestInfo)` block | Window check + counter increment must be atomic per user; avoids TOCTOU race |
| `UserRequestInfo.requestCount` | `AtomicInteger` | Atomic increment (also guarded by `synchronized` block above — defense in depth) |
| `TokenBucketStrategy.userBuckets` | `ConcurrentHashMap` | Lock-free concurrent reads; atomic `putIfAbsent` for new user initialization |
| `TokenBucket` operations | `synchronized(bucket)` block | Refill + token consume must be atomic; prevents two threads both seeing `tokens > 0` and both consuming |

**Why lock on the object, not the map?**

`synchronized(userRequestMap)` serializes all users — user A's request blocks user B's check. Locking on the per-user object (`synchronized(requestInfo)` / `synchronized(bucket)`) means threads for different users run in parallel while threads for the same user are serialized. This is **fine-grained locking** and eliminates contention between unrelated users at scale.
