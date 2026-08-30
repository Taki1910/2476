# ADR-0008: Keep AI Behind a Proposal and Approval Boundary

- Status: `ACCEPTED`
- Accepted: 2026-08-24
- MVP scope: AI implementation deferred
- Date: 2026-08-23
- Decision class: Safe boundary; provider/capabilities remain deferred
- Decision source: repository governance plus Blueprint v1.1 analysis

## Context

AI may assist customers and administrators but is non-deterministic and may
process sensitive inputs. It cannot own transactional correctness.

## Decision

AI returns structured proposals/drafts through an adapter. Schema and business
validation precede preview and authorized approval. Existing deterministic use
cases perform publication or business changes. AI receives no direct mutation,
repository, or SQL authority.

## Consequences

- AI failure degrades to ordinary commerce UX and never blocks checkout.
- Provider selection requires a later ADR covering quality, privacy, security,
  cost, retention, fallback, and exit strategy.
- AI-specific infrastructure is deferred until a confirmed use case proves it
  necessary.

## Rejected alternatives

- Generated executable production pages.
- AI-issued discounts, refunds, inventory changes, or permission grants.
