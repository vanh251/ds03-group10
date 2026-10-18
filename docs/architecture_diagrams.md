# Ride-Hailing Microservices Architecture Diagrams

As a Software Architect, these UML diagrams strictly adhere to the Database-per-Service pattern, Saga Orchestration, and Eventual Consistency principles.

> **Visual Conventions (Strict)**
> - **Solid lines (`-->` / `->>`)** = **Synchronous REST/HTTP** — tight coupling, caller blocks and waits; requires timeout & circuit breaker.
> - **Dashed lines (`-.->` / `-->>`)** = **Asynchronous Events (Apache Kafka)** — loose coupling, publisher fires and forgets.
> - **Arrow direction** = direction of **dependency** (Caller → Callee), NOT data flow direction.
> - Arrows **never** cross into another service's database directly.

All cross-service relationships use UUID soft references to prevent tight database coupling.

---

## 1. Use Case Diagram (System Interactions)

This diagram maps out the primary functional capabilities of the Passenger and Driver actors against the 7 core microservices.

Conventions: Actor → UseCase uses solid lines (direct interaction). Cross-service dependencies: solid lines = Sync REST, dashed lines = Async Event (Kafka). **Numbered steps ①–⑫** trace the full trip lifecycle order.

```mermaid
flowchart LR
    %% Actors
    Passenger([🧍 Passenger])
    Driver([🚗 Driver])

    %% Services as subsystems
    subgraph IS["Identity Service (:8081)"]
        UC1(Register / Login / RBAC)
    end

    subgraph TS["Trip Service (:8083)\n(Orchestrator)"]
        UC2(Request Ride)
        UC6(Manage Trip Lifecycle)
    end

    subgraph DS["Driver/Location Service (:8082)"]
        UC3(Update Real-time Location)
        UC3b(GET Nearby Drivers)
        UC3c(CAS Reserve / Release Driver)
    end

    subgraph MS["Matching Service (:8084)"]
        UC4(Find & Match Driver)
    end

    subgraph PS["Pricing Service (:8085)"]
        UC5(Calculate Dynamic Fare Quote)
    end

    subgraph PAY["Payment Service (:8086)"]
        UC7(Authorize / Process Payment)
    end

    subgraph NS["Notification Service (:8087)"]
        UC8(Receive WebSocket Alerts)
    end

    %% ── Actor → Service (direct interactions, no step numbers — always-on) ──
    Passenger -->|"⓪ Auth"| UC1
    Driver -->|"⓪ Auth"| UC1
    Driver -->|"Heartbeat / Status"| UC3

    %% ── Step 1: Passenger requests a ride ──
    Passenger -->|"① Request Ride"| UC2

    %% ── Step 2: Trip calls Pricing for quote (Sync REST) ──
    UC2 -->|"② Sync REST: POST /api/pricing/quote"| UC5

    %% ── Step 3: Trip publishes event → Matching (Async Kafka) ──
    UC2 -.->|"③ Event: trip.requested"| UC4

    %% ── Step 4: Matching queries nearby drivers (Sync REST) ──
    UC4 -->|"④ Sync REST: GET /api/drivers/available"| UC3b

    %% ── Step 5: Matching locks driver via CAS (Sync REST) ──
    UC4 -->|"⑤ Sync REST: POST /api/drivers/{id}/reserve"| UC3c

    %% ── Step 6: driver.reserved → Orchestrator updates state (Async Kafka) ──
    UC4 -.->|"⑥ Event: driver.reserved"| UC6

    %% ── Step 7: Orchestrator notifies parties via WebSocket (Async Kafka) ──
    UC6 -.->|"⑦ Event: driver.assigned"| UC8
    UC8 -->|"⑦ WebSocket push"| Passenger
    UC8 -->|"⑦ WebSocket push"| Driver

    %% ── Step 8: Driver completes ride → Orchestrator triggers payment ──
    Driver -->|"⑧ Complete Ride"| UC6
    UC6 -.->|"⑧ Event: trip.completed"| UC7

    %% ── Step 9: Payment result flows back to Orchestrator (Async Kafka) ──
    UC7 -.->|"⑨ Event: payment.success / failed"| UC6

    %% ── Step 10a (Cancellation/Compensation path): Release driver lock (Sync REST) ──
    UC2 -->|"⑩a Sync REST: DELETE /api/drivers/{id}/reserve\n(Cancel before pickup)"| UC3c

    %% ── Step 11: Orchestrator publishes trip outcome (Async Kafka) ──
    UC6 -.->|"⑪ Event: trip.paid / trip.cancelled / payment.failed"| UC8

    %% ── Step 12: Final WebSocket push to both parties ──
    UC8 -->|"⑫ WebSocket push"| Passenger
    UC8 -->|"⑫ WebSocket push"| Driver
```

