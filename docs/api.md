# API Reference

## Ride-Hailing Microservices System (DS03)

**Version:** 1.0.0  
**Date:** 01/10/2026  
**Base URL (via API Gateway):** `http://localhost:8080`  
**Authentication:** Bearer JWT (obtained from Identity Service login)

---

## Table of Contents

1. [Authentication](#1-authentication)
2. [Identity Service](#2-identity-service)
3. [Driver Service](#3-driver-service)
4. [Trip Service](#4-trip-service)
5. [Matching Service](#5-matching-service)
6. [Pricing Service](#6-pricing-service)
7. [Payment Service](#7-payment-service)
8. [Request / Response Conventions](#8-request--response-conventions)
9. [Error Codes](#9-error-codes)
10. [API Gateway Routing Table](#10-api-gateway-routing-table)

---

## 1. Authentication

All requests to backend services (except `/api/auth/register` and `/api/auth/login`) require:

```
Authorization: Bearer <accessToken>
```

**Token format:** JWT (HS256), issued by Identity Service.  
**Token expiry:** 24 hours (configurable via `JWT_EXPIRATION_MS`).

### Role-based Access

| Role | Permissions |
|---|---|
| `PASSENGER` | Create ride, cancel own ride, view own trip |
| `DRIVER` | Update own status, update own location, accept/reject/start/complete trip |

---

## 2. Identity Service

**Internal address:** `http://identity-service:8081`  
**Gateway prefix:** `/api/auth/**`, `/api/users/**`

---

### POST /api/auth/register

Register a new user.

**Auth required:** No

**Request Body:**
```json
{
  "fullName": "Nguyen Van A",
  "email": "user@example.com",
  "password": "securePassword123",
  "role": "PASSENGER"
}
```

| Field | Type | Required | Constraints |
|---|---|---|---|
| fullName | string | Yes | 1-100 chars |
| email | string | Yes | Valid email, unique |
| password | string | Yes | Min 8 chars |
| role | enum | Yes | `PASSENGER` or `DRIVER` |

**Response 201 Created:**
```json
{
  "id": "a1b2c3d4-...",
  "fullName": "Nguyen Van A",
  "email": "user@example.com",
  "role": "PASSENGER",
  "createdAt": "2026-09-30T10:00:00Z"
}
```

**Errors:**
- `400` – Validation error (missing field, invalid email format)
- `409` – Email already registered

---

### POST /api/auth/login

Authenticate and receive JWT.

**Auth required:** No

**Request Body:**
```json
{
  "email": "user@example.com",
  "password": "securePassword123"
}
```

**Response 200 OK:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400,
  "userId": "a1b2c3d4-...",
  "role": "PASSENGER"
}
```

**Errors:**
- `400` – Missing fields
- `401` – Invalid credentials

---

### GET /api/users/{id}

Get user profile.

**Auth required:** Yes (any authenticated user)

**Path Parameters:**

| Param | Type | Description |
|---|---|---|
| id | UUID | User ID |

**Response 200 OK:**
```json
{
  "id": "a1b2c3d4-...",
  "fullName": "Nguyen Van A",
  "email": "user@example.com",
  "role": "PASSENGER",
  "createdAt": "2026-09-30T10:00:00Z"
}
```

**Errors:**
- `401` – Missing or invalid JWT
- `404` – User not found

---

## 3. Driver Service

**Internal address:** `http://driver-service:8082`  
**Gateway prefix:** `/api/drivers/**`

---

### GET /api/drivers/{id}

Get driver details.

**Auth required:** Yes

**Response 200 OK:**
```json
{
  "id": "d001",
  "userId": "a1b2c3d4-...",
  "fullName": "Tran Van B",
  "status": "AVAILABLE",
  "vehicle": {
    "licensePlate": "29A-12345",
    "model": "Toyota Vios",
    "color": "White"
  },
  "location": {
    "latitude": 21.0285,
    "longitude": 105.8542,
    "updatedAt": "2026-09-30T10:05:00Z"
  }
}
```

---

### GET /api/drivers/available

List all AVAILABLE drivers.

**Auth required:** Yes (PASSENGER or internal)

**Query Parameters:**

| Param | Type | Required | Description |
|---|---|---|---|
| latitude | double | No | Reference point latitude |
| longitude | double | No | Reference point longitude |
| radiusKm | double | No | Search radius in km (default: 5.0) |

**Response 200 OK:**
```json
[
  {
    "id": "d001",
    "fullName": "Tran Van B",
    "status": "AVAILABLE",
    "location": {
      "latitude": 21.0285,
      "longitude": 105.8542
    },
    "distanceKm": 1.2
  }
]
```

---

### GET /api/drivers/nearby

Query nearby AVAILABLE drivers (spatial search).

**Auth required:** Yes (PASSENGER or Matching Service internally)

**Query Parameters:**

| Param | Type | Required | Description |
|---|---|---|---|
| latitude | double | Yes | Pickup latitude |
| longitude | double | Yes | Pickup longitude |
| radiusKm | double | No | Radius in km (default: 5.0) |
| limit | int | No | Max results (default: 10) |

**Response 200 OK:**
```json
[
  {
    "id": "d001",
    "fullName": "Tran Van B",
    "distanceKm": 0.8,
    "location": {
      "latitude": 21.029,
      "longitude": 105.855
    }
  }
]
```

---

### PATCH /api/drivers/{id}/status

Update driver availability status.

**Auth required:** Yes (DRIVER role, own resource)

**Request Body:**
```json
{
  "status": "AVAILABLE"
}
```

**Allowed transitions:**

| From | To |
|---|---|
| OFFLINE | AVAILABLE |
| AVAILABLE | OFFLINE |
| AVAILABLE | BUSY (set by system) |
| BUSY | AVAILABLE |
| RESERVED | AVAILABLE (compensation) |

**Response 200 OK:**
```json
{
  "id": "d001",
  "status": "AVAILABLE",
  "updatedAt": "2026-09-30T10:10:00Z"
}
```

**Errors:**
- `400` – Invalid status transition
- `403` – Not own resource

---

### PATCH /api/drivers/{id}/location

Update driver GPS location (simulated).

**Auth required:** Yes (DRIVER role, own resource)

**Request Body:**
```json
{
  "latitude": 21.0300,
  "longitude": 105.8560
}
```

**Response 200 OK:**
```json
{
  "id": "d001",
  "location": {
    "latitude": 21.0300,
    "longitude": 105.8560,
    "updatedAt": "2026-09-30T10:11:00Z"
  }
}
```

---

### POST /api/drivers/{id}/reserve

Reserve a driver (CAS lock with 30s lease). Used internally by Matching Service.

**Auth required:** Yes (internal service call)

**Request Body:**
```json
{
  "tripId": "trip-001",
  "expectedVersion": 5
}
```

**Response 200 OK:**
```json
{
  "driverId": "d001",
  "status": "RESERVED",
  "reservedForTrip": "trip-001",
  "reservedUntil": "2026-09-30T10:12:30Z",
  "version": 6
}
```

**Errors:**
- `409` – Driver already reserved (CAS failed – affectedRows = 0)

---

### DELETE /api/drivers/{id}/reserve

Release driver reservation (compensating action). Returns driver to AVAILABLE.

**Auth required:** Yes (internal service call)

**Response 204 No Content**

---

## 4. Trip Service

**Internal address:** `http://trip-service:8083`  
**Gateway prefix:** `/api/trips/**`

---

### POST /api/trips

Create a ride request. Triggers matching flow.

**Auth required:** Yes (PASSENGER role)

**Request Body:**
```json
{
  "pickup": {
    "latitude": 21.0285,
    "longitude": 105.8542,
    "address": "Hoan Kiem, Hanoi"
  },
  "destination": {
    "latitude": 21.0188,
    "longitude": 105.8520,
    "address": "Dong Da, Hanoi"
  }
}
```

**Response 201 Created:**
```json
{
  "tripId": "trip-001",
  "status": "SEARCHING",
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
  "correlationId": "corr-001",
  "createdAt": "2026-09-30T10:00:00Z"
}
```

Side effects:
- Publishes `trip.requested` event to Kafka
- Saga step: `STARTED` written to `saga_log`

---

### GET /api/trips/{id}

Get trip details.

**Auth required:** Yes

**Response 200 OK:**
```json
{
  "tripId": "trip-001",
  "status": "ASSIGNED",
  "passengerId": "passenger-001",
  "driverId": "d001",
  "pickup": { "latitude": 21.0285, "longitude": 105.8542 },
  "destination": { "latitude": 21.0188, "longitude": 105.8520 },
  "estimatedFare": 45000,
  "finalFare": null,
  "correlationId": "corr-001",
  "createdAt": "2026-09-30T10:00:00Z",
  "updatedAt": "2026-09-30T10:02:00Z"
}
```

---

### POST /api/trips/{id}/accept

Driver accepts assigned trip.

**Auth required:** Yes (DRIVER role)

**Response 200 OK:**
```json
{
  "tripId": "trip-001",
  "status": "ASSIGNED",
  "driverId": "d001",
  "updatedAt": "2026-09-30T10:03:00Z"
}
```

---

### POST /api/trips/{id}/start

Driver starts the trip (picks up passenger).

**Auth required:** Yes (DRIVER role)

**Response 200 OK:**
```json
{
  "tripId": "trip-001",
  "status": "IN_PROGRESS",
  "startedAt": "2026-09-30T10:05:00Z"
}
```

---

### POST /api/trips/{id}/complete

Driver completes the trip (arrives at destination).

**Auth required:** Yes (DRIVER role)

**Response 200 OK:**
```json
{
  "tripId": "trip-001",
  "status": "COMPLETED",
  "finalFare": 47500,
  "completedAt": "2026-09-30T10:25:00Z"
}
```

Side effects:
- Publishes `trip.completed` event to Kafka
- Payment Service processes payment asynchronously

---

### POST /api/trips/{id}/cancel

Cancel a trip. Valid when status is `SEARCHING` or `ASSIGNED`.

**Auth required:** Yes (PASSENGER role, own trip)

**Request Body (optional):**
```json
{
  "reason": "Changed plans"
}
```

**Response 200 OK:**
```json
{
  "tripId": "trip-001",
  "status": "CANCELLED",
  "cancelledAt": "2026-09-30T10:01:30Z"
}
```

Side effects:
- Compensation: `DELETE /api/drivers/{driverId}/reserve` (if driver was RESERVED/ASSIGNED)
- Publishes `trip.cancelled` event to Kafka

---

## 5. Matching Service

**Internal address:** `http://matching-service:8084`  
**Gateway prefix:** `/api/matching/**`

> Note: Matching is primarily driven by Kafka events. REST endpoints exist for manual trigger and testing.

---

### POST /api/matching/{requestId}/match

Manually trigger matching for a ride request.

**Auth required:** Yes (internal or admin)

**Response 202 Accepted:**
```json
{
  "requestId": "trip-001",
  "status": "MATCHING",
  "message": "Matching process initiated"
}
```

---

### POST /api/matching/{requestId}/rematch

Trigger rematch when initial driver rejected.

**Auth required:** Yes (internal)

**Response 202 Accepted:**
```json
{
  "requestId": "trip-001",
  "status": "REMATCHING",
  "attempt": 2
}
```

---

## 6. Pricing Service

**Internal address:** `http://pricing-service:8085`  
**Gateway prefix:** `/api/pricing/**`

> Note: Pricing Service is stateless. No database. Called synchronously by Trip Service.

---

### POST /api/pricing/quote

Calculate estimated fare for a trip.

**Auth required:** Yes

**Request Body:**
```json
{
  "pickup": {
    "latitude": 21.0285,
    "longitude": 105.8542
  },
  "destination": {
    "latitude": 21.0188,
    "longitude": 105.8520
  }
}
```

**Response 200 OK:**
```json
{
  "estimatedDistanceKm": 2.5,
  "estimatedDurationMin": 12,
  "baseFare": 15000,
  "distanceFare": 12500,
  "timeFare": 6000,
  "totalFare": 33500,
  "currency": "VND"
}
```

**Fare formula:**
```
total = baseFare + (distanceKm * distanceRate) + (durationMin * timeRate)
```

---

### POST /api/pricing/final

Calculate final fare after trip completion (actual distance/duration).

**Auth required:** Yes (internal)

**Request Body:**
```json
{
  "tripId": "trip-001",
  "actualDistanceKm": 2.8,
  "actualDurationMin": 15
}
```

**Response 200 OK:**
```json
{
  "tripId": "trip-001",
  "baseFare": 15000,
  "distanceFare": 14000,
  "timeFare": 7500,
  "totalFare": 36500,
  "currency": "VND"
}
```

---

## 7. Payment Service

**Internal address:** `http://payment-service:8086`  
**Gateway prefix:** `/api/payments/**`

> Note: Payment is triggered primarily via Kafka (`trip.completed` event). REST endpoints allow manual trigger and status check.

---

### POST /api/payments/{tripId}/charge

Initiate payment charge for a completed trip.

**Auth required:** Yes (internal)

**Request Body:**
```json
{
  "passengerId": "passenger-001",
  "amount": 36500,
  "currency": "VND",
  "idempotencyKey": "trip-001-charge-001"
}
```

> `idempotencyKey` prevents duplicate charges on retry.

**Response 201 Created:**
```json
{
  "paymentId": "pay-001",
  "tripId": "trip-001",
  "status": "SUCCESS",
  "amount": 36500,
  "currency": "VND",
  "processedAt": "2026-09-30T10:26:00Z"
}
```

**Errors:**
- `409` – Duplicate idempotency key (already processed)
- `402` – Simulated payment failure

---

### POST /api/payments/{tripId}/refund

Refund a payment (compensating action for cancellation after charge).

**Auth required:** Yes (internal)

**Response 200 OK:**
```json
{
  "paymentId": "pay-001",
  "tripId": "trip-001",
  "status": "REFUNDED",
  "refundedAt": "2026-09-30T10:30:00Z"
}
```

---

### GET /api/payments/{tripId}

Get payment status for a trip.

**Auth required:** Yes

**Response 200 OK:**
```json
{
  "paymentId": "pay-001",
  "tripId": "trip-001",
  "status": "SUCCESS",
  "amount": 36500,
  "currency": "VND",
  "processedAt": "2026-09-30T10:26:00Z"
}
```

**Payment Status Values:**

| Status | Description |
|---|---|
| `PENDING` | Payment initiated, not yet processed |
| `PROCESSING` | Being processed |
| `SUCCESS` | Payment captured successfully |
| `FAILED` | Payment failed |
| `REFUNDED` | Payment refunded |

---

## 8. Request / Response Conventions

### Request Headers

| Header | Required | Description |
|---|---|---|
| `Authorization` | Yes (for protected routes) | `Bearer <jwt_token>` |
| `Content-Type` | Yes (for POST/PATCH) | `application/json` |
| `X-Correlation-ID` | No (auto-generated if absent) | Trace ID for distributed tracing |
| `X-Request-ID` | No | Unique per-request ID |
| `X-Idempotency-Key` | Required for payment charge | Prevents duplicate operations |

### Pagination (for list endpoints)

```
GET /api/drivers/available?page=0&size=10&sort=distanceKm,asc
```

### Timestamps

All timestamps are ISO 8601 UTC:

```
"2026-09-30T10:00:00Z"
```

### Common Response Wrapper

Successful responses return the resource directly (no wrapper envelope).

Error responses follow:

```json
{
  "timestamp": "2026-09-30T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Email already registered",
  "path": "/api/auth/register",
  "correlationId": "corr-001"
}
```

---

## 9. Error Codes

| HTTP Status | Meaning | Common Causes |
|---|---|---|
| 200 | OK | Successful GET, PATCH, DELETE |
| 201 | Created | Successful POST (resource created) |
| 202 | Accepted | Async operation initiated |
| 204 | No Content | Successful DELETE with no body |
| 400 | Bad Request | Validation failure, missing fields, invalid enum |
| 401 | Unauthorized | Missing or expired JWT |
| 403 | Forbidden | Valid JWT but insufficient role |
| 404 | Not Found | Resource does not exist |
| 409 | Conflict | Duplicate email, duplicate idempotency key, CAS conflict |
| 402 | Payment Required | Simulated payment failure |
| 422 | Unprocessable Entity | Business rule violation (e.g., invalid state transition) |
| 500 | Internal Server Error | Unexpected server error |
| 503 | Service Unavailable | Downstream service unreachable |

---

## 10. API Gateway Routing Table

All client requests enter via `http://localhost:8080`.

| Gateway Path | Target Service | Target URL |
|---|---|---|
| `/api/auth/**` | identity-service | `http://identity-service:8081/api/auth/**` |
| `/api/users/**` | identity-service | `http://identity-service:8081/api/users/**` |
| `/api/drivers/**` | driver-service | `http://driver-service:8082/api/drivers/**` |
| `/api/trips/**` | trip-service | `http://trip-service:8083/api/trips/**` |
| `/api/matching/**` | matching-service | `http://matching-service:8084/api/matching/**` |
| `/api/pricing/**` | pricing-service | `http://pricing-service:8085/api/pricing/**` |
| `/api/payments/**` | payment-service | `http://payment-service:8086/api/payments/**` |
| `/actuator/health` | api-gateway | (local) |

> No `lb://` scheme is used. Service discovery is via Docker DNS only.
