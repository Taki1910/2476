# Phase B certification

Certified on 2026-09-05 (Asia/Ho_Chi_Minh) against Git HEAD
`9bdb87df476eea48d937dd7947bce950be361417`.

## Acceptance environment

- Windows principal: `AN\asus` through Windows Integrated Authentication.
- SQL Server: SQL Server 2022 Developer 16.0.1190.2, instance `AN\AN`, x64.
- Java: 25.0.4, 64-bit (`amd64`).
- JDBC: Microsoft JDBC Driver 13.4.0.jre11 with the matching
  `mssql-jdbc_auth-13.4.0.x64.dll` from Microsoft's official 13.4.0 package.
- Native authentication was enabled only through a process-local
  `java.library.path`. No credential, native path, SQL login, role grant, or
  SQL Server security change was committed or persisted.

## Database acceptance

- Fresh migration: PASS. An empty SQL Server database applied V1 through V21;
  all 21 migrations ran once and Hibernate/JPA startup validation succeeded.
- Upgrade migration: PASS. The fixture built a populated V20 database, applied
  exactly V21, retained existing catalog data, and created
  `STAFF_MANAGE_SCOPED` exactly once.
- Mandatory acceptance profile: PASS. Maven reported 20 unit tests and 119 SQL
  Server integration tests, with 0 failures, 0 errors, and 0 skips. The full
  build completed in 2 minutes 21 seconds.
- Authorization coverage includes scoped staff listing/mutation, exact-location
  POS scope, capability grant/revoke, auth-version invalidation, and forbidden
  escalation paths.
- Fulfillment coverage includes the PENDING-to-PREPARED rejection, picking and
  preparation lifecycle, pickup handover, delivery dispatch/completion,
  cancellation races, recovery records, transaction rollback, and idempotency.
- Reporting regression coverage passed.

## Fixture corrections

`FlywayV19UpgradeExternalIT` remains named for compatibility but now exercises
the V20-to-V21 boundary. Its pre-upgrade shoe-fit assertion expects the V20
table to exist because V20 creates it. The test does not use a second datasource
environment variable.

`VnPayPaymentExternalIT` now expects `BusinessConflictException` when a caller
tries to prepare a pending fulfillment. This matches the application boundary's
stable conflict contract while preserving the domain transition rejection.

## Frontend and browser acceptance

- Vitest: 110/110 tests passed across 9 files.
- TypeScript typecheck: PASS.
- Production build: PASS.
- Real-browser checks: the delegated `manager.demo` account could open scoped
  People & Access, fulfillment recovery, and reporting navigation; the cashier
  saw only its assigned registers and direct People & Access navigation reached
  the controlled 403 page.
- Responsive checks at 1440, 1024, 768, and 390 CSS pixels showed no horizontal
  document overflow. The authenticated People & Access view was checked at
  1440, 1024, and 390; the 768 breakpoint was checked separately in the same
  built frontend.

## Cleanup and limits

- All timestamped Phase B databases and upgrade-fixture databases were removed.
- The downloaded/extracted JDBC distribution and process-local metadata were
  removed.
- No temporary SQL login remains and no SQL Server security setting was changed.
- The repository has no lint script, so lint remains an explicit tooling gap;
  tests, typecheck, and production build are the enforced frontend gates.

## Verdict

PHASE B CERTIFIED