---

## 2. Component Diagram (Service Dependencies & Trip Lifecycle Order)

This diagram shows all 7 microservices and their communication channels with API Gateway and Apache Kafka. **Read the line style** to understand coupling risk. **Numbered steps ①–⑫** trace the full lifecycle of one ride booking.

> Legend: **Solid line** = Sync REST (blocking, needs circuit breaker) | **Dashed line** = Async Event via Apache Kafka (fire-and-forget)

```mermaid
flowchart LR
    GW["🔀 API Gateway\n(:8080)"]

    subgraph CORE["Core Business Services (Spring Boot 3.x)"]
        IDENTITY["Identity Service\n(:8081 | db_identity)"]
        TRIP["Trip Service\n(:8083 | db_trip)\n⭐ Saga Orchestrator"]
        DRIVER["Driver/Location Service\n(:8082 | db_driver)\nRedis GEO"]
        MATCH["Matching Service\n(:8084 | db_matching)"]
        PRICE["Pricing Service\n(:8085 | db_pricing)"]
        PAY["Payment Service\n(:8086 | db_payment)"]
        NOTIF["Notification Service\n(:8087 | db_notification)"]
    end

    KF[("⚡ Apache Kafka\n(kafka:9092)")]

    %% ── Step 0: Gateway validates JWT before forwarding ──
    GW -->|"⓪ Sync REST: JWT auth"| IDENTITY
    GW -->|"① Sync REST: POST /api/trips"| TRIP
    GW -->|"Sync REST: PATCH /api/drivers/{id}/status"| DRIVER

    %% ── Step 2: Trip calls Pricing for fare quote (Sync REST) ──
    TRIP -->|"② Sync REST: POST /api/pricing/quote\n⚠️ If Pricing dies, Trip fails"| PRICE

    %% ── Step 3: Trip publishes event → Matching picks up ──
    TRIP -.->|"③ Event: trip.requested\n(via Outbox → Kafka)"| KF
    KF -.->|"③ Event: trip.requested"| MATCH

    %% ── Step 4: Matching queries nearby drivers (Sync REST) ──
    MATCH -->|"④ Sync REST: GET /api/drivers/available\n⚠️ If Driver dies, Match fails"| DRIVER

    %% ── Step 5: Matching locks driver via CAS (Sync REST) ──
    MATCH -->|"⑤ Sync REST: POST /api/drivers/{id}/reserve (CAS Lock)\n⚠️ If Driver dies, Match fails"| DRIVER

    %% ── Step 6: Matching publishes result → Orchestrator updates state ──
    MATCH -.->|"⑥ Event: driver.reserved"| KF
    KF -.->|"⑥ Event: driver.reserved"| TRIP

    %% ── Step 7: Orchestrator notifies parties ──
    TRIP -.->|"⑦ Event: driver.assigned"| KF
    KF -.->|"⑦ Event: driver.assigned"| NOTIF

    %% ── Step 8: Trip completed → trigger payment ──
    TRIP -.->|"⑧ Event: trip.completed"| KF
    KF -.->|"⑧ Event: trip.completed"| PAY

    %% ── Step 9 (Happy): Payment success → Orchestrator closes saga ──
    PAY -.->|"⑨ Event: payment.success"| KF
    KF -.->|"⑨ Event: payment.success"| TRIP

    %% ── Step 9 (Failure): Payment failed → Saga compensation / debt record ──
    PAY -.->|"⑨ Event: payment.failed"| KF
    KF -.->|"⑨ Event: payment.failed"| TRIP

    %% ── Step 10 (Compensation during matching/booking): Release driver lock ──
    TRIP -->|"⑩ Sync REST: DELETE /api/drivers/{id}/reserve\n(Compensation if cancelled before start)"| DRIVER

    %% ── Step 11: Orchestrator publishes outcome event ──
    TRIP -.->|"⑪ Event: trip.paid / trip.cancelled / payment.failed"| KF
    KF -.->|"⑪ Event: trip.paid / trip.cancelled / payment.failed"| NOTIF

    %% ── Step 12: Notify both parties via WebSocket ──
    NOTIF -.->|"⑫ WebSocket push\nto Passenger & Driver"| GW
```

