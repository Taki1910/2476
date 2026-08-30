# ADR-0001: Use a Modular Monolith

- Status: `ACCEPTED`
- Accepted: 2026-08-24
- MVP scope: Core architecture baseline
- Date: 2026-08-23
- Decision class: Safe to freeze after independent review
- Decision source: repository governance plus Blueprint v1.1 analysis

## Context

The platform spans many business boundaries, but no confirmed requirement
justifies independent services, distributed transactions, service discovery,
or broker-operated workflows.

## Decision

Build one modular backend deployment with explicit domain/application/
infrastructure boundaries. Use one SQL Server database initially while keeping
schema and code ownership clear enough for later extraction if justified.

## Consequences

- Cross-domain consistency can use local database transactions where valid.
- Modules cannot bypass ownership through direct cross-module repository writes.
- Microservices, brokers, and distributed transaction infrastructure require a
  later ADR backed by measured or contractual need.

## Rejected alternatives

- Copying the package-by-Spring-layer legacy monolith.
- Microservices-by-domain from day one.
