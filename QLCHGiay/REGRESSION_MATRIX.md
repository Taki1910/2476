# Regression matrix

Status meanings:

- **Automated pass**: covered by the Maven test suite.
- **Code/DB verified; manual pending**: implementation or live data was inspected, but a human still needs to exercise the rendered UI.
- **Manual pending**: requires browser interaction and has not been claimed as passed.
- **N/A**: the conditional scenario does not apply to the current schema.

## Product / Variant

| # | Scenario | Status / evidence |
|---:|---|---|
| 1 | Multiple variants group into one row | Automated pass — `DashboardControllerTest` |
| 2 | Same name does not merge when a stronger identity exists | N/A — schema has no product root/model key |
| 3 | Group-by-name limitation documented | Code/DB verified — normalized name is an explicitly documented compatibility key, not a domain identity |
| 4 | Multiple colors | Code/DB verified; manual pending — 7 colors in live DB |
| 5 | Multiple sizes | Code/DB verified; manual pending — 6 sizes in live DB |
| 6 | Mixed active/inactive | Automated pass — explicit `mixed` group status |
| 7 | Different variant prices | Automated pass — min/max price range |
| 8 | Detail shows correct variants | Code verified; manual pending — representative ID resolves variants by normalized name |
| 9 | Pagination uses grouped rows/count | Code verified; manual pending — filter/sort runs before 10-row slice |
| 10 | Sort after grouping | Automated pass — total group stock/default order |
| 11 | Search after grouping | Code verified; manual pending |

## Filter

| # | Scenario | Status / evidence |
|---:|---|---|
| 12 | Color | Code verified; manual pending |
| 13 | Size | Code verified; manual pending |
| 14 | Color + size match the same variant | Automated contract pass; manual pending — `.some()` over variant records |
| 15 | Stock | Code verified; manual pending — evaluated on the same variant |
| 16 | Active/inactive | Code verified; manual pending — explicit group `mixed` plus per-variant active state |
| 17 | Remove one filter | Automated contract pass; manual pending |
| 18 | Clear all | Automated contract pass; manual pending |
| 19 | Price cycle none → asc → desc → none | Automated contract pass; manual pending |
| 20 | Refresh starts in a valid state | Code verified; manual pending — server render initializes no stale client state |

## Product form

| # | Scenario | Status / evidence |
|---:|---|---|
| 21 | Existing color | Automated pass — batch/create controller tests |
| 22 | Inline color | Automated pass — normalized inline color test |
| 23 | Form data retained after client interaction | Manual pending |
| 24 | Inline size | Code verified; manual pending |
| 25 | Reject size 0 | Backend rule verified; manual/crafted-request retest pending |
| 26 | Reject negative size | Backend rule verified; manual/crafted-request retest pending |
| 27 | Reject decimal size | Automated pass |
| 28 | Reject equivalent duplicate size/variant | Automated pass — duplicate variant is rejected before write |
| 29 | Reject missing image for active product | Automated pass |
| 30 | Reject price 0 | Backend rule verified |
| 31 | Reject negative price | Backend rule verified |
| 32 | Reject price not divisible by 1,000 | Automated pass |
| 33 | Accept 2,690,000 | Backend rule verified; positive stepped prices pass |
| 34 | Frontend bypass still rejected by backend | Automated pass — controller-level validation tests |

## Customer / Invoice

| # | Scenario | Status / evidence |
|---:|---|---|
| 35 | Walk-in checkout | Automated pass |
| 36 | Stored-customer checkout | Automated pass |
| 37 | Walk-in needs no fake name/phone | Automated pass — null customer, `Khách lẻ` snapshot |
| 38 | Customer stats exclude walk-in | Code/DB verified — no customer row is created |
| 39 | Invoice remains traceable | Automated pass — invoice ID and snapshots retained |
| 40 | Stock reservation | Automated pass |
| 41 | Payment flow | Automated pass — stock, price quote, payment and snapshots |

## Report

| # | Scenario | Status / evidence |
|---:|---|---|
| 42 | Rolling 6 months | Automated pass |
| 43 | Rolling 12 months | Automated pass |
| 44 | Current year | Automated pass |
| 45 | Cross-year | Automated pass — 09/2025 → 08/2026 fixture |
| 46 | Zero-revenue month | Automated pass |
| 47 | Unpaid excluded | Automated pass |
| 48 | Chart/table/total consistent | Backend automated pass; rendered chart/table manual pending |

## Supplier

| # | Scenario | Status / evidence |
|---:|---|---|
| 49 | No fake KPI | Automated template contract pass |
| 50 | No warehouse subsystem added | Code verified |

## Promotion

| # | Scenario | Status / evidence |
|---:|---|---|
| 51 | Accept integer percentage | Automated pass |
| 52 | Reject decimal percentage | Automated pass |
| 53 | Reject non-positive value | Automated pass |
| 54 | Reject percentage above 100 | Automated pass |
| 55 | Fixed discount is integer money | Automated pass + trusted DB CHECK |
| 56 | Invoice history survives promotion edits | Automated pass — historical snapshot test |

## Notification

| # | Scenario | Status / evidence |
|---:|---|---|
| 57 | Persistent notification | Automated pass + live `ThongBao` schema |
| 58 | Survives logout/login | Repository-backed request reload automated; login UI manual pending |
| 59 | New session sees it | Automated pass — every request reloads by account ID |
| 60 | Mark read persists | Automated pass |
| 61 | Unread badge count | Repository query verified; rendered badge manual pending |
| 62 | Employee cannot see admin-only notification | User-recipient isolation verified; no admin-only event type is emitted yet |
| 63 | User A cannot see user B notification | Automated pass — ownership included in read/query path |
| 64 | No infinite duplicates | Automated pass + unique `(recipient, dedupeKey)` constraint |

## Manual retest batch

Run this batch in the browser before release:

1. Product list: grouping, page size/last page, search, all filter combinations, chip removal, clear-all, price cycle, refresh, detail navigation and inactive styling.
2. Product form: inline color/size state retention, formatted money typing with Backspace/Delete, image upload/preview and crafted invalid submissions.
3. Invoice: walk-in and stored customer checkout, reservation conflict, payment and invoice lookup/print.
4. Report: switch 6/12/year and compare every KPI, chart bar and table row.
5. Notification: trigger a real 6→5 or 1→0 stock transition, logout/login, open a second session, mark read and verify the count only changes for that account.
