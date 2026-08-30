# ADR-0023: Pickup Handover, Confirmed Cancellation and Financial Void

- Status: ACCEPTED
- Date: 2026-08-27
- Decision class: Fulfillment, inventory and payment transaction semantics
- Supersedes: the pre-v10 on-hand restoration wording in ADR-0010, `BR-CAN-102`,
  `DOMAIN_MODEL.md` and `LIFECYCLES.md`

## Context

ADR-0022 established that verified payment changes the Order to `PAID` and its
Reservation to `COMMITTED` without changing `onHand` or `reserved`. Older
cancellation drafts assumed payment had already issued stock and therefore said
that cancellation increased `onHand`. Applying that older delta to the v10 model
would create phantom stock.

The pickup terminal commands also compete for the same physical unit, while a
VNPAY full reversal is an external, potentially ambiguous financial operation.

## Decision

Pickup preparation requires a paid Order, a committed Reservation,
`FULFILL_PICKUP`, and exact active Location scope. It changes only Fulfillment to
`PREPARED`.

Handover and confirmed cancellation both lock:

```text
rank 0 command identity
-> rank 1 Order
-> rank 2 PickupFulfillment
```

Only one may cross the Fulfillment terminal fence.

Handover then locks Reservation and Balance and atomically applies:

```text
onHand   -= quantity
reserved -= quantity
Reservation COMMITTED -> CONSUMED
Fulfillment PREPARED -> HANDED_OVER
append PICKUP_HANDOVER
```

Confirmed cancellation locks Payment and reversal children before Inventory,
reserves the immutable captured `ORDER_ITEM` component under RR-01, then applies:

```text
onHand   unchanged
reserved -= quantity
Reservation COMMITTED -> CANCELLED_RESTORED
Fulfillment -> CANCELLED
Order PAID -> CANCELLED
append CANCELLATION_RESTORE
```

`committedAt` remains immutable historical evidence in both terminal Reservation
paths. SQL uniqueness allows one movement of each mutually exclusive type per
Order, while the Fulfillment row lock prevents both types from being created.

The domain operation is `VOID`. The VNPAY adapter maps it to `vnp_Command=refund`
and `vnp_TransactionType=02`, signs the provider-defined pipe-delimited request
with HMAC-SHA512, and sends exact VND multiplied by 100. The provider call occurs
only after the local cancellation transaction commits. A separate result
transaction changes every allocation for that attempt together:

- definitive transaction status `00`: `ACTIVE -> SUCCEEDED`;
- definitive failure: `ACTIVE -> RELEASED`;
- processing, unauthenticated, timed-out or otherwise unknown: remains `ACTIVE`;
- suspicious/unrecognized evidence: review required and remains `ACTIVE`.

`vnp_ResponseCode=00` only says the API request was processed; it is not proof
of reversal success without the separate transaction status.

For the current single-item, quantity-one, full-capture MVP:

```text
successfulVoid(c) + activeVoid(c) <= capturedComponentCapacity(c)
```

The broader approved RR-01 equation still includes Refund allocations, although
the Return/Refund product workflow is not implemented in this slice.

## Consequences

- Cancellation remains operationally final even when financial reversal is
  unknown, failed or under review; handover can never reopen.
- Provider retry never repeats inventory restoration.
- An unknown attempt retains capacity and blocks blind retry.
- A definitive failure preserves immutable evidence and may be retried only as a
  new attempt generation under the same logical Void after capacity is reacquired.
- No provider network call holds SQL locks.

## Rejected alternatives

- Increasing `onHand` on cancellation: creates phantom stock under ADR-0022.
- Consuming stock at payment time: reverses the approved v10 boundary.
- Treating response code `00` as financial success: confuses API acceptance with
  the provider transaction state.
- A JVM mutex or frontend button fence: cannot protect concurrent SQL Server
  transactions or multiple application instances.
- Implementing Return/Refund, partial reversal or a scheduler in this slice.
