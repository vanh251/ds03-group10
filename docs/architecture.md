# Architecture Documentation

## Ride-Hailing Microservices System (DS03)

**Version:** 1.0.0  
**Date:** 01/10/2026  
**Status:** Development Baseline

---

## Table of Contents

1. [System Context (C4 Level 1)](#1-system-context-c4-level-1)
2. [Container Diagram (C4 Level 2)](#2-container-diagram-c4-level-2)
3. [Component Diagram (C4 Level 3)](#3-component-diagram-c4-level-3)
4. [Bounded Contexts](#4-bounded-contexts)
5. [Communication Patterns](#5-communication-patterns)
6. [Data Architecture](#6-data-architecture)
7. [Concurrency and Consistency Model](#7-concurrency-and-consistency-model)
8. [Distributed Transaction – Saga Pattern](#8-distributed-transaction--saga-pattern)
9. [Fault Tolerance Design](#9-fault-tolerance-design)
10. [Deployment Architecture](#10-deployment-architecture)
11. [Observability Architecture](#11-observability-architecture)
12. [Architecture Decision Records](#12-architecture-decision-records)

---

## 1. System Context (C4 Level 1)

```
+-----------------------------------------+
|            External Actors              |
|                                         |
|  [Passenger Client]   [Driver Client]   |
|  (Mobile/Web App)     (Mobile/Web App)  |
+-----------+-------------------+---------+
            | HTTP/REST         | HTTP/REST
            v                   v
+--------------------------------------------------+
|       Ride-Hailing Microservices System          |
|              (DS03 Course Project)               |
|                                                  |
|  - User registration & JWT authentication        |
|  - Driver availability & location management     |
|  - Trip booking lifecycle management             |
|  - Driver matching with concurrency control      |
|  - Fare calculation                              |
|  - Simulated payment processing                  |
|  - Event-driven notifications                    |
+--------------------------------------------------+
```

---

## 2. Container Diagram (C4 Level 2)

```
+------------------------------------------------------------------------------+
|                    ride-hailing-network (Docker Bridge)                      |
|                                                                              |
|  +-------------------------------------------------------------------+       |
|  |                    API Gateway :8080                              | <---- | External Clients
|  |       (Spring Cloud Gateway - Auth filter + routing)             |       |
|  +----+--------+--------+----------+----------+--------------------+        |
|       |        |        |          |          |                             |
|  +----+--+ +---+---+ +--+---+ +----+--+ +----+--+ +-------------+          |
|  |identity| |driver| |trip  | |match  | |pricing | |  payment   |          |
|  |service | |service| |svc  | |service| |service | |  service   |          |
|  | :8081  | | :8082 | |:8083| | :8084 | | :8085  | |   :8086    |          |
|  +----+---+ +--+----+ +--+--+ +---+---+ +--------+ +------+-----+          |
|       |        |         |        |                        |                |
|  +----+--+ +---+---+ +---+--+ +---+---+              +----+---+            |
|  |ident- | |driver-| |trip- | |match- |              |payment-|            |
|  |  db   | |  db   | |  db  | |  db   |              |   db   |            |
|  +-------+ +-------+ +--+---+ +-------+              +--------+            |
|                          |                                                  |
|  +--------------------+  |  +------------------------+                     |
|  | Apache Kafka :9092 |<-+  | notification-service   |                     |
|  |   (KRaft mode)     |---> |        :8087           |                     |
|  +--------------------+     +------------------------+                     |
|                                                                             |
|  +-------------------+  +-----------------------------+                    |
|  | Prometheus :9090  |  |       Grafana :3000         |                    |
|  +-------------------+  +-----------------------------+                    |
+------------------------------------------------------------------------------+
```

### Container Responsibilities

| Container | Technology | Port | Persistence | Kafka |
|---|---|---|---|---|
| api-gateway | Spring Cloud Gateway | 8080 | None | No |
| identity-service | Spring Boot 3.x | 8081 | PostgreSQL `identity_db` | No |
| driver-service | Spring Boot 3.x | 8082 | PostgreSQL `driver_db` | No |
| trip-service | Spring Boot 3.x | 8083 | PostgreSQL `trip_db` | Producer + Consumer |
| matching-service | Spring Boot 3.x | 8084 | PostgreSQL `matching_db` | Producer + Consumer |
| pricing-service | Spring Boot 3.x | 8085 | None (stateless) | No |
| payment-service | Spring Boot 3.x | 8086 | PostgreSQL `payment_db` | Producer + Consumer |
| notification-service | Spring Boot 3.x | 8087 | None (log-only) | Consumer |
| kafka | Apache Kafka 3.8.0 (KRaft) | 9092 | Volume `kafka_data` | — |
| identity-db | PostgreSQL 16 | 5432 | Volume | — |
| driver-db | PostgreSQL 16 | 5432 | Volume | — |
| trip-db | PostgreSQL 16 | 5432 | Volume | — |
| matching-db | PostgreSQL 16 | 5432 | Volume | — |
| payment-db | PostgreSQL 16 | 5432 | Volume | — |
| prometheus | Prometheus 2.54.1 | 9090 | Volume `prometheus_data` | — |
| grafana | Grafana 11.2.0 | 3000 | Volume `grafana_data` | — |

---

## 3. Component Diagram (C4 Level 3)

### Trip Service (Saga Orchestrator)

```
+------------------------------------------------------------+
|                       trip-service                         |
|                                                            |
|  +------------------+   +------------------------------+  |
|  |  REST Controller |   |   Kafka Consumer             |  |
|  |  /api/trips/**   |   |  (driver.reserved,           |  |
|  +--------+---------+   |   payment.success,           |  |
|           |             |   payment.failed)            |  |
|           v             +---------------+--------------+  |
|  +----------------------------------------------------------+  |
|  |             Trip Domain Service                      |  |
|  |  - Manage trip state machine                         |  |
|  |  - Orchestrate Saga (saga_log table)                 |  |
|  |  - Idempotency (idempotency_keys table)              |  |
|  +----------------------------------------------------------+  |
|           |                        |                     |
|  +--------+----------+  +----------+--------------------+|
|  | Trip Repository   |  |   Kafka Producer              ||
|  | (JPA / Hibernate) |  |  (trip.requested,             ||
|  +--------+----------+  |   trip.completed,             ||
|           |             |   trip.cancelled)             ||
|           v             +-------------------------------+|
|    PostgreSQL (trip_db)                                  |
+------------------------------------------------------------+
```

### Matching Service

```
+-----------------------------------------------------------+
|                    matching-service                       |
|                                                           |
|  +--------------------+  +---------------------------+   |
|  |   Kafka Consumer   |  |   REST Endpoint           |   |
|  |  (trip.requested)  |  |  /api/matching/**         |   |
|  +----------+---------+  +-----------+---------------+   |
|             |                        |                   |
|             v                        v                   |
|  +-----------------------------------------------------------+  |
|  |           Matching Domain Service                     |  |
|  |  1. Query Driver Service: GET /api/drivers/available  |  |
|  |  2. Calculate distance (Haversine)                    |  |
|  |  3. POST /api/drivers/{id}/reserve (CAS lock)         |  |
|  |  4. Handle rejection -> rematch                       |  |
|  +-----------------------------------------------------------+  |
|             |                     |                      |
|  +----------+--------+  +---------+--------------------+ |
|  | Match Repository  |  |    Kafka Producer            | |
|  +----------+--------+  |  (driver.reserved)           | |
|             |           +------------------------------+ |
|             v                                            |
|   PostgreSQL (matching_db)                               |
+-----------------------------------------------------------+
```

---

## 4. Bounded Contexts

```
+----------------------------+----------------------------------+
|  Identity Context          |  Driver Context                  |
|  - User aggregate          |  - Driver aggregate              |
|  - Role (PASS/DRIVER)      |  - Vehicle aggregate             |
|  - JWT token mgmt          |  - Location (simulated GPS)      |
|  - Password security       |  - Availability state machine    |
|                            |  - CAS reservation (30s lease)   |
+----------------------------+----------------------------------+
|  Trip Context (Orch.)      |  Matching Context                |
|  - RideRequest aggregate   |  - MatchRequest aggregate        |
|  - Trip state machine      |  - DriverOffer aggregate         |
|  - Saga orchestration      |  - Proximity algorithm           |
|  - saga_log (audit trail)  |  - Timeout & rematch logic       |
|  - Outbox events           |                                  |
+----------------------------+----------------------------------+
|  Pricing Context           |  Payment Context                 |
|  - Fare calculation        |  - Payment aggregate             |
|  - baseFare + dist rate    |  - Idempotency (dedup)           |
|  - + time rate             |  - Charge / Refund               |
|  - Stateless (no DB)       |  - Ledger records                |
+----------------------------+----------------------------------+
|  Notification Context                                        |
|  - Consumes all domain events via Kafka                      |
|  - Fan-out to Passenger / Driver (log/console in demo)       |
+--------------------------------------------------------------+
```

### Context Mapping

| Upstream | Downstream | Relationship |
|---|---|---|
| Identity Service | API Gateway | Shared Kernel (JWT public key) |
| Trip Service | Matching Service | Published Language (Kafka events) |
| Trip Service | Payment Service | Published Language (Kafka events) |
| Matching Service | Driver Service | Open Host Service (REST) |
| Trip Service | Pricing Service | Open Host Service (REST) |
| All Services | Notification Service | Published Language (events -> fan-out) |

---

## 5. Communication Patterns

### 5.1. Synchronous (HTTP/REST)

Used for: operations requiring an immediate response.

```
Client -> API Gateway -> Target Service -> Response
```

| Caller | Callee | Endpoint | Purpose |
|---|---|---|---|
| Passenger | Trip Service | POST /api/trips | Create ride request |
| Matching Service | Driver Service | GET /api/drivers/available | Find candidates (`/nearby` is passenger-only) |
| Matching Service | Driver Service | POST /api/drivers/{id}/reserve | CAS lock driver |
| Trip Service | Pricing Service | POST /api/pricing/quote | Estimate fare |
| Trip Service | Pricing Service | POST /api/pricing/final | Final fare |

### 5.2. Asynchronous (Apache Kafka)

Used for: cross-service workflows requiring decoupling and durability.

```
Producer Service -> Kafka Topic -> Consumer Service(s)
```

Full event catalogue: see `events.md`.

### 5.3. Service Discovery

**Method:** Docker Compose DNS (no Eureka / Consul).

All services reside in `ride-hailing-network`. Names resolve via Docker's embedded DNS resolver.

```
trip-service calls -> http://matching-service:8084
api-gateway routes -> http://identity-service:8081
```

---

## 6. Data Architecture

### 6.1. Database-per-Service

Each business service owns its exclusive PostgreSQL instance. Services must **never** share databases or schemas.

| Service | Database Name | Key Tables |
|---|---|---|
| identity-service | `identity_db` | `users`, `refresh_tokens` |
| driver-service | `driver_db` | `drivers`, `vehicles`, `driver_locations`, `driver_status`, `processed_events` |
| trip-service | `trip_db` | `ride_requests`, `trips`, `trip_status_history`, `saga_log`, `outbox_events`, `processed_events`, `idempotency_keys` |
| matching-service | `matching_db` | `match_requests`, `driver_offers`, `processed_events` |
| payment-service | `payment_db` | `payments`, `ledger`, `outbox_events`, `processed_events` |

### 6.2. Outbox Pattern

To guarantee at-least-once event delivery without two-phase commit:

```
1. Business logic + INSERT outbox_events  -> single local DB transaction
2. Outbox poller reads unpublished events
3. Publish to Kafka
4. Mark event as published
```

### 6.3. Deduplication Table

Consumers prevent duplicate side effects using `processed_events`:

```sql
-- Check before processing
SELECT 1 FROM processed_events WHERE event_id = :eventId;
-- Found -> skip. Not found -> process + INSERT event_id
```

---

## 7. Concurrency and Consistency Model

### 7.1. Consistency Guarantees

| Component | Consistency Model |
|---|---|
| Driver reservation | **Strong** – CAS via atomic SQL UPDATE |
| Trip state transitions | **Strong** – within Trip Service DB |
| Matching -> Trip notification | **Eventual** – via Kafka |
| Trip -> Payment flow | **Eventual** – via Kafka |
| Notification delivery | **Eventual** – best effort |

### 7.2. Optimistic Concurrency Control (Driver Reservation)

**Problem:** Two passengers simultaneously request the same AVAILABLE driver.

**Solution:** Atomic CAS UPDATE:

```sql
UPDATE drivers
SET    status            = 'RESERVED',
       reserved_for_trip = :tripId,
       reserved_until    = NOW() + INTERVAL '30 seconds',
       version           = version + 1
WHERE  driver_id = :driverId
  AND  status    = 'AVAILABLE'
  AND  version   = :expectedVersion;
```

- `affectedRows = 1` → reservation succeeded
- `affectedRows = 0` → driver taken → find another candidate or retry

**Lease expiry:** 30 seconds. A cleanup scheduler returns expired `RESERVED` drivers to `AVAILABLE`.

### 7.3. Trip State Machine

```
REQUESTED -> SEARCHING -> ASSIGNED -> IN_PROGRESS -> COMPLETED -> PAID
                 |                                                  ^
                 | (timeout / no driver)                            |
                 v                     CANCELLED <------------------+
             CANCELLED              (passenger cancel)
                                                  PAYMENT_FAILED
                                              (after COMPLETED, retry)
```

---

## 8. Distributed Transaction – Saga Pattern

### 8.1. Architecture

The **Trip Service** acts as the central saga orchestrator, tracking state in `saga_log`:

```
[Trip Service]
  Save Trip(REQUESTED) + saga_log(STARTED)
      |
      v publish: trip.requested -> Kafka
[Matching Service]
  Find nearby -> CAS reserve -> publish: driver.reserved -> Kafka
      |
      v
[Trip Service]
  Consume driver.reserved -> Trip(ASSIGNED) + saga_log(DRIVER_RESERVED)
      |
      ... Driver starts and completes ride ...
      |
      v
[Trip Service]
  POST /api/trips/{id}/complete -> Trip(COMPLETED) + saga_log(TRIP_COMPLETED)
  publish: trip.completed -> Kafka
      |
      v
[Payment Service]
  Consume trip.completed -> deduplicate -> charge
  publish: payment.success OR payment.failed -> Kafka
      |
      +---> payment.success -> [Trip Service] Trip(PAID) + saga_log(SAGA_COMPLETED)
      |
      +---> payment.failed  -> [Trip Service] Trip(PAYMENT_FAILED) + saga_log(COMPENSATED)
```

### 8.2. Compensating Actions

| Scenario | Compensation |
|---|---|
| Passenger cancels during SEARCHING/ASSIGNED | DELETE /api/drivers/{id}/reserve (sync) -> Driver returns to AVAILABLE |
| Matching timeout | Same as above |
| Payment failure after trip completion | Trip marked PAYMENT_FAILED; debt logged; driver state NOT rolled back |

### 8.3. saga_log Table Schema

```sql
CREATE TABLE saga_log (
    id         UUID PRIMARY KEY,
    trip_id    UUID NOT NULL,
    step_name  VARCHAR(100) NOT NULL,
    -- Values: STARTED | DRIVER_RESERVED | TRIP_COMPLETED | SAGA_COMPLETED | COMPENSATED
    status     VARCHAR(50) NOT NULL,
    payload    JSONB,
    created_at TIMESTAMP DEFAULT NOW()
);
```

---

## 9. Fault Tolerance Design

### 9.1. Message Queue Durability

```
Trip Service -> Kafka (message persisted) -> Matching Service (DOWN)
                                                    v (restart)
                                           Matching Service (UP)
                                                    v
                                           Consume pending messages -> resume matching
```

### 9.2. Retry with Exponential Backoff

```
Attempt 1 -> fail -> wait 1s
Attempt 2 -> fail -> wait 2s
Attempt 3 -> fail -> wait 4s
       v
Dead Letter Queue (DLQ)
```

Maximum retry: 3 attempts. After max retry -> message moved to DLQ topic.

### 9.3. Driver Disconnection Recovery

```
RESERVED driver heartbeat timeout
       v
reserved_until exceeded
       v
Scheduler: set status = AVAILABLE
       v
Matching Service: rematch
```

### 9.4. Payment Idempotency

Payment Service uses `processed_events` deduplication:
- All payment events include a `eventId` (UUID)
- Before processing: check if `eventId` already exists
- If duplicate -> discard silently
- Guarantees: no double-charge on Kafka retry

---

## 10. Deployment Architecture

### 10.1. Runtime Environment

Docker Compose on a single host (development/demo). 16 containers on bridge network `ride-hailing-network`.

### 10.2. Port Exposure Policy

| Service | Host Port | Rationale |
|---|---|---|
| api-gateway | 8080 | Client entry point |
| prometheus | 9090 | Monitoring access |
| grafana | 3000 | Dashboard access |
| All backend services | (none) | Security – internal Docker network only |

### 10.3. Healthcheck Dependency Chain

```
identity-db  --> identity-service  -->+
driver-db    --> driver-service    -->|
trip-db      --> trip-service      -->|---> api-gateway
matching-db  -->+                     |
kafka        -->+--> matching-service->|
driver-svc   -->+                     |
payment-db   -->+                     |
kafka        -->+--> payment-service ->|
kafka        --> notification-service->+
pricing-service (no hard dependencies)->+
```

### 10.4. Directory Structure

```
ds-project/                    <- Repository root (NOT a Gradle project)
|-- docker-compose.yml
|-- .env / .env.example
|-- docs/
|-- deploy/
|   `-- monitoring/
|       |-- prometheus/prometheus.yml
|       `-- grafana/provisioning/datasources/prometheus.yml
`-- src/
    |-- api-gateway/           <- Independent Gradle project
    |-- identity-service/      <- Independent Gradle project
    |-- driver-service/        <- Independent Gradle project
    |-- trip-service/          <- Independent Gradle project
    |-- matching-service/      <- Independent Gradle project
    |-- pricing-service/       <- Independent Gradle project
    |-- payment-service/       <- Independent Gradle project
    `-- notification-service/  <- Independent Gradle project
```

---

## 11. Observability Architecture

### 11.1. Metrics Pipeline

```
Spring Boot Actuator (/actuator/prometheus)
          v
Micrometer Prometheus Registry
          v
Prometheus (scrapes all 8 services every 15s via Docker DNS)
          v
Grafana (dashboards)
```

### 11.2. Key Metrics

| Metric | Source | Description |
|---|---|---|
| `http_requests_total` | All services | Total HTTP requests by method/status |
| `http_request_duration_seconds` | All services | Request latency histogram |
| `matching_success_total` | matching-service | Successful driver matches |
| `matching_failure_total` | matching-service | Failed matches |
| `payment_success_total` | payment-service | Successful payments |
| `payment_failure_total` | payment-service | Failed payments |
| `kafka_consumer_lag` | All Kafka consumers | Message processing backlog |
| `kafka_dlq_messages_total` | All services | Dead letter queue volume |

### 11.3. Structured Log Format

```json
{
  "timestamp": "2026-09-30T10:20:01Z",
  "service": "matching-service",
  "level": "INFO",
  "eventType": "DriverMatched",
  "correlationId": "corr-001",
  "requestId": "req-abc",
  "tripId": "T001",
  "driverId": "D001",
  "result": "SUCCESS",
  "durationMs": 43
}
```

### 11.4. Correlation ID Propagation

```
Passenger Request (correlationId=corr-001)
       v
Trip Service (log correlationId)
       v
Kafka events (embed correlationId in payload)
       v
Matching Service (log correlationId)
       v
Payment Service (log correlationId)
       v
Notification Service (log correlationId)
```

---

## 12. Architecture Decision Records

### ADR-001: Microservices over Monolith

**Status:** Accepted

**Context:** DS03 requires demonstrating distributed systems concepts: REST, async messaging, distributed transactions, fault tolerance, concurrency control.

**Decision:** 8 independent microservices (1 gateway + 7 business services).

**Consequences:**
- Each service is independently deployable and scalable
- Bounded contexts enforced by separate databases
- Kafka isolates failures between services
- Trade-off: 16 containers required, operational complexity increases

---

### ADR-002: Apache Kafka over RabbitMQ

**Status:** Accepted

**Context:** Need durable async messaging for Trip -> Matching -> Payment -> Notification flows.

**Decision:** Apache Kafka 3.8.0 in KRaft mode (no ZooKeeper).

**Consequences:**
- Message replay and durability built-in
- Consumer groups allow independent scaling
- KRaft eliminates ZooKeeper dependency
- Trade-off: heavier resource usage than RabbitMQ

---

### ADR-003: Docker DNS over Eureka

**Status:** Accepted

**Context:** Service-to-service discovery is required. Traditional Spring Cloud uses Eureka.

**Decision:** Docker Compose service names as DNS entries. No Eureka server.

**Consequences:**
- Zero additional infrastructure components
- Spring Cloud Gateway uses plain `http://service-name:port` (no `lb://`)
- Trade-off: no client-side load balancing; not portable to Kubernetes without modification

---

### ADR-004: Saga Orchestration via Trip Service

**Status:** Accepted

**Context:** The ride lifecycle spans multiple services. 2PC is not viable in microservices.

**Decision:** Trip Service as central Saga orchestrator, tracking state in `saga_log`.

**Consequences:**
- Single authoritative source of saga state
- Compensating actions are explicit and auditable
- Trade-off: Trip Service is a critical path; its unavailability blocks the entire flow

---

### ADR-005: Optimistic Concurrency Control for Driver Reservation

**Status:** Accepted

**Context:** Multiple Matching Service instances may race to reserve the same driver.

**Decision:** Atomic SQL `UPDATE ... WHERE status = 'AVAILABLE' AND version = :v`. No Redis/ZooKeeper distributed lock.

**Consequences:**
- Correctness guaranteed without external lock infrastructure
- Portable (PostgreSQL only)
- Trade-off: high contention degrades throughput (acceptable at demo scale)

---

### ADR-006: Independent Gradle Projects

**Status:** Accepted

**Context:** Services must be independently buildable. The root should not be a Gradle multi-project.

**Decision:** Each service has its own `build.gradle`, `settings.gradle`, `gradlew`. Root has no Gradle files.

**Consequences:**
- Docker build context is self-contained per service
- No coupling through shared root build files
- Trade-off: no shared `common-library` module (DTOs kept minimal or duplicated)

---

### ADR-007: PostgreSQL for All Persistent Services

**Status:** Accepted

**Context:** Services require relational storage with ACID guarantees.

**Decision:** PostgreSQL 16 Alpine for all database containers.

**Consequences:**
- ACID transactions within each service's database
- JSONB support for event payloads in `saga_log` and `outbox_events`
- `pg_isready` for Docker healthchecks
- Wide Spring Data JPA compatibility