---

## 3. Sequence Diagram: Trip Lifecycle & Saga Orchestration

The **Trip Service** acts as the central **Saga Orchestrator**. It maintains global transaction state inside `db_trip` (via `saga_log` table).

- **`->>`** (solid arrow) = **Synchronous REST call** — caller blocks, strict timeout & circuit breaker required.
- **`-->>`** (dashed arrow) = **Asynchronous Event via Apache Kafka** — fire and forget; Transactional Outbox Pattern guarantees at-least-once delivery.

```mermaid
sequenceDiagram
    autonumber
    actor P as 🧍 Passenger
    participant GW as API Gateway (:8080)
    participant Trip as Trip Service (:8083)<br/>(Saga Orchestrator)
    participant Pricing as Pricing Service (:8085)
    participant KF as ⚡ Apache Kafka (9092)
    participant Match as Matching Service (:8084)
    participant Loc as Driver/Location (:8082)
    participant Pay as Payment Service (:8086)
    participant Notif as Notification Service (:8087)

    rect rgb(220, 245, 220)
    Note over P, Notif: 🟢 HAPPY PATH: Ride Request → Driver Match → Completion → Payment Success

    P->>GW: POST /api/trips {pickup, dropoff, passengerId}
    GW->>Trip: POST /api/trips (JWT validated, correlationId injected)
    activate Trip

    Note right of Trip: ⚠️ Sync REST: If Pricing dies, Trip request fails
    Trip->>Pricing: POST /api/pricing/quote {pickup, dropoff, distance} (Sync REST)
    Pricing-->>Trip: 200 OK {estimatedFare: 50000, currency: VND, fareRuleId}

    Trip->>Trip: ACID TX: Save Trip(REQUESTED)<br/>+ Insert saga_log(STARTED)<br/>+ Insert outbox_events(trip.requested)
    Trip-->>GW: 201 CREATED {tripId, status: REQUESTED, estimatedFare}
    deactivate Trip
    GW-->>P: 201 CREATED {tripId, status: REQUESTED, estimatedFare}

    Note over KF: Outbox Relay Worker publishes event to Kafka
    Trip-->>(KF): Publish [trip.requested] {tripId, passengerId, correlationId, pickupLat, pickupLng}
    end

    rect rgb(220, 235, 255)
    Note over Match, Loc: Matching Phase (Async Consumer + Sync CAS Lock)

    (KF)-->>Match: Consume [trip.requested]
    activate Match
    Match->>Match: Save match_requests(PENDING)

    Note right of Match: ⚠️ Sync REST: If Driver/Location dies, Matching fails
    Match->>Loc: GET /api/drivers/available?latitude=X&longitude=Y&radiusKm=3 (Sync REST)
    Loc-->>Match: 200 OK [{driverId, distance, rating, version}] sorted by score

    Note right of Match: ⚠️ Concurrency Control (C1 Race Condition CAS)
    Match->>Loc: POST /api/drivers/{driverId}/reserve {tripId, version} (Sync REST CAS Lock)
    Note right of Loc: UPDATE drivers SET status='RESERVED',<br/>reserved_for_trip=$tripId, version=version+1<br/>WHERE status='AVAILABLE' AND version=$v
    Loc-->>Match: 200 OK {driverId, status: RESERVED, version: N+1}

    Match->>Match: Save driver_offers(ACCEPTED) & match_requests(MATCHED)
    Match-->>(KF): Publish [driver.reserved] {tripId, driverId, correlationId}
    deactivate Match
    end

    rect rgb(255, 248, 220)
    Note over Trip, Notif: Orchestrator Updates Trip State to ASSIGNED

    (KF)-->>Trip: Consume [driver.reserved]
    activate Trip
    Trip->>Trip: ACID TX: Update Trip(ASSIGNED)<br/>+ Update saga_log(DRIVER_RESERVED)<br/>+ Insert outbox_events(driver.assigned)
    deactivate Trip

    Trip-->>(KF): Publish [driver.assigned] {tripId, driverId, correlationId}
    (KF)-->>Notif: Consume [driver.assigned]
    Notif-->>P: WebSocket 🔔 "Driver found! ETA 3 mins"
    Notif-->>Loc: WebSocket 🔔 "New trip assigned to you"
    end

    Note over P, Notif: ... Driver starts ride (Trip IN_PROGRESS) → Driver arrives dropoff ...

    rect rgb(220, 235, 255)
    Note over Loc, Pay: Trip Completion & Payment Charge

    Loc->>Trip: POST /api/trips/{tripId}/complete (Driver action, Sync REST)
    activate Trip
    Trip->>Trip: ACID TX: Update Trip(COMPLETED)<br/>+ Update saga_log(TRIP_COMPLETED)<br/>+ Insert outbox_events(trip.completed)
    Trip-->>Loc: 200 OK
    deactivate Trip

    Trip-->>(KF): Publish [trip.completed] {tripId, passengerId, finalFare, currency, correlationId}
    (KF)-->>Pay: Consume [trip.completed]
    activate Pay
    Note right of Pay: Check processed_events for deduplication<br/>INSERT ON CONFLICT DO NOTHING
    Pay->>Pay: ACID TX: Save Payment(SUCCESS)<br/>+ Insert ledger(CAPTURE)<br/>+ Insert processed_events(eventId)<br/>+ Insert outbox_events(PaymentSucceeded)
    deactivate Pay
    Pay-->>(KF): Publish [payment.success] {tripId, paymentId, amount, correlationId}

    (KF)-->>Trip: Consume [payment.success]
    Trip->>Trip: Update Trip(PAID) + saga_log(SAGA_COMPLETED)
    Trip-->>(KF): Publish [trip.paid] {tripId, correlationId}
    (KF)-->>Notif: Consume [trip.paid]
    Notif-->>P: WebSocket 🔔 "Trip paid successfully. Thank you!"
    end

    rect rgb(255, 220, 220)
    Note over P, Notif: 🔴 COMPENSATION PATH 1: Passenger Cancels During Matching/Assigned

    P->>GW: POST /api/trips/{tripId}/cancel
    GW->>Trip: POST /api/trips/{tripId}/cancel
    activate Trip
    Trip->>Trip: ACID TX: Update Trip(CANCELLED)<br/>+ Update saga_log(COMPENSATING)

    Note right of Trip: Compensation: Release reserved driver back to AVAILABLE
    Trip->>Loc: DELETE /api/drivers/{driverId}/reserve (Sync REST Compensation)
    Loc-->>Trip: 200 OK {driverId, status: AVAILABLE}

    Trip->>Trip: Update saga_log(SAGA_COMPENSATED)<br/>+ Insert outbox_events(trip.cancelled)
    deactivate Trip
    Trip-->>(KF): Publish [trip.cancelled] {tripId, reason: "Passenger cancelled", correlationId}
    (KF)-->>Notif: Consume [trip.cancelled]
    Notif-->>Loc: WebSocket 🔔 "Trip cancelled by passenger. You are AVAILABLE."
    end

    rect rgb(255, 235, 235)
    Note over P, Notif: 🔴 ALTERNATIVE PATH 2: Post-trip Payment Fails (Debt / Retry Log)

    (KF)-->>Pay: Consume [trip.completed]
    activate Pay
    Pay->>Pay: ACID TX: Save Payment(FAILED)<br/>+ Insert ledger audit<br/>+ Insert outbox_events(payment.failed)
    deactivate Pay
    Pay-->>(KF): Publish [payment.failed] {tripId, reason: "Card declined", correlationId}

    (KF)-->>Trip: Consume [payment.failed]
    activate Trip
    Note right of Trip: Ride was finished! Driver is already AVAILABLE.<br/>Do NOT rollback driver. Mark Trip as PAYMENT_FAILED for retry/debt collection.
    Trip->>Trip: Update Trip(PAYMENT_FAILED)<br/>+ Update saga_log(COMPENSATED, error="Payment failed")
    deactivate Trip
    Trip-->>(KF): Publish [payment.failed] {tripId, correlationId}
    (KF)-->>Notif: Consume [payment.failed]
    Notif-->>P: WebSocket 🔔 "Payment failed. Please update payment method."
    end
```

