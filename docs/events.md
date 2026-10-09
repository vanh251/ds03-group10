# Event Catalogue

## Ride-Hailing Microservices System (DS03)

**Version:** 1.0.0  
**Date:** 01/10/2026  
**Message Broker:** Apache Kafka 3.8.0 (KRaft mode)  
**Bootstrap Server:** `kafka:9092` (Docker internal)

---

## Table of Contents

1. [Event Design Principles](#1-event-design-principles)
2. [Event Envelope Schema](#2-event-envelope-schema)
3. [Kafka Topics](#3-kafka-topics)
4. [Event Catalogue](#4-event-catalogue)
   - [trip.requested](#41-triprequested)
   - [driver.reserved](#42-driverreserved)
   - [driver.assigned](#43-driverassigned)
   - [trip.started](#44-tripstarted)
   - [trip.completed](#45-tripcompleted)
   - [payment.success](#46-paymentsuccess)
   - [payment.failed](#47-paymentfailed)
   - [trip.cancelled](#48-tripcancelled)
5. [Consumer Group Assignments](#5-consumer-group-assignments)
6. [Dead Letter Queue](#6-dead-letter-queue)
7. [Deduplication](#7-deduplication)
8. [Retry Strategy](#8-retry-strategy)
9. [Saga Event Flow](#9-saga-event-flow)
10. [Correlation ID Propagation](#10-correlation-id-propagation)

---

## 1. Event Design Principles

### At-Least-Once Delivery
Kafka guarantees at-least-once delivery. Consumers **must** be idempotent using the `eventId` deduplication mechanism.

### Immutability
Events are immutable facts. Never modify a published event. Corrections are new events.

### Event Envelope
Every event must include the standard envelope fields (`eventId`, `eventType`, `correlationId`, etc.) before the domain-specific `payload`.

### Consumer Responsibility
- Each consumer checks `processed_events` table before processing
- If `eventId` found → skip (idempotent)
- If not found → process → insert `eventId`

### Outbox Pattern
Producers use the Outbox Pattern to guarantee event publication:
1. Write business entity + event to `outbox_events` in a single transaction
2. Outbox poller publishes to Kafka
3. Mark `outbox_events` record as published

---

## 2. Event Envelope Schema

All events share a common envelope:

```json
{
  "eventId": "evt-550e8400-e29b-41d4-a716-446655440000",
  "eventType": "RideRequested",
  "aggregateId": "trip-001",
  "aggregateType": "Trip",
  "producer": "trip-service",
  "correlationId": "corr-f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "causationId": "evt-00000000-0000-0000-0000-000000000000",
  "schemaVersion": "1.0",
  "occurredAt": "2026-09-30T10:00:00.000Z",
  "payload": {}
}
```

> **Identifiers:** every `driverId` in an event payload is the driver's identity user id (`drivers.driver_id = users.user_id`), the same value as the JWT `sub` and the `X-User-Id` header of that driver.

### Envelope Fields

| Field | Type | Required | Description |
|---|---|---|---|
| `eventId` | UUID | Yes | Globally unique event ID. Used for deduplication |
| `eventType` | string | Yes | Event name (PascalCase) |
| `aggregateId` | string | Yes | ID of the primary aggregate (e.g., tripId) |
| `aggregateType` | string | Yes | Type of aggregate (e.g., `Trip`, `Driver`, `Payment`) |
| `producer` | string | Yes | Source service name |
| `correlationId` | UUID | Yes | Business flow correlation ID (spans all services) |
| `causationId` | UUID | No | ID of the event that caused this event |
| `schemaVersion` | string | Yes | Payload schema version for forward compatibility |
| `occurredAt` | ISO 8601 | Yes | UTC timestamp when the event occurred |
| `payload` | object | Yes | Domain-specific event data |

---

## 3. Kafka Topics

| Topic | Partitions | Producers | Consumers | Description |
|---|---|---|---|---|
| `trip-events` | 3 | trip-service | matching-service, notification-service | Trip lifecycle events |
| `matching-events` | 3 | matching-service | trip-service, notification-service | Driver matching results |
| `payment-events` | 3 | payment-service | trip-service, notification-service | Payment results |
| `trip-events.DLQ` | 1 | Kafka (auto) | Manual / monitoring | Dead letters from trip-events |
| `matching-events.DLQ` | 1 | Kafka (auto) | Manual / monitoring | Dead letters from matching-events |
| `payment-events.DLQ` | 1 | Kafka (auto) | Manual / monitoring | Dead letters from payment-events |

### Topic Configuration (recommended for demo)

```
replication.factor=1
min.insync.replicas=1
retention.ms=604800000   # 7 days
```

---

## 4. Event Catalogue

---

### 4.1. `trip.requested`

A passenger has created a ride request. Initiates the driver matching process.

**Topic:** `trip-events`  
**Producer:** `trip-service`  
**Consumers:** `matching-service`, `notification-service`

**Trigger:** Passenger calls `POST /api/trips`

**Full Event:**

```json
{
  "eventId": "evt-001",
  "eventType": "RideRequested",
  "aggregateId": "trip-001",
  "aggregateType": "Trip",
  "producer": "trip-service",
  "correlationId": "corr-001",
  "causationId": null,
  "schemaVersion": "1.0",
  "occurredAt": "2026-09-30T10:00:00Z",
  "payload": {
    "tripId": "trip-001",
    "passengerId": "passenger-001",
    "pickup": {
      "latitude": 21.0285,
      "longitude": 105.8542,
      "address": "Hoan Kiem, Hanoi"
    },
    "destination": {
      "latitude": 21.0188,
      "longitude": 105.8520,
      "address": "Dong Da, Hanoi"
    },
    "estimatedFare": 45000,
    "requestedAt": "2026-09-30T10:00:00Z"
  }
}
```

**Consumer Actions:**

| Consumer | Action |
|---|---|
| matching-service | Start driver search: query nearby AVAILABLE drivers, apply CAS reservation |
| notification-service | Notify passenger: "Searching for a driver..." |

---

### 4.2. `driver.reserved`

A driver has been successfully reserved by the Matching Service (CAS lock acquired).

**Topic:** `matching-events`  
**Producer:** `matching-service`  
**Consumers:** `trip-service`, `notification-service`

**Trigger:** Matching Service successfully reserves a driver via `POST /api/drivers/{id}/reserve`

**Full Event:**

```json
{
  "eventId": "evt-002",
  "eventType": "DriverReserved",
  "aggregateId": "trip-001",
  "aggregateType": "Trip",
  "producer": "matching-service",
  "correlationId": "corr-001",
  "causationId": "evt-001",
  "schemaVersion": "1.0",
  "occurredAt": "2026-09-30T10:00:05Z",
  "payload": {
    "tripId": "trip-001",
    "driverId": "d001",
    "driverName": "Tran Van B",
    "driverLocation": {
      "latitude": 21.0290,
      "longitude": 105.8545
    },
    "estimatedArrivalMin": 3,
    "reservedUntil": "2026-09-30T10:00:35Z"
  }
}
```

**Consumer Actions:**

| Consumer | Action |
|---|---|
| trip-service | Update trip status: `SEARCHING -> ASSIGNED`; record `DRIVER_RESERVED` in `saga_log` |
| notification-service | Notify passenger: "Driver found! [name] is on the way" |

---

### 4.3. `driver.assigned`

A driver has been formally assigned to the trip by Trip Service.

**Topic:** `trip-events`  
**Producer:** `trip-service`  
**Consumers:** `driver-service`, `notification-service`

**Trigger:** Trip Service processes `driver.reserved` and updates trip to `ASSIGNED`

**Full Event:**

```json
{
  "eventId": "evt-003",
  "eventType": "DriverAssigned",
  "aggregateId": "trip-001",
  "aggregateType": "Trip",
  "producer": "trip-service",
  "correlationId": "corr-001",
  "causationId": "evt-002",
  "schemaVersion": "1.0",
  "occurredAt": "2026-09-30T10:00:06Z",
  "payload": {
    "tripId": "trip-001",
    "driverId": "d001",
    "passengerId": "passenger-001",
    "assignedAt": "2026-09-30T10:00:06Z"
  }
}
```

**Consumer Actions:**

| Consumer | Action |
|---|---|
| driver-service | Dedup via `eventId` → update driver status `RESERVED -> BUSY` only if `drivers.reserved_for_trip = tripId`; clear `reserved_until` (lease no longer applies); keep `reserved_for_trip`. No match → log and skip |
| notification-service | Notify driver: "You have been assigned a new trip" |

---

### 4.4. `trip.started`

The driver has started the trip (picked up the passenger).

**Topic:** `trip-events`  
**Producer:** `trip-service`  
**Consumers:** `notification-service`

> driver-service does **not** consume this event: the driver is already `BUSY` since `driver.assigned`.

**Trigger:** Driver calls `POST /api/trips/{id}/start`

**Full Event:**

```json
{
  "eventId": "evt-004",
  "eventType": "TripStarted",
  "aggregateId": "trip-001",
  "aggregateType": "Trip",
  "producer": "trip-service",
  "correlationId": "corr-001",
  "causationId": "evt-003",
  "schemaVersion": "1.0",
  "occurredAt": "2026-09-30T10:05:00Z",
  "payload": {
    "tripId": "trip-001",
    "driverId": "d001",
    "passengerId": "passenger-001",
    "startedAt": "2026-09-30T10:05:00Z",
    "driverLocation": {
      "latitude": 21.0285,
      "longitude": 105.8542
    }
  }
}
```

**Consumer Actions:**

| Consumer | Action |
|---|---|
| notification-service | Notify passenger: "Your ride has started" |

---

### 4.5. `trip.completed`

The driver has completed the trip (arrived at destination). Triggers payment processing.

**Topic:** `trip-events`  
**Producer:** `trip-service`  
**Consumers:** `payment-service`, `driver-service`, `notification-service`

**Trigger:** Driver calls `POST /api/trips/{id}/complete`

**Full Event:**

```json
{
  "eventId": "evt-005",
  "eventType": "TripCompleted",
  "aggregateId": "trip-001",
  "aggregateType": "Trip",
  "producer": "trip-service",
  "correlationId": "corr-001",
  "causationId": "evt-004",
  "schemaVersion": "1.0",
  "occurredAt": "2026-09-30T10:25:00Z",
  "payload": {
    "tripId": "trip-001",
    "driverId": "d001",
    "passengerId": "passenger-001",
    "actualDistanceKm": 2.8,
    "actualDurationMin": 20,
    "finalFare": 47500,
    "currency": "VND",
    "completedAt": "2026-09-30T10:25:00Z"
  }
}
```

**Consumer Actions:**

| Consumer | Action |
|---|---|
| payment-service | Deduplicate via `eventId` → process charge → publish `payment.success` or `payment.failed` |
| driver-service | Dedup via `eventId` → release driver `BUSY -> AVAILABLE` only if `drivers.reserved_for_trip = tripId`; clear `reserved_for_trip`, `version + 1`. No match → log and skip |
| notification-service | Notify passenger: "Trip completed. Processing payment..." |

> The driver is released as soon as the trip is completed, **independent of the payment result**. A later `payment.failed` never moves the driver back (see §9 and SRS §13.2).

---

### 4.6. `payment.success`

Payment has been captured successfully.

**Topic:** `payment-events`  
**Producer:** `payment-service`  
**Consumers:** `trip-service`, `notification-service`

**Trigger:** Payment Service successfully processes charge

**Full Event:**

```json
{
  "eventId": "evt-006",
  "eventType": "PaymentSucceeded",
  "aggregateId": "pay-001",
  "aggregateType": "Payment",
  "producer": "payment-service",
  "correlationId": "corr-001",
  "causationId": "evt-005",
  "schemaVersion": "1.0",
  "occurredAt": "2026-09-30T10:25:05Z",
  "payload": {
    "paymentId": "pay-001",
    "tripId": "trip-001",
    "passengerId": "passenger-001",
    "amount": 47500,
    "currency": "VND",
    "processedAt": "2026-09-30T10:25:05Z"
  }
}
```

**Consumer Actions:**

| Consumer | Action |
|---|---|
| trip-service | Update trip status: `COMPLETED -> PAID`; record `SAGA_COMPLETED` in `saga_log` |
| notification-service | Notify passenger: "Payment successful. Thank you for riding!" |
| notification-service | Notify driver: "Payment received for trip [id]" |

---

### 4.7. `payment.failed`

Payment charge has failed.

**Topic:** `payment-events`  
**Producer:** `payment-service`  
**Consumers:** `trip-service`, `notification-service`

**Trigger:** Payment Service fails to process charge (simulated failure or retry exhausted)

**Full Event:**

```json
{
  "eventId": "evt-007",
  "eventType": "PaymentFailed",
  "aggregateId": "pay-001",
  "aggregateType": "Payment",
  "producer": "payment-service",
  "correlationId": "corr-001",
  "causationId": "evt-005",
  "schemaVersion": "1.0",
  "occurredAt": "2026-09-30T10:25:15Z",
  "payload": {
    "paymentId": "pay-001",
    "tripId": "trip-001",
    "passengerId": "passenger-001",
    "amount": 47500,
    "currency": "VND",
    "reason": "SIMULATED_FAILURE",
    "retryCount": 3,
    "failedAt": "2026-09-30T10:25:15Z"
  }
}
```

**Consumer Actions:**

| Consumer | Action |
|---|---|
| trip-service | Update trip status: `COMPLETED -> PAYMENT_FAILED`; record `COMPENSATED` in `saga_log`; log debt |
| notification-service | Notify passenger: "Payment failed. Please retry or contact support." |

> **Note:** Driver state is NOT rolled back. The driver has completed their service. Only the payment record is marked failed for retry or manual resolution.

---

### 4.8. `trip.cancelled`

A trip has been cancelled by the passenger (or timeout).

**Topic:** `trip-events`  
**Producer:** `trip-service`  
**Consumers:** `driver-service`, `matching-service`, `notification-service`

**Trigger:** Passenger calls `POST /api/trips/{id}/cancel` OR matching timeout

**Full Event:**

```json
{
  "eventId": "evt-008",
  "eventType": "TripCancelled",
  "aggregateId": "trip-001",
  "aggregateType": "Trip",
  "producer": "trip-service",
  "correlationId": "corr-001",
  "causationId": null,
  "schemaVersion": "1.0",
  "occurredAt": "2026-09-30T10:01:30Z",
  "payload": {
    "tripId": "trip-001",
    "passengerId": "passenger-001",
    "driverId": "d001",
    "reason": "PASSENGER_CANCELLED",
    "previousStatus": "ASSIGNED",
    "cancelledAt": "2026-09-30T10:01:30Z"
  }
}
```

**Cancellation Reasons:**

| Reason | Description |
|---|---|
| `PASSENGER_CANCELLED` | Passenger explicitly cancelled |
| `MATCHING_TIMEOUT` | No driver found within timeout window |
| `DRIVER_REJECTED` | Driver rejected and no rematch available |

**Consumer Actions:**

| Consumer | Action |
|---|---|
| driver-service | Dedup via `eventId` → find driver by `driverId`; if `driverId` is missing, by `reserved_for_trip = tripId` → release `RESERVED/BUSY -> AVAILABLE` only if `drivers.reserved_for_trip = tripId`; clear `reserved_for_trip` and `reserved_until`, `version + 1`. No match (no driver assigned yet, or driver already on another trip) → log and skip |
| matching-service | Cancel any in-progress matching job |
| notification-service | Notify passenger: "Trip cancelled" |
| notification-service | Notify driver (if assigned): "Trip has been cancelled" |

---

## 5. Consumer Group Assignments

| Consumer Service | Consumer Group ID | Topics Subscribed |
|---|---|---|
| matching-service | `matching-service-group` | `trip-events` |
| trip-service | `trip-service-group` | `matching-events`, `payment-events` |
| payment-service | `payment-service-group` | `trip-events` (for `trip.completed` only) |
| driver-service | `driver-service-group` | `trip-events` |
| notification-service | `notification-service-group` | `trip-events`, `matching-events`, `payment-events` |

### Event Type Filtering per Consumer

Each consumer filters by `eventType` within its subscribed topics:

| Consumer | Topic | EventTypes Consumed |
|---|---|---|
| matching-service | `trip-events` | `RideRequested` |
| trip-service | `matching-events` | `DriverReserved` |
| trip-service | `payment-events` | `PaymentSucceeded`, `PaymentFailed` |
| payment-service | `trip-events` | `TripCompleted` |
| driver-service | `trip-events` | `DriverAssigned`, `TripCompleted`, `TripCancelled` |
| notification-service | `trip-events` | `RideRequested`, `DriverAssigned`, `TripStarted`, `TripCompleted`, `TripCancelled` |
| notification-service | `matching-events` | `DriverReserved` |
| notification-service | `payment-events` | `PaymentSucceeded`, `PaymentFailed` |

> driver-service follows the same consumer rules as the other services: dedup via `processed_events` (§7), bounded retry (§8), then `trip-events.DLQ` (§6). Other event types on `trip-events` are ignored.

---

## 6. Dead Letter Queue

Messages that cannot be processed after maximum retries are moved to the DLQ topic.

### DLQ Topic Naming Convention

```
<original-topic>.DLQ
```

| Original Topic | DLQ Topic |
|---|---|
| `trip-events` | `trip-events.DLQ` |
| `matching-events` | `matching-events.DLQ` |
| `payment-events` | `payment-events.DLQ` |

### DLQ Message Format

```json
{
  "originalTopic": "trip-events",
  "originalPartition": 0,
  "originalOffset": 123,
  "originalMessage": {
    "eventId": "evt-005",
    "eventType": "TripCompleted",
    "...": "..."
  },
  "error": "NullPointerException: paymentId is null",
  "retryCount": 3,
  "firstFailedAt": "2026-09-30T10:25:01Z",
  "lastFailedAt": "2026-09-30T10:25:15Z",
  "correlationId": "corr-001"
}
```

### DLQ Monitoring

- DLQ message count metric: `kafka_dlq_messages_total{topic="payment-events.DLQ"}`
- Monitor in Grafana dashboard
- Manual replay can be triggered by re-publishing to original topic with same `eventId` (consumer deduplication handles already-processed events)

---

## 7. Deduplication

### Goal

Prevent duplicate side effects when Kafka delivers the same message more than once (at-least-once semantics).

### Mechanism

Each consumer service maintains a `processed_events` table:

```sql
CREATE TABLE processed_events (
    event_id   VARCHAR(255) PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP DEFAULT NOW()
);
```

### Processing Flow

```
Receive Kafka message
       v
SELECT COUNT(*) FROM processed_events WHERE event_id = :eventId
       v
eventId found? -> LOG "Duplicate detected, skipping" -> ACK message -> return
eventId NOT found? -> BEGIN TRANSACTION
       v
Execute business logic
       v
INSERT INTO processed_events (event_id, event_type) VALUES (:eventId, :eventType)
       v
COMMIT
       v
ACK Kafka message
```

### Services with Deduplication Tables

| Service | Database | Table |
|---|---|---|
| trip-service | `trip_db` | `processed_events` |
| matching-service | `matching_db` | `processed_events` |
| payment-service | `payment_db` | `processed_events` |
| driver-service | `driver_db` | `processed_events` |

> **Note:** Notification service is best-effort (no deduplication). Duplicate notifications are acceptable in demo scope.

---

## 8. Retry Strategy

### Consumer Retry (Application-level)

```
Message received -> processing fails
       v
Retry 1 (wait: 1 second)
       v
Retry 2 (wait: 2 seconds)
       v
Retry 3 (wait: 4 seconds)
       v
Max retries exceeded -> send to DLQ topic
```

| Parameter | Value |
|---|---|
| Max retries | 3 |
| Backoff type | Exponential |
| Initial delay | 1 second |
| Multiplier | 2.0 |
| Max delay | 30 seconds |

### Spring Kafka Configuration

```yaml
spring:
  kafka:
    consumer:
      enable-auto-commit: false
    listener:
      ack-mode: MANUAL_IMMEDIATE
```

### Non-Retryable Exceptions

The following exceptions skip retry and go directly to DLQ:
- `DuplicateEventException` – already processed (idempotency hit)
- `ValidationException` – malformed event, retry won't help

---

## 9. Saga Event Flow

Complete event flow for a successful ride:

```
[1] Passenger: POST /api/trips
        |
        v  (saga_log: STARTED)
[2] trip-service publishes: trip.requested -> trip-events
        |
        v
[3] matching-service consumes: trip.requested
        |  -> GET /api/drivers/nearby
        |  -> POST /api/drivers/{id}/reserve (CAS)
        v  (saga_log: not applicable for matching-service)
[4] matching-service publishes: driver.reserved -> matching-events
        |
        v  (saga_log: DRIVER_RESERVED)
[5] trip-service consumes: driver.reserved
        |  -> Trip status: SEARCHING -> ASSIGNED
        v
[6] trip-service publishes: driver.assigned -> trip-events
        |
        v
[6a] driver-service consumes: driver.assigned
        |  -> Deduplication check
        |  -> Driver status: RESERVED -> BUSY (clear reserved_until)
        v
[7] notification-service consumes all above events (fan-out)
        |
        v
[8] Driver: POST /api/trips/{id}/start
        |  -> Trip status: ASSIGNED -> IN_PROGRESS
        v
[9] Driver: POST /api/trips/{id}/complete
        |  -> Trip status: IN_PROGRESS -> COMPLETED  (saga_log: TRIP_COMPLETED)
        v
[10] trip-service publishes: trip.completed -> trip-events
        |
        +--> [10a] driver-service consumes: trip.completed  (in parallel with [11])
        |          -> Deduplication check
        |          -> Driver status: BUSY -> AVAILABLE (clear reserved_for_trip)
        |          -> Not affected by the payment result
        v
[11] payment-service consumes: trip.completed
        |  -> Deduplication check
        |  -> Process charge
        v
[12] payment-service publishes: payment.success -> payment-events
        |
        v  (saga_log: SAGA_COMPLETED)
[13] trip-service consumes: payment.success
        |  -> Trip status: COMPLETED -> PAID
        v
[14] notification-service consumes: payment.success
        |  -> Notify passenger + driver
```

---

## 10. Correlation ID Propagation

The `correlationId` must be included in every event payload and HTTP header throughout the business flow.

### Origin

The `correlationId` is generated by Trip Service when the passenger creates a ride request:

```
correlationId = UUID.randomUUID()  // generated once per business flow
```

### Propagation Rules

| Layer | How to propagate |
|---|---|
| HTTP Request (inbound) | Read from `X-Correlation-ID` header; generate if absent |
| HTTP Response | Echo in `X-Correlation-ID` header |
| Kafka Event | Embed in envelope `correlationId` field |
| Log entries | Include in every log line as `correlationId` field |

### Example Trace

```
Request: POST /api/trips  [X-Correlation-ID: corr-001]
  -> trip.requested       [correlationId: corr-001]
    -> driver.reserved    [correlationId: corr-001]
      -> driver.assigned  [correlationId: corr-001]
        -> trip.completed [correlationId: corr-001]
          -> payment.success [correlationId: corr-001]

# Query all logs for a single ride:
grep correlationId=corr-001 *.log
```

### Prometheus Label

Include `correlationId` in trace-level metrics for distributed tracing integration:

```
http_request_duration_seconds{service="trip-service", correlation_id="corr-001"}
```
