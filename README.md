# Shoe Commerce

Spring Boot modular-monolith and Vue SPA for a deliberately limited shoe-commerce MVP. It keeps pricing, checkout, inventory, payment, pickup, cash POS, and reporting authoritative on the server.

## Implemented

- Customer catalog, server-authoritative VND price quotes, idempotent online checkout, and location-aware reservations.
- Verified VNPAY callback boundary, pickup fulfillment, confirmed cancellation with financial-void reconciliation, and limited exact-cash POS.
- SQL Server-backed inventory concurrency and read-only reconciliation reporting.

The system is designed and tested around modeled concurrent business races; it is not a production-scale or certified payment deployment.

## Architecture

```text
Vue SPA -> REST API -> application/domain modules -> SQL Server
                         |       |       |
                      Pricing Inventory Payment/POS
                         \       |       /
                         Order + Pickup

Reporting reads immutable transaction facts; it does not write commerce state.
```

One `ProductVariant`, Pricing, Order, and location-aware `InventoryBalance` authority is shared by online checkout and POS. `available = onHand - reserved`; quote expiry is separate from reservation expiry; browser returns do not prove payment.

Key decisions: [pricing and checkout](docs/ADR/0020-quote-checkout-atomic-reservation.md), [reservation expiry](docs/ADR/0021-independent-checkout-reservation-expiry.md), [VNPAY verification](docs/ADR/0022-vnpay-verified-payment-and-inventory-commitment.md), [pickup/cancellation](docs/ADR/0023-pickup-handover-cancellation-and-financial-void.md), [POS](docs/ADR/0024-limited-cash-pos-and-cross-channel-inventory.md), and [reporting](docs/ADR/0025-authoritative-reporting-and-reconciliation.md).

## Run locally

Requirements: **Java 25** (a lower JDK fails with `release version 25 not supported`), a reachable SQL Server database, and Node.js/npm for the Vue app.

```powershell
$env:SPRING_DATASOURCE_URL = 'jdbc:sqlserver://localhost:1433;databaseName=shoe_commerce;encrypt=true;trustServerCertificate=true'
$env:SPRING_DATASOURCE_USERNAME = '<sql-user>'
$env:SPRING_DATASOURCE_PASSWORD = '<sql-password>'
cd backend; mvn spring-boot:run -Dspring-boot.run.profiles=local
```

```powershell
cd frontend; npm install; npm run dev
```

Flyway owns the schema. `V1`–`V15` are the current frozen baseline; Hibernate validates rather than creates tables.

### Disposable local demo

Use the explicit `demo` profile only with a fresh disposable database. It creates no demo data in the default or `local` profile.

```powershell
$env:SPRING_PROFILES_ACTIVE = 'demo'
cd backend; mvn spring-boot:run
```

The bootstrap is idempotent and creates one branch/location/register, two published `Court Classic` variants (`DEMO-CC-BLK-40`, `DEMO-CC-BLK-41`) at 125000 VND with three units each, and the demo identities documented in [docs/DEMO.md](docs/DEMO.md). It never creates orders, quotes, payments, voids, POS sales, or report totals.

Optional VNPAY configuration: `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, `VNPAY_PAY_URL`, `VNPAY_API_URL`, `VNPAY_RETURN_URL`, `VNPAY_FRONTEND_RESULT_URL`, `VNPAY_REFUND_CREATE_BY`, and `VNPAY_SERVER_IP`. `CHECKOUT_RESERVATION_TTL`, `SESSION_COOKIE_SECURE`, and `BCRYPT_STRENGTH` are also configurable. Do not commit their values.

## Verify

```powershell
cd backend; mvn verify
cd frontend; npm run typecheck; npm test; npm run build
```

The backend acceptance suite requires SQL Server/Testcontainers support. See [docs/DEMO.md](docs/DEMO.md) for the evidence-based demo and external-payment limits.

## Intentional non-goals

Voucher workflows, shipping/delivery, returns/refunds, multiple payment providers, multi-line POS, inventory valuation, Redis, Kafka, microservices, and Kubernetes are outside this Core MVP.

## External-payment evidence

VNPAY 2.1.0 integration is implemented and protocol/integration tested. The `demo` profile uses an isolated deterministic payment/void adapter to validate application orchestration; it is not VNPAY certification. Real merchant sandbox payment and full reversal require merchant credentials plus a public HTTPS callback.