---

## 4. Class Diagram (Domain Models & Distributed Patterns)

Each namespace represents a separate, isolated PostgreSQL database conforming to [database_schema.md](file:///d:/Nam_4/He-thong-phan-tan/ds-project/docs/database_schema.md). Dashed arrows (`..>`) indicate UUID soft references — no physical `FOREIGN KEY` constraints cross database boundaries.

```mermaid
classDiagram
    %% Soft Reference Declarations (UUID, no physical FK)
    User "1" <.. "0..*" Driver : soft ref via user_id
    User "1" <.. "0..*" Trip : soft ref via passenger_id
    Driver "1" <.. "0..*" Trip : soft ref via driver_id
    Trip "1" <.. "0..*" Payment : soft ref via trip_id
    Trip "1" <.. "0..*" MatchRequest : soft ref via trip_id
    Trip "1" <.. "0..*" SagaLog : soft ref via trip_id
    Trip "1" *-- "0..*" OutboxEvent : owns (same ACID TX)
    Payment "1" *-- "0..*" Ledger : owns (same DB)
    Payment "1" *-- "0..*" OutboxEvent_Pay : owns (same ACID TX)
    MatchRequest "1" *-- "0..*" DriverOffer : owns (same DB)

    namespace IdentityDB {
        class User {
            +UUID user_id PK
            +String email UNIQUE
            +String password_hash
            +String full_name
            +String phone_number
            +Enum role PASSENGER|DRIVER|ADMIN
            +Enum status ACTIVE|SUSPENDED|DELETED
            +Int version
            +DateTime created_at
            +DateTime updated_at
        }
        class RefreshToken {
            +UUID token_id PK
            +UUID user_id SOFT_REF
            +String token_hash
            +DateTime expires_at
            +Boolean revoked
        }
    }

    namespace DriverDB {
        class Driver {
            +UUID driver_id PK
            +UUID user_id SOFT_REF
            +String license_number
            +Enum status OFFLINE|AVAILABLE|RESERVED|BUSY
            +UUID reserved_for_trip SOFT_REF
            +DateTime reserved_until
            +Decimal current_lat
            +Decimal current_lng
            +DateTime last_heartbeat_at
            +Int version
            +DateTime updated_at
        }
        class Vehicle {
            +UUID vehicle_id PK
            +UUID driver_id FK
            +String license_plate
            +String make_model
        }
        class DriverLocation {
            +UUID location_id PK
            +UUID driver_id SOFT_REF
            +Decimal lat
            +Decimal lng
            +DateTime recorded_at
        }
    }

    namespace TripDB {
        class Trip {
            +UUID trip_id PK
            +UUID passenger_id SOFT_REF
            +UUID driver_id SOFT_REF nullable
            +Enum status REQUESTED|PRICED|SEARCHING|ASSIGNED|IN_PROGRESS|COMPLETED|PAID|PAYMENT_FAILED|CANCELLED|FAILED
            +Decimal pickup_lat
            +Decimal pickup_lng
            +Decimal dropoff_lat
            +Decimal dropoff_lng
            +Decimal distance_km
            +Decimal estimated_fare
            +String currency
            +UUID correlation_id
            +String idempotency_key UNIQUE
            +Int version
            +DateTime created_at
            +DateTime updated_at
        }

        class SagaLog {
            +UUID log_id PK
            +UUID trip_id SOFT_REF
            +UUID correlation_id
            +String saga_state
            +String compensation_step
            +JSONB context_snapshot
            +String error_detail
            +DateTime created_at
        }

        class OutboxEvent {
            +UUID event_id PK
            +String aggregate_type
            +UUID aggregate_id
            +String event_type
            +JSONB payload
            +UUID correlation_id
            +Boolean published DEFAULT false
            +Int retry_count DEFAULT 0
            +DateTime created_at
            +DateTime published_at nullable
        }

        class ProcessedEvent_Trip {
            +UUID event_id PK
            +String consumer_name PK
            +DateTime processed_at
        }

        class IdempotencyKey {
            +String key_value PK
            +UUID resource_id
            +String resource_type
            +JSONB response_payload
            +DateTime expires_at
        }
    }

    namespace MatchingDB {
        class MatchRequest {
            +UUID request_id PK
            +UUID trip_id SOFT_REF
            +UUID passenger_id SOFT_REF
            +UUID correlation_id
            +Decimal pickup_lat
            +Decimal pickup_lng
            +Decimal search_radius_km
            +Enum status PENDING|MATCHED|NO_DRIVER_FOUND|EXPIRED
            +UUID matched_driver_id SOFT_REF
            +DateTime expires_at
        }

        class DriverOffer {
            +UUID offer_id PK
            +UUID request_id FK
            +UUID driver_id SOFT_REF
            +UUID trip_id SOFT_REF
            +UUID correlation_id
            +Enum status PENDING|ACCEPTED|REJECTED|CONFLICT|EXPIRED
            +Int driver_version_used
            +Decimal distance_km
            +DateTime offered_at
        }

        class ProcessedEvent_Match {
            +UUID event_id PK
            +String consumer_name PK
            +DateTime processed_at
        }
    }

    namespace PaymentDB {
        class Payment {
            +UUID payment_id PK
            +UUID trip_id SOFT_REF
            +UUID passenger_id SOFT_REF
            +Decimal amount
            +String currency
            +Enum status PENDING|SUCCESS|FAILED|REFUNDED
            +String payment_method
            +String gateway_ref
            +String idempotency_key UNIQUE
            +UUID correlation_id
            +Int version
            +DateTime created_at
        }

        class Ledger {
            +UUID ledger_id PK
            +UUID payment_id FK
            +UUID trip_id SOFT_REF
            +Enum entry_type PREAUTH|PREAUTH_REVERSAL|CAPTURE|REFUND|ADJUSTMENT
            +Decimal amount
            +DateTime created_at
        }

        class OutboxEvent_Pay {
            +UUID event_id PK
            +String event_type
            +JSONB payload
            +Boolean published
        }

        class ProcessedEvent_Pay {
            +UUID event_id PK
            +String consumer_name PK
            +DateTime processed_at
        }
    }

    namespace PricingDB {
        class FareRule {
            +UUID rule_id PK
            +String zone
            +Decimal base_fare
            +Decimal per_km_rate
            +Decimal per_minute_rate
            +Decimal surge_multiplier DEFAULT 1.0
            +Boolean is_active
        }

        class SurgeZone {
            +UUID zone_id PK
            +String zone_name
            +Decimal surge_multiplier
            +DateTime effective_until
        }

        class Quote {
            +UUID quote_id PK
            +UUID fare_rule_id FK
            +Decimal distance_km
            +Decimal estimated_fare
            +DateTime expires_at
        }
    }

    namespace NotifDB {
        class Notification {
            +UUID notification_id PK
            +UUID user_id SOFT_REF
            +UUID trip_id SOFT_REF
            +Enum channel WEBSOCKET|PUSH|SMS
            +Enum event_type
            +String title
            +String message
            +Enum status PENDING|SENT|FAILED
            +DateTime sent_at
        }

        class ProcessedEvent_Notif {
            +UUID event_id PK
            +String consumer_name PK
            +DateTime processed_at
        }
    }
```

---

## Architectural Decisions & Distributed Systems Constraints

| Concern | Implementation | Trade-off |
|---|---|---|
| **Message Broker** | **Apache Kafka** (`kafka:9092`) with consumer groups and persistent topics (`trip.requested`, `driver.reserved`, `trip.completed`, etc.). | High throughput, partition-ordered log; requires ZooKeeper/KRaft. |
| **Transactional Outbox** | Save business entity + `outbox_events` in one ACID TX. Relay worker polls and publishes to Kafka. | Adds background relay polling; completely eliminates dual-write split-brain. |
| **Consumer Deduplication** | `processed_events` table per consumer. `INSERT ... ON CONFLICT DO NOTHING` in same TX as consumer logic. | Extra DB write per event; guarantees idempotent exactly-once processing side-effects. |
| **CAS / Optimistic Lock** | `version` column on `trips` and `drivers`. `UPDATE ... WHERE version = $v` returns 0 rows on conflict → retry/409. | Requires client retry logic; avoids pessimistic DB-level row locks and deadlocks. |
| **Saga Orchestration** | `Trip Service` acts as Saga Orchestrator, recording global state in `saga_log`. Compensating actions are driven centrally. | Orchestrator handles timeouts and rollbacks; easier to monitor & trace than distributed choreography. |
| **Correlation IDs** | Injected at API Gateway (`X-Correlation-Id`). Propagated through all REST headers and Kafka event headers/payloads. | Enables complete distributed tracing across Jaeger/Zipkin for all 7 microservices. |
| **Circuit Breakers** | Resilience4j circuit breakers and timeouts (2s) on all synchronous REST calls (`Trip → Pricing`, `Matching → Driver`). | Prevents cascading failures when downstream dependencies slow down or crash. |
