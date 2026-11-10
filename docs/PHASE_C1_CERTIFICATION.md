# Phase C1 Certification

Status: **PASS — PHASE C1 CERTIFIED**

## Environment

- Repository HEAD before certification: `9bdb87df476eea48d937dd7947bce950be361417`
- Git state: working tree contains the reviewed Phase A, Phase B, and Phase C1 implementation, tests, ADRs, and certification documents; `git diff --check` passed.
- Java: Oracle Java 25.0.4 LTS, 64-bit.
- Maven: 3.9.11.
- Windows/process architecture: 64-bit/64-bit.
- SQL Server: `localhost\AN`, SQL Server 16.0.1190.2 Developer Edition, 64-bit.
- Windows and JDBC-authenticated principal: `AN\asus`; database user `dbo`.
- JDBC: Microsoft JDBC Driver 13.4.0 (`mssql-jdbc-13.4.0.jre11`).
- Native authentication: official Microsoft `mssql-jdbc_auth-13.4.0.x64.dll`, valid Microsoft Authenticode signature, loaded through a temporary process-local `java.library.path`. Distribution ZIP SHA-256: `E1974FA3CDE9071877D2CE4533003E06E0E2CCEF6FE33F7B5916E47354EA38FF`.
- No credential was committed and no SQL login, server role, grant, or SQL Server security configuration was created or changed.

## Database acceptance

- The authenticated principal connected through Windows Integrated Authentication and had existing `CREATE ANY DATABASE`, `sysadmin`, and `dbcreator` capability. Acceptance made no security changes.
- Fresh V1→V22: PASS. All 22 migrations applied exactly once; V22 tables, GSO-2024 references, order/quote money columns, and `SHIPPING_RATE_MANAGE` were present; `pickup_fulfillment.delivery_fee_amount` was absent; Hibernate schema validation passed.
- Populated V21→V22: PASS. A database at exactly V21 containing a real order, order item, reservation, and pickup fulfillment applied exactly one migration. The historical order became `merchandise_amount=900000`, `shipping_fee_amount=0`, `total_amount=900000`; the fulfillment survived unchanged; V22 objects and permission appeared once.

## C1 behavior

- Shipping rule resolution: PASS for all six specificity levels and higher priority within equal specificity.
- Ambiguity and concurrent publication: PASS; two conflicting serializable publications allowed at most one commit.
- Lifecycle: published revisions are immutable; retired quote revisions remain usable; invalidated revisions return `SHIPPING_QUOTE_STALE`.
- Origin consistency: PASS for deterministic quote/checkout origin, stock loss, disabled Location, and disabled Branch; checkout returns conflict and never switches origin silently.
- Money: PASS for pickup, local/core, outer-area, inter-province, supported VND maximum, and shipping-induced overflow. Quote merchandise plus shipping equals quote total, order total, and payment amount. Fulfillment reads the order snapshot and no second delivery-fee column remains.
- Authorization: PASS for exact Location allow/foreign Location deny, global two-permission allow, each insufficient permission set deny, and Branch administration deny.
- Destination references: PASS for stable codes, province/district relationship, enabled references, labels/free text not acting as matching keys, and deterministic GSO-2024 seed.

## Regression results

- Mandatory SQL Server profile: 26 suites total (7 unit and 19 integration), 145 tests total (20 unit and 125 integration), 0 failures, 0 errors, 0 skips, Maven `BUILD SUCCESS`, 2 minutes 42 seconds.
- Frontend: 9 files and 111 tests passed; `vue-tsc -b` passed; Vite production build passed.
- Browser: delivery showed `2,980,000 + 80,000 = 3,060,000 VND`; province change reset district; unsupported destination showed a customer-safe message; disabled quoted origin showed stale/conflict recovery without rerouting; pickup showed a zero shipping fee and retained Location selection.
- Shipping administration: Location draft/edit/publish/new revision/retire flows passed; published rows were immutable; invalidation control and warning were exposed; global authority denial and allowance passed.
- Responsive: Cart/Checkout and Shipping Rules passed at 1440, 1024, 768, and 390 CSS pixels with no document-level horizontal overflow. The revision table scrolls inside its container at 768 and 390.

## Fixture review and cleanup

- `FlywayV19UpgradeExternalIT.java` is a genuine Phase B migration fixture change from V19→V20 to V20→V21, including `STAFF_MANAGE_SCOPED` preservation. It is unrelated to the acceptance environment and was retained; its historical filename was not renamed during C1.
- Temporary databases, JDBC distribution/DLL, process-local environment, demo servers, and temporary logs were removed after certification. No user demo/development database was targeted.
