# Rate Limiter — LLD Revision Guide

> **Purpose:** Complete revision reference. Read this instead of the entire codebase.
> 2 design patterns · 7 classes · 5 rate-limiting algorithms explained · Thread-safe per-user throttling

---

## Table of Contents

1. [System in One Story](#1-system-in-one-story)
2. [Pattern Map — Memory Hook](#2-pattern-map--memory-hook)
3. [Class Responsibility Cheatsheet](#3-class-responsibility-cheatsheet)
4. [Draw the Class Diagram in 4 Steps](#4-draw-the-class-diagram-in-4-steps)
5. [Entity Relationship Diagram](#5-entity-relationship-diagram)
6. [Class Diagram](#6-class-diagram)
7. [Design Patterns — Deep Dive](#7-design-patterns--deep-dive)
8. [Rate Limiting Algorithms — All 5 Explained](#8-rate-limiting-algorithms--all-5-explained)
9. [Complete Application Flow — End to End](#9-complete-application-flow--end-to-end)
10. [Key Flows — Sequence Diagrams](#10-key-flows--sequence-diagrams)
11. [Thread Safety Analysis](#11-thread-safety-analysis)
12. [Quick Revision Cheatsheet](#12-quick-revision-cheatsheet)

---

## 1. System in One Story

> A `RateLimiterService` (Singleton) is the one shared gate for all incoming requests. It holds a `RateLimitingStrategy` (Strategy) — either a `FixedWindowStrategy` or a `TokenBucketStrategy`. When `handleRequest(userId)` is called, the service delegates entirely to the active strategy's `allowRequest(userId)`. Each strategy maintains a `ConcurrentHashMap` of per-user state: `FixedWindowStrategy` tracks a counter inside a time window; `TokenBucketStrategy` tracks a replenishing pool of tokens. Both use fine-grained locking on the per-user object — not the whole map — so requests for different users proceed in parallel. The `RateLimiterDemo` shows how to swap strategies at runtime without changing any client code.

**Mnemonic — 2 patterns: "S·S"**
- **S**ingleton → `RateLimiterService` — one gate, one active strategy
- **S**trategy → `RateLimitingStrategy` — plug in Fixed Window or Token Bucket without touching the gate

---

## 2. Pattern Map — Memory Hook

| Pattern | Interface / Class | Concrete Classes | One-line Why |
|---|---|---|---|
| **Singleton** | — | `RateLimiterService` | One shared gate; no ambiguity about which instance holds the live strategy |
| **Strategy** | `RateLimitingStrategy` | `FixedWindowStrategy`, `TokenBucketStrategy` | Swap the algorithm at runtime; service never knows which algorithm runs |

---

## 3. Class Responsibility Cheatsheet

> **All 7 classes — one-liner each.**

### Service (1)
| Class | Role |
|---|---|
| `RateLimiterService` | Singleton gate — receives all requests, delegates to current strategy, prints allow/reject |

### Strategy Layer (3)
| Class | Role |
|---|---|
| `RateLimitingStrategy` *(interface)* | Contract: `allowRequest(userId) → boolean` — implemented by every algorithm |
| `FixedWindowStrategy` | Allows up to `maxRequests` per fixed time window; resets counter when window expires |
| `TokenBucketStrategy` | Each user has a token bucket; tokens refill at a fixed rate; one token consumed per request |

### State Holders — Inner Classes (2)
| Class | Role |
|---|---|
| `UserRequestInfo` *(inner of FixedWindowStrategy)* | Holds `windowStart` (epoch ms) + `requestCount` (AtomicInteger) for one user |
| `TokenBucket` *(inner of TokenBucketStrategy)* | Holds `tokens`, `capacity`, `refillRatePerSecond`, `lastRefillTimestamp` for one user |

### Demo (1)
| Class | Role |
|---|---|
| `RateLimiterDemo` | Wires both strategies, spins up thread pools, fires 10 concurrent requests each |

---

## 4. Draw the Class Diagram in 4 Steps

> Follow this order and you'll reconstruct the full diagram without missing anything.

**Step 1 — Draw the interface (your skeleton)**
```
RateLimitingStrategy
  └─ allowRequest(userId: String): boolean
```

**Step 2 — Hang the two strategies off the interface**
```
RateLimitingStrategy ◄── FixedWindowStrategy
                     ◄── TokenBucketStrategy
```

**Step 3 — Add inner state classes and their maps**
```
FixedWindowStrategy
  └─ userRequestMap: ConcurrentHashMap<String, UserRequestInfo>
       └─ UserRequestInfo (inner)
            ├─ windowStart: long
            ├─ requestCount: AtomicInteger
            └─ reset(newStart)

TokenBucketStrategy
  └─ userBuckets: ConcurrentHashMap<String, TokenBucket>
       └─ TokenBucket (inner)
            ├─ tokens: int
            ├─ capacity: int
            ├─ refillRatePerSecond: int
            ├─ lastRefillTimestamp: long
            └─ refill(currentTime)
```

**Step 4 — Wrap in the Singleton service**
```
RateLimiterService (Singleton)
  └─ rateLimitingStrategy: RateLimitingStrategy  [ASSOCIATION — injected via setter]
       └─ delegates allowRequest() call to it

RateLimiterDemo
  └─ creates FixedWindowStrategy → injects into RateLimiterService
  └─ creates TokenBucketStrategy → injects into RateLimiterService
```

> **Tip:** The strategies are *associated* with the service (passed in via `setRateLimitingStrategy()`), not *composed* — the service does not create or own the strategy objects.

---

## 5. Entity Relationship Diagram

```mermaid
erDiagram
    USER {
        string userId PK "Caller-provided identifier"
    }

    RATE_LIMITER_SERVICE {
        string activeStrategy "Fixed Window or Token Bucket"
    }

    USER_REQUEST_INFO {
        long windowStart "Epoch ms when current window started"
        int requestCount "Requests made so far in current window"
    }

    TOKEN_BUCKET {
        int tokens "Current available tokens in range 0..capacity"
        int capacity "Max tokens — controls burst ceiling"
        int refillRatePerSecond "Tokens added back per second"
        long lastRefillTimestamp "Epoch ms of last refill calculation"
    }

    FIXED_WINDOW_STRATEGY {
        int maxRequests "Max allowed requests per window"
        long windowSizeInMs "Window duration converted to milliseconds"
    }

    TOKEN_BUCKET_STRATEGY {
        int capacity "Bucket capacity shared as default for all users"
        int refillRatePerSecond "Refill rate shared as default for all users"
    }

    RATE_LIMITER_SERVICE ||--|| FIXED_WINDOW_STRATEGY : "delegates to (active strategy)"
    RATE_LIMITER_SERVICE ||--|| TOKEN_BUCKET_STRATEGY : "delegates to (active strategy)"
    USER ||--o| USER_REQUEST_INFO : "has one state per strategy"
    USER ||--o| TOKEN_BUCKET : "has one bucket per strategy"
    FIXED_WINDOW_STRATEGY ||--o{ USER_REQUEST_INFO : "manages one entry per user"
    TOKEN_BUCKET_STRATEGY ||--o{ TOKEN_BUCKET : "manages one bucket per user"
```

---

## 6. Class Diagram

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
        <<inner class>>
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
        <<inner class>>
        +int tokens
        +int capacity
        +int refillRatePerSecond
        +long lastRefillTimestamp
        +TokenBucket(int capacity, int refillRatePerSecond, long now)
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
    FixedWindowStrategy *-- UserRequestInfo : inner class
    TokenBucketStrategy *-- TokenBucket : inner class
    RateLimiterDemo ..> RateLimiterService : uses singleton
    RateLimiterDemo ..> FixedWindowStrategy : creates and injects
    RateLimiterDemo ..> TokenBucketStrategy : creates and injects
```

---

## 7. Design Patterns — Deep Dive

---

### 7.1 Singleton Pattern — `RateLimiterService`

**What it does:**
A single `RateLimiterService` instance is shared across all callers. It holds the currently active `RateLimitingStrategy` and is the only entry point for all `handleRequest()` calls.

**Key implementation — Lazy synchronized init:**
```java
private static RateLimiterService instance;          // not volatile — simpler impl

public static synchronized RateLimiterService getInstance() {
    if (instance == null) {
        instance = new RateLimiterService();         // only one thread creates it
    }
    return instance;
}
```

**vs. Double-Checked Locking (DCL):**
The implemented version uses a fully `synchronized` method — simpler, correct, but every `getInstance()` call acquires the lock even after init. DCL with `volatile` avoids the lock on the fast path. For a rate limiter where `getInstance()` is called once in setup, the simpler approach is fine.

**Without Singleton:** Every caller creates their own `RateLimiterService` with its own strategy — two callers could hold different strategies, creating split-brain rate limiting. No single authority over request decisions.

**Strategy injection:**
```java
service.setRateLimitingStrategy(new FixedWindowStrategy(5, 10));
// Strategy is swapped here — next handleRequest() uses the new algorithm
service.setRateLimitingStrategy(new TokenBucketStrategy(5, 1));
```

---

### 7.2 Strategy Pattern — `RateLimitingStrategy`

**What it does:**
The rate-limiting algorithm is encapsulated behind a single interface. `RateLimiterService` delegates the allow/reject decision entirely to whatever strategy is currently injected — it never inspects or knows the algorithm's internals.

**The interface contract:**
```java
public interface RateLimitingStrategy {
    boolean allowRequest(String userId);
}
```

**How delegation works — zero algorithm logic in the service:**
```java
public void handleRequest(String userId) {
    if (rateLimitingStrategy.allowRequest(userId)) {   // ← Strategy decides everything
        System.out.println("Request from user " + userId + " is allowed");
    } else {
        System.out.println("Request from user " + userId + " is rejected: Rate limit exceeded");
    }
}
```

**Runtime swap — the key benefit:**
```java
// Phase 1: Fixed Window
service.setRateLimitingStrategy(new FixedWindowStrategy(5, 10));
runDemo(service);

// Phase 2: Token Bucket — zero changes to service or caller
service.setRateLimitingStrategy(new TokenBucketStrategy(5, 1));
runDemo(service);
```

**Extensibility — adding Sliding Window:**
```java
public class SlidingWindowLogStrategy implements RateLimitingStrategy {
    @Override
    public boolean allowRequest(String userId) { ... }
}
// Zero changes to RateLimiterService, RateLimiterDemo, or any existing strategy
service.setRateLimitingStrategy(new SlidingWindowLogStrategy(5, 10));
```

**Without Strategy:** All algorithm logic lives inside `handleRequest()` as a giant if/else block. Adding Token Bucket means editing the service class, re-testing all paths, and breaking the Open/Closed Principle.

---

## 8. Rate Limiting Algorithms — All 5 Explained

> The codebase implements **Fixed Window** and **Token Bucket**. All 5 algorithms are explained here for interview completeness.

---

### 8.1 Fixed Window Counter ✅ Implemented

**Concept:** Divide time into fixed-size windows. Keep a counter per user per window. Reject when the counter hits the limit. Reset the counter when the window expires.

```mermaid
flowchart TD
    A([Request arrives for userId]) --> B{userId in map?}
    B -- No --> C[Create UserRequestInfo\nwindowStart=now\nrequestCount=0]
    B -- Yes --> D[Get UserRequestInfo]
    C --> D
    D --> E[synchronized on requestInfo]
    E --> F{now − windowStart\n≥ windowSizeInMs?}
    F -- Yes\nWindow Expired --> G[reset\nwindowStart=now\nrequestCount=0]
    F -- No\nStill in Window --> H{requestCount\n< maxRequests?}
    G --> H
    H -- Yes --> I[incrementAndGet → return true\n✅ ALLOW]
    H -- No --> J[return false\n❌ REJECT]
```

**Timeline example** — maxRequests=5, window=10s:
```
│◄─── Window 1 ───►│◄─── Window 2 ───►│
T=0               T=10              T=20
●  ●  ●  ●  ●  ✗  ✗     ●  ●  ●
1  2  3  4  5  6  7     1  2  3   ← counter resets at T=10
```

**Boundary burst problem — the critical flaw:**
```
T=9.9s  → 5 requests  (end of Window 1)  ✅ all allowed
T=10.1s → 5 requests  (start of Window 2) ✅ all allowed
= 10 requests in 0.2 seconds — 2× the intended limit
```

**State per user:**

| Field | Type | Purpose |
|---|---|---|
| `windowStart` | `long` | Epoch ms when current window started |
| `requestCount` | `AtomicInteger` | Requests made so far in this window |

**Pros:** O(1) time, O(1) space per user. Simple to implement.
**Cons:** Boundary burst allows 2× traffic spike at window edges.

---

### 8.2 Token Bucket ✅ Implemented

**Concept:** Each user has a bucket with a maximum capacity. Tokens refill at a constant rate (up to capacity). Each request consumes one token. If the bucket is empty, the request is rejected.

```mermaid
flowchart TD
    A([Request arrives for userId]) --> B{userId in map?}
    B -- No --> C[Create TokenBucket\ntokens=capacity\nlastRefill=now]
    B -- Yes --> D[Get TokenBucket]
    C --> D
    D --> E[synchronized on bucket]
    E --> F[refill\nelapsed = now − lastRefill\ntokensToAdd = ⌊elapsed÷1000⌋ × rate]
    F --> G{tokensToAdd > 0?}
    G -- Yes --> H[tokens = min\ncapacity, tokens+add\nlastRefill = now]
    G -- No --> I[no change to tokens]
    H --> J{tokens > 0?}
    I --> J
    J -- Yes --> K[tokens−− → return true\n✅ ALLOW]
    J -- No --> L[return false\n❌ REJECT]
```

**Refill formula:**
```
elapsed      = currentTime − lastRefillTimestamp        (ms)
tokensToAdd  = ⌊elapsed / 1000⌋ × refillRatePerSecond
newTokens    = min(capacity, currentTokens + tokensToAdd)
```

**Timeline example** — capacity=5, refillRate=1/s, requests every 300ms:
```
T=0.0s  tokens=5 → req ● → tokens=4
T=0.3s  tokens=4 → req ● → tokens=3
T=0.6s  tokens=3 → req ● → tokens=2
T=0.9s  tokens=2 → req ● → tokens=1
T=1.2s  tokens=1 → req ● → tokens=0  (1 token refilled since T=0)
T=1.5s  tokens=0 → req ✗  (elapsed=300ms, no new token yet)
T=2.5s  tokens=1 → req ● → tokens=0  (1 more token since last refill)
```

**State per user:**

| Field | Type | Purpose |
|---|---|---|
| `tokens` | `int` | Current available tokens |
| `capacity` | `int` | Maximum tokens — controls burst ceiling |
| `refillRatePerSecond` | `int` | Tokens added per second |
| `lastRefillTimestamp` | `long` | When refill was last calculated |

**Key insight — lazy refill:** Tokens are not added on a background timer. They are calculated *on-demand* when a request arrives, using elapsed time since the last calculation.

**Pros:** Handles bursts up to `capacity`. Smooth continuous refill — no hard window resets. O(1) per request.
**Cons:** Allows burst of `capacity` tokens at any point — could still spike traffic.

---

### 8.3 Sliding Window Log ❌ Not Implemented

**Concept:** Maintain a log (sorted list) of request timestamps per user. On each request, remove all timestamps older than the window, then check if the log size is within the limit.

```mermaid
flowchart TD
    A([Request arrives for userId]) --> B[Get request log for userId]
    B --> C[Remove all timestamps\nolder than now − windowSize]
    C --> D{logSize < maxRequests?}
    D -- Yes --> E[Add current timestamp to log\nreturn true ✅ ALLOW]
    D -- No --> F[return false ❌ REJECT]
```

**Timeline example** — maxRequests=3, window=10s:
```
T=1s   log=[1]         → size=1 < 3 ✅
T=5s   log=[1,5]       → size=2 < 3 ✅
T=9s   log=[1,5,9]     → size=3 == 3 ✅
T=11s  log=[5,9,11]    → evict T=1 (11-1=10≥10), size=3 ✅
T=12s  log=[5,9,11,12] → evict T=5 would not work (12-5=7<10), size=4 ❌ REJECT
```

**Fixes the boundary burst problem** — the window slides with every request. No hard reset.

**State per user:** A sorted set of timestamps (e.g., `TreeMap<Long, Integer>` for counts, or a `LinkedList<Long>`).

**Pros:** Perfectly accurate. No boundary burst.
**Cons:** Memory grows with request volume — O(maxRequests) per user. Timestamp eviction is O(log n).

---

### 8.4 Sliding Window Counter ❌ Not Implemented

**Concept:** Hybrid of Fixed Window + Sliding Window Log. Store counters for the current and previous fixed windows. Estimate the effective request count using a weighted average based on how far into the current window you are.

```mermaid
flowchart TD
    A([Request arrives for userId]) --> B[Get current & previous window counts]
    B --> C[ratio = elapsed in current window ÷ windowSize]
    C --> D[estimate = prevCount × 1−ratio + currCount]
    D --> E{estimate < maxRequests?}
    E -- Yes --> F[increment current window count\nreturn true ✅ ALLOW]
    E -- No --> G[return false ❌ REJECT]
```

**Formula:**
```
ratio        = (now − currentWindowStart) / windowSizeMs
weightedCount = previousWindowCount × (1 − ratio) + currentWindowCount
```

**Example** — maxRequests=10, window=60s, we are 25% into the current window:
```
previousWindowCount = 8
currentWindowCount  = 3
ratio               = 0.25
estimate            = 8 × (1 − 0.25) + 3 = 6 + 3 = 9 < 10 → ALLOW
```

**Pros:** O(1) space per user (only two counters). Approximates Sliding Window Log with 0.003% error rate (per Cloudflare research). No boundary burst in practice.
**Cons:** Approximate — theoretically allows slight overcounting at window boundaries.

---

### 8.5 Leaky Bucket ❌ Not Implemented

**Concept:** Requests enter a queue (the "bucket"). A fixed-rate processor drains the queue one request at a time. If the queue is full, incoming requests overflow and are rejected.

```mermaid
flowchart TD
    A([Request arrives]) --> B{queue.size\n< capacity?}
    B -- Yes --> C[Enqueue request\n✅ ACCEPT into queue]
    B -- No --> D[❌ REJECT — bucket full]
    E([Background processor\nfixed rate]) --> F{queue empty?}
    F -- No --> G[Dequeue and process\none request]
    F -- Yes --> H[Wait for next tick]
    G --> E
    H --> E
```

**Comparison with Token Bucket:**

| Aspect | Token Bucket | Leaky Bucket |
|---|---|---|
| Burst handling | Allows burst up to `capacity` | Smooths all bursts — constant output rate |
| Output rate | Variable (consumes on demand) | Constant (drains at fixed rate) |
| Implementation | Counter + timestamp (stateless drain) | Queue + background thread |
| Use case | API rate limiting with burst tolerance | Network traffic shaping |

**Pros:** Guarantees a constant output rate — ideal for smoothing traffic to downstream systems.
**Cons:** Requires a background processing thread. Queue adds latency. Legitimate burst traffic is still delayed even if capacity allows.

---

### Algorithm Comparison Table

| Algorithm | Memory per User | Accuracy | Burst Handling | Boundary Burst | Complexity |
|---|---|---|---|---|---|
| Fixed Window | O(1) | Approximate | ❌ No (hard reset) | ✅ Yes — up to 2× | O(1) |
| Token Bucket | O(1) | Approximate | ✅ Yes (up to capacity) | ❌ No hard boundary | O(1) |
| Sliding Window Log | O(maxRequests) | Exact | ❌ No | ❌ No | O(log n) |
| Sliding Window Counter | O(1) | ~99.997% | ❌ No | Minimal | O(1) |
| Leaky Bucket | O(capacity) | Exact | Delays (smooths) | ❌ No | O(1) drain |

**Interview answer — which to use?**
- **API rate limiting** (most common): Token Bucket or Sliding Window Counter
- **Traffic shaping** (downstream protection): Leaky Bucket
- **Simple quota enforcement** (daily/hourly limits): Fixed Window is fine
- **Strict accuracy required**: Sliding Window Log

---

## 9. Complete Application Flow — End to End

> The full `RateLimiterDemo.main()` call chain — both phases, all threads, in order.

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

    Demo->>FWS: new FixedWindowStrategy(maxRequests=5, windowSeconds=10)
    Note over FWS: windowSizeInMillis = 10 × 1000 = 10000ms

    Demo->>Service: getInstance()
    Note over Service: synchronized — lazy init<br/>instance == null → new RateLimiterService()
    Service-->>Demo: singleton instance

    Demo->>Service: setRateLimitingStrategy(fixedWindowStrategy)
    Demo->>Pool: Executors.newFixedThreadPool(3)

    loop i = 0 to 9 — 10 requests, 500ms apart
        Demo->>Pool: submit(→ service.handleRequest("user123"))
        Pool->>Service: handleRequest("user123")
        Service->>FWS: allowRequest("user123")
        FWS->>FWS: currentTime = System.currentTimeMillis()
        FWS->>Info: putIfAbsent("user123", new UserRequestInfo(currentTime))
        FWS->>Info: synchronized(requestInfo)
        alt Window not expired AND requestCount < 5
            FWS->>Info: requestCount.incrementAndGet()
            FWS-->>Service: true
            Service-->>Pool: "Request from user123 is allowed"
        else requestCount >= 5 (requests 6–10 at 500ms intervals, still in 10s window)
            FWS-->>Service: false
            Service-->>Pool: "Request from user123 is rejected: Rate limit exceeded"
        end
        Note over Demo: Thread.sleep(500ms)
    end

    Demo->>Pool: executor.shutdown()

    Note over Demo,Pool: ── PHASE 2: Token Bucket Demo (capacity=5, refillRate=1/s) ──

    Demo->>TBS: new TokenBucketStrategy(capacity=5, refillRatePerSecond=1)
    Demo->>Service: setRateLimitingStrategy(tokenBucketStrategy)
    Note over Service: strategy swapped — FWS discarded, TBS active
    Demo->>Pool: Executors.newFixedThreadPool(2)

    loop i = 0 to 9 — 10 requests, 300ms apart
        Demo->>Pool: submit(→ service.handleRequest("user123"))
        Pool->>Service: handleRequest("user123")
        Service->>TBS: allowRequest("user123")
        TBS->>TBS: currentTime = System.currentTimeMillis()
        TBS->>Bucket: putIfAbsent("user123", new TokenBucket(5, 1, currentTime))
        TBS->>Bucket: synchronized(bucket) → refill(currentTime)
        Note over Bucket: elapsed = now − lastRefill<br/>tokensToAdd = ⌊elapsed÷1000⌋ × 1
        alt tokens > 0
            TBS->>Bucket: tokens--
            TBS-->>Service: true
            Service-->>Pool: "Request from user123 is allowed"
        else tokens == 0 (requests faster than 1/s refill at 300ms gaps)
            TBS-->>Service: false
            Service-->>Pool: "Request from user123 is rejected: Rate limit exceeded"
        end
        Note over Demo: Thread.sleep(300ms) — faster than 1s refill rate
    end

    Demo->>Pool: executor.shutdown()
```

---

## 10. Key Flows — Sequence Diagrams

### Flow 1 — Fixed Window: Request Allowed (Window Active, Below Limit)

```mermaid
sequenceDiagram
    actor Client
    participant Service as RateLimiterService
    participant FWS as FixedWindowStrategy
    participant Info as UserRequestInfo

    Client->>Service: handleRequest("user123")
    Service->>FWS: allowRequest("user123")
    FWS->>FWS: currentTime = System.currentTimeMillis()
    FWS->>Info: putIfAbsent → get UserRequestInfo
    Note over Info: windowStart=T0, requestCount=2
    FWS->>Info: synchronized(requestInfo)
    Note over FWS,Info: (now − T0) < windowSizeInMs → still in window
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
    Note over FWS,Info: (now − T0) < windowSizeInMs → still in window
    Note over FWS,Info: requestCount(5) >= maxRequests(5) → REJECT
    FWS-->>Service: false
    Service-->>Client: "Request from user123 is rejected: Rate limit exceeded"
```

---

### Flow 3 — Fixed Window: Window Expired → Reset → Allow

```mermaid
sequenceDiagram
    actor Client
    participant Service as RateLimiterService
    participant FWS as FixedWindowStrategy
    participant Info as UserRequestInfo

    Note over Client: Request arrives after window expiry (T2 > T0 + windowSize)
    Client->>Service: handleRequest("user123")
    Service->>FWS: allowRequest("user123")
    FWS->>FWS: currentTime = T2
    FWS->>Info: get existing UserRequestInfo
    Note over Info: windowStart=T0, requestCount=5
    FWS->>Info: synchronized(requestInfo)
    Note over FWS,Info: (T2 − T0) >= windowSizeInMs → WINDOW EXPIRED
    FWS->>Info: reset(T2) — windowStart=T2, requestCount=0
    Note over Info: Fresh window started at T2
    FWS->>Info: requestCount(0) < maxRequests(5) → incrementAndGet() → 1
    FWS-->>Service: true
    Service-->>Client: "Request from user123 is allowed"
```

---

### Flow 4 — Token Bucket: First Request (Bucket Created, Token Consumed)

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
    Note over Bucket: tokens=5, capacity=5, refillRate=1, lastRefill=now
    TBS->>Bucket: synchronized(bucket) → refill(now)
    Note over Bucket: elapsed=0ms → tokensToAdd=0 → no change
    Note over TBS,Bucket: tokens(5) > 0 → tokens-- → tokens=4
    TBS-->>Service: true
    Service-->>Client: "Request from user123 is allowed"
```

---

### Flow 5 — Token Bucket: Bucket Drained → Request Rejected

```mermaid
sequenceDiagram
    actor Client
    participant Service as RateLimiterService
    participant TBS as TokenBucketStrategy
    participant Bucket as TokenBucket

    Note over Client: Rapid burst — 5 prior requests already drained tokens to 0
    Client->>Service: handleRequest("user123")
    Service->>TBS: allowRequest("user123")
    TBS->>TBS: currentTime = now
    TBS->>Bucket: get existing TokenBucket
    Note over Bucket: tokens=0, lastRefill=T0
    TBS->>Bucket: synchronized(bucket) → refill(now)
    Note over Bucket: elapsed < 1000ms → tokensToAdd = ⌊elapsed÷1000⌋ × 1 = 0
    Note over TBS,Bucket: tokens(0) == 0 → REJECT
    TBS-->>Service: false
    Service-->>Client: "Request from user123 is rejected: Rate limit exceeded"
```

---

### Flow 6 — Token Bucket: Time Passes → Refill → Allow

```mermaid
sequenceDiagram
    actor Client
    participant Service as RateLimiterService
    participant TBS as TokenBucketStrategy
    participant Bucket as TokenBucket

    Note over Client: 3 seconds have elapsed since bucket was drained
    Client->>Service: handleRequest("user123")
    Service->>TBS: allowRequest("user123")
    TBS->>TBS: currentTime = T2
    TBS->>Bucket: get existing TokenBucket
    Note over Bucket: tokens=0, lastRefill=T0
    TBS->>Bucket: synchronized(bucket) → refill(T2)
    Note over Bucket: elapsed = T2 − T0 = 3000ms<br/>tokensToAdd = ⌊3000÷1000⌋ × 1 = 3<br/>tokens = min(5, 0+3) = 3<br/>lastRefill = T2
    Note over TBS,Bucket: tokens(3) > 0 → tokens-- → tokens=2
    TBS-->>Service: true
    Service-->>Client: "Request from user123 is allowed"
```

---

## 11. Thread Safety Analysis

### What's Thread-Safe and Why

| Component | Mechanism | Why This Mechanism |
|---|---|---|
| `RateLimiterService.getInstance()` | `synchronized` method | Prevents two threads from each creating a new instance on startup |
| `FixedWindowStrategy.userRequestMap` | `ConcurrentHashMap` | Lock-free concurrent reads; atomic `putIfAbsent` prevents duplicate user-init |
| `UserRequestInfo` window check + increment | `synchronized(requestInfo)` block | Window expiry check and counter increment must be one atomic operation — avoids TOCTOU race where two threads both see count=4 and both increment to 5 |
| `UserRequestInfo.requestCount` | `AtomicInteger` | Atomic increment without needing an extra lock (also guarded by `synchronized` above) |
| `TokenBucketStrategy.userBuckets` | `ConcurrentHashMap` | Lock-free concurrent reads; atomic `putIfAbsent` for new user bucket creation |
| `TokenBucket` refill + consume | `synchronized(bucket)` block | Refill calculation and token decrement must be one atomic operation — prevents two threads both seeing `tokens=1` and both consuming it |

### Why Lock on the User Object, Not the Map?

```
❌ synchronized(userRequestMap)   → serializes ALL users
                                    user-A's request blocks user-B's check

✅ synchronized(requestInfo)      → serializes only requests for the SAME user
                                    user-A and user-B proceed in parallel
```

This is **fine-grained locking**. Locking on the per-user object means contention only occurs when the same user fires concurrent requests — the common case of many users is fully parallel.

### The TOCTOU Race — Why Both Lines Must Be Inside `synchronized`

```java
// ❌ WRONG — race condition between check and increment
if (requestInfo.requestCount.get() < maxRequests) {    // Thread A reads count=4
    // Thread B also reads count=4 here — both pass the check!
    requestInfo.requestCount.incrementAndGet();         // Both increment → count becomes 5 then 6
}

// ✅ CORRECT — check and increment are atomic together
synchronized (requestInfo) {
    if (requestInfo.requestCount.get() < maxRequests) {
        requestInfo.requestCount.incrementAndGet();    // Only one thread reaches here at a time
        return true;
    }
    return false;
}
```

---

## 12. Quick Revision Cheatsheet

### Both Patterns at a Glance

| Pattern | Class | Without It |
|---|---|---|
| **Singleton** | `RateLimiterService` | Multiple instances → split-brain rate limiting → no consistent policy enforcement |
| **Strategy** | `RateLimitingStrategy` | Algorithm baked into service → changing algorithm requires editing and re-testing the service class |

### Inner Classes — Why Inner, Not Top-Level?

`UserRequestInfo` and `TokenBucket` are private inner classes because:
1. They are implementation details of their enclosing strategy — no other class should touch them.
2. Being `private static` inner classes means they don't hold a reference to the outer class instance — no memory leak.
3. They model state that is meaningless outside the strategy that created them.

### Algorithm Selection — The One-Liner Summary

```
Fixed Window   → simple quota; beware 2× burst at window boundary
Token Bucket   → smooth refill with burst tolerance; best for API rate limiting
Sliding Log    → exact accuracy; memory cost O(maxRequests) per user
Sliding Counter→ best accuracy/cost tradeoff; Cloudflare's choice
Leaky Bucket   → constant output rate; best for downstream traffic shaping
```

### Request Lifecycle — State Diagram

```mermaid
stateDiagram-v2
    [*] --> Received : handleRequest(userId)
    Received --> Delegated : service.allowRequest(userId)
    Delegated --> Allowed : strategy returns true
    Delegated --> Rejected : strategy returns false
    Allowed --> [*] : print allowed
    Rejected --> [*] : print rejected

    Allowed : tokens-- or requestCount++
    Rejected : no state change
```

### How to Answer "How Does the Rate Limiter Work?" in 30 Seconds

```
RateLimiterService (Singleton) ← single gate for all requests
  │
  └─ delegates to RateLimitingStrategy (Strategy)
       │
       ├─ FixedWindowStrategy
       │    └─ ConcurrentHashMap<userId, UserRequestInfo>
       │         └─ synchronized(info): check window, increment count
       │
       └─ TokenBucketStrategy
            └─ ConcurrentHashMap<userId, TokenBucket>
                 └─ synchronized(bucket): refill on arrival, consume token
```

### Extensibility — Where to Add New Stuff

| Feature | Add This | Touch Nothing Else |
|---|---|---|
| New algorithm (Sliding Window) | New class implementing `RateLimitingStrategy` | `RateLimiterService` untouched — just inject the new class |
| Per-endpoint limits | Pass endpoint ID alongside `userId` as composite key | Strategy internals only |
| Distributed rate limiting (Redis-backed) | New `RedisRateLimitingStrategy` implementing the interface | All other classes untouched |
| Rate limit headers in response | Return a result object instead of `boolean` from `allowRequest()` | Requires interface change — plan for it upfront |
