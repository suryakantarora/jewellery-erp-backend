# Jewellery ERP & Retail Platform — Backend

Spring Boot **modular monolith** for a multi-branch jewellery retailer in Laos,
built to `Jewellery_Backend_Architecture_Instruction.md`.

Group id `com.finotech`, base package `com.finotech.jewellery`.

## Status

| Phase | Modules | State |
|---|---|---|
| 1 — Foundation | Shared, Identity, Organization, Audit | **Done** |
| 2 — Core jewellery domain | Product, Metal, Gemstone, Jewellery Item, Inventory Movement | **Done** |
| 3 — Commercial | Supplier, Procurement, Pricing, Customer, Sales/POS, Payment | **Done** |
| 4 — Operations | Exchange & Buyback, Repair, Warehouse & Vault, Notification | **Done** |
| 5 — Customer growth | CRM, Loyalty, Segmentation, Campaigns | **Done** |
| 6 — Enterprise | Finance, Compliance, Reporting | **Done** |

The platform now runs the full shop floor: buy from a supplier, receive stock as
serialized items, price and sell them, take split payments, refund and return,
take old jewellery in exchange or buyback, repair pieces, verify the vault,
award and redeem loyalty points, run targeted campaigns, and post every one of
those events to a double-entry ledger. Business modules
announce what happened through domain events; loyalty and notification react to
them. The central entity remains the unique serialized
**JewelleryItem** with full lifecycle traceability.

## Stack

Java 17 · Spring Boot 3.3 · PostgreSQL 16 · Flyway · Spring Security (JWT) ·
springdoc OpenAPI · Redis · Actuator/Prometheus.

## Running locally

Start the infrastructure:

```bash
docker compose up -d postgres redis minio
```

> If port 5432 is already taken by a local PostgreSQL, run the app with
> `DB_PORT=<other port>` and map the container accordingly.

Then start the application. The initial super administrator is created only if
you supply a password — no credentials are ever committed:

```bash
JEWELLERY_BOOTSTRAP_ADMIN_PASSWORD='<choose-a-strong-password>' mvn spring-boot:run
```

- API docs: <http://localhost:8080/swagger-ui.html>
- Health: <http://localhost:8080/actuator/health>

Log in at `POST /api/v1/auth/login` with `admin` and that password; the response
carries `mustChangePassword: true` until it is changed.

### Configuration

Secrets come from the environment only. Relevant variables:

| Variable | Purpose |
|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | Database |
| `JWT_SECRET` | Access-token signing key (≥ 32 bytes; required outside `local`) |
| `JWT_ACCESS_MINUTES` / `JWT_REFRESH_DAYS` | Token lifetimes |
| `JEWELLERY_BOOTSTRAP_ADMIN_PASSWORD` | First-run administrator password |
| `SERVER_PORT` | HTTP port |

Profiles: `local` (default), `dev`, `prod`.

## Tests

```bash
mvn test
```

Unit tests run anywhere. The integration tests need a PostgreSQL and pick one in
this order:

1. `TEST_DB_URL` (plus optional `TEST_DB_USERNAME` / `TEST_DB_PASSWORD`);
2. otherwise a throwaway Testcontainers database.

If neither is reachable they are **skipped**, not failed, so `mvn test` stays
green on a machine without Docker. To run them against a database you already
have up:

```bash
TEST_DB_URL='jdbc:postgresql://localhost:5433/jewellery_test' mvn test
```

Point `TEST_DB_URL` at a **dedicated** database, not one you have been clicking
around in. The integration tests assert exact prices, and a stray company-wide
tax or pricing rule left in a shared database will change them.

> On Colima, Testcontainers cannot auto-detect the socket (docker-java
> negotiates an API version Colima rejects). Use the `TEST_DB_URL` route above,
> or symlink the socket to `/var/run/docker.sock`.

## Layout

```
src/main/java/com/finotech/jewellery
├── shared/            config · common · exception · security · audit · utils
├── modules/
│   ├── identity/      users, roles, permissions, JWT, refresh tokens
│   ├── organization/  company · branch · location tree
│   ├── product/       category · type · brand · collection · design · product
│   ├── metal/         metal · purity · daily rates · scrap
│   ├── gemstone/      gemstones · certificates · stones set in an item
│   ├── inventory/     JewelleryItem · lifecycle · movements
│   ├── supplier/      vendors · contacts · bank details · documents
│   ├── procurement/   requisition · purchase order · goods receipt · invoice
│   ├── pricing/       making-charge rules · tax · discount policy · calculator
│   ├── customer/      customer master · addresses · documents · KYC
│   ├── sales/         quotation · sale · return · daily closing
│   ├── payment/       split payments · refunds · reconciliation
│   ├── exchange/      old-jewellery intake · purity test · valuation · buyback
│   ├── repair/        intake · estimate · customer approval · QC · delivery
│   ├── warehouse/     storage bins · stock verification · dual control
│   ├── loyalty/       programs · tiers · points · redemption · expiry
│   ├── crm/           customer 360 · activities · follow-ups · segments · campaigns
│   ├── finance/       chart of accounts · double-entry journal · statements
│   ├── compliance/    high-value · KYC · dual-authorisation · audit summary
│   ├── reporting/     sales · inventory valuation · stock ageing · loyalty liability
│   └── notification/  templates · outbox · dispatcher · event listeners
└── JewelleryApplication.java
```

Each module follows `api / application / domain / infrastructure`, and every
module owns its own tables in its own PostgreSQL schema.

## Design decisions worth knowing

**Modules talk through published interfaces, never each other's tables.**
`OrganizationDirectory`, `ProductCatalog`, `MetalRateProvider`, `StoneRegistry`
and `InventoryOperations` are the seams. When a module is later extracted into a
service, these are the contracts that become remote calls.

**One `Location` entity, not one per kind.** Showroom, counter, vault, store
room and warehouse are a `LocationType` on a self-referencing `Location` tree.
Inventory therefore holds a single uniform "current location" reference instead
of a polymorphic one.

**Status transitions live in the `ItemStatus` enum.** A sold item cannot be
transferred, an item under repair cannot be sold, and `SCRAPPED` is terminal —
enforced in one table rather than scattered across services.

**Transfers are two-sided.** Items leave the source at dispatch and become
`IN_TRANSIT`; they only arrive when the destination confirms receipt. Nothing is
ever in two places, and goods in transit are visible as such. Cross-branch moves
and any movement touching a dual-authorization vault require approval, and vault
movements need two *different* approvers.

**Concurrency.** Every entity carries a `@Version` for optimistic locking, and
the reservation, sale and dispatch paths additionally take a pessimistic row lock
(`SELECT … FOR UPDATE`), so two cashiers cannot sell the same item. Movement
creation accepts an `X-Idempotency-Key` header; a replayed key returns the
movement already created.

**Money and weight are always `BigDecimal`** — 2 decimals for money, 3 for
weight, `HALF_UP`. Never floating point.

**Business modules publish events, they do not call side effects** (§13).
`DomainEventPublisher` raises facts like `SaleCompleted` and `RepairReady`;
notification listens `AFTER_COMMIT`, so a customer is never told about a sale
that rolled back. The same events can be forwarded to Kafka later without
touching the modules that raise them.

**Notifications are an outbox, not an inline send.** Messages are queued inside
the listener and delivered by a scheduled dispatcher, each in its own
transaction — a slow SMS provider cannot delay a sale, and one bad address
cannot block the batch. Retries are bounded, after which a message is abandoned
rather than retried forever. Templates use `{{placeholder}}` markers, so wording
and language change without a deployment.

**Loyalty points are a ledger, not a counter.** Every movement — earn, redeem,
reverse, expire, adjust — is an immutable row carrying the balance it produced,
so a disputed balance can be reconstructed. The balance on the account exists
only so a POS lookup is fast; both move together under a row lock, which stops
two counters spending the same points. Awarding is idempotent per sale, backed
by a unique index rather than only a check, so a redelivered event cannot
double-credit. Spending points never reduces lifetime points, so a customer
cannot be demoted by using their own benefits.

**Loyalty benefits reach the price.** A tier's standing discount is applied by
Pricing as an automatic entitlement, separate from a staff-requested discount and
outside the approval allowance — it is not a decision anyone makes at the
counter. Points redeemed against a sale reduce what the customer pays rather than
the sale total, so the invoice still shows the true value of the goods. Cancelling
a sale hands the points back without touching lifetime points, so an abandoned
purchase cannot promote a customer.

**Loyalty listens; Sales does not know it exists.** Points are awarded from a
`SaleCompleted` listener running after commit in a new transaction. A loyalty
failure is logged, not propagated — the sale is already paid for, and a benefit
must never fail a customer's purchase.

**Segments are evaluated, not stored.** A campaign always reaches who qualifies
today. Criteria are explicit columns rather than a query string: safe to
evaluate, readable by staff, and limited to the dimensions actually used.
Evaluation pulls candidates from Customer, then filters against purchase history
and loyalty standing in one batch each — not one query per customer.

**Campaigns name a template, not a message.** The campaign says who to reach;
the notification module owns wording and channel. Switching a campaign from SMS
to email is a template change. A campaign launches once — re-running it would
message every customer twice.

**Valuations are derived, not typed in.** Exchange and buyback enforce
weigh → purity-test → value in order, and the payout is computed from the
recorded net weight, the *tested* purity and the `BUYING` rate. The valuer
cannot approve their own valuation. Every measurement behind a payout is kept,
because buyback is the easiest place in a jewellery business to lose money
quietly.

**Repairs gate on the customer.** No chargeable work starts until the estimate
is accepted; a failed quality check returns the job to the bench rather than
reaching the customer; and a piece the business sold is moved to
`UNDER_REPAIR`, which stops it being transferred or sold while it is on the
bench.

**Stock counts report, they never correct.** A count snapshots the expected
stock, records what was found, and reports missing and unexpected items. It does
not adjust anything: a missing vault item is an incident to investigate, not a
number to silently fix. Counts need a reviewer other than the counter, and a
vault count — or any count with a variance — needs two different approvers.

**The ledger is written in one place and never edited.** Every automatic
posting goes through `JournalPostingService`, which is the only code that writes
journal entries and the only place the rules live: an entry must balance, may
only touch postable accounts, and is immutable once posted. A mistake is
corrected by a reversing entry. Both the original and its reversal stay in the
ledger — they net to zero, and the history of what was posted and then corrected
stays visible. Posting the same business document twice is refused, backed by a
unique index rather than only a check.

**Finance consumes events; no module knows it exists** (dependency rule 6). Sale
confirmation, payment, goods receipt, exchange and loyalty awards each raise an
event, and finance decides what that means in accounting terms. Postings run
after commit in their own transaction: a bookkeeping problem must never roll back
a sale the customer has already paid for. A failed posting is logged loudly,
because an unposted sale is a real gap needing correction, not something to
swallow.

**Payments credit customer deposits, not receivables.** A payment can arrive
before its sale is confirmed, so money is held as a deposit and the confirmation
entry clears it. Doing it the other way round would leave receivables negative
between the two.

**Statements report whether they balance rather than assuming it.** Retained
earnings are derived from the ledger instead of being stored, so a balance sheet
is always drawn from the same source as everything else and cannot drift. Where
a statement should balance, the check is part of the response.

**Uploaded filenames are never trusted.** The storage key is generated, not
derived from the upload, and any key resolving outside the storage root is
refused. Content types are an allow-list, so accepting a new one is a deliberate
decision. Files are served as attachments, never inline.

**Reporting reads across schemas; nothing else does.** The compliance and
reporting modules query several schemas directly in SQL, which no business module
is permitted to do. That is correct here and only here: a report is a question
about the business as a whole, and answering it by calling each module in turn
would be slower and less accurate. Nothing in either module writes, so the
ownership rule that protects data integrity is untouched. Keeping every report
query in one class per area is also what makes the later move behind ETL a
replacement rather than a rewrite (section 22).

**Compliance reports over what is already recorded.** High-value transactions,
KYC posture, dual-authorisation controls and the audit summary are all derived
from data the business modules already write — the audit trail, KYC state, and
the approvals captured on vault movements, stock counts and buyback valuations.
No module records anything extra to make a report possible, which is what keeps
the reports honest. Thresholds are configuration, not constants, because
jurisdictions differ and limits change. Running a compliance report is itself
audited.

**Payment never touches inventory** (dependency rule 4). It records money and
reports it to Sales through `SaleSettlement`; Sales alone decides what full
settlement means. Duplicate protection is twofold: a replayed `X-Idempotency-Key`
returns the original payment, and a unique index on that key means even two
genuinely concurrent retries cannot both insert.

**Rates are append-only history.** Republishing a rate for a later date never
alters prices already captured on earlier documents.

**Pricing is a pure calculation.** `PricingCalculator.calculate` reads the item,
the rate in force and the matching rules, and returns a full breakdown —
metal, wastage, making charge, stone value, tax, discount. It writes nothing,
and Sales stores the returned breakdown on the sale line rather than recomputing
it, so an invoice can be reproduced exactly years later. Making-charge rules
resolve most-specific-scope-first (product beats product type beats catch-all).

**Discounts escalate rather than being blocked.** A branch's `DiscountPolicy`
sets what staff may give unaided and what an approver may authorise; beyond that
ceiling the discount is refused outright. Which part of the price a discount may
erode (making charge, metal value, or both) is policy, not code.

**Stock leaves at settlement, not at checkout.** Opening a sale reserves its
items and prices them; the items become `SOLD` only when the sale is fully paid.
An abandoned sale is cancelled and its items return to stock automatically.

**Goods become stock only on quality check.** A goods receipt is recorded first
and accepted second; acceptance creates one serialized item per physical piece
through `InventoryOperations`. Goods that fail the check never become stock, and
over-delivery against a purchase order is refused before anything is written.

**Auditing.** `AuditService` writes in its own transaction, so a rolled-back
business operation still leaves a trace, and auditing failures never break the
operation they observe. Separately, every item carries a full lifecycle history
(`GET /api/v1/inventory/items/{id}/passport`) — the digital jewellery passport.

## API conventions

Versioned under `/api/v1`. Successes are wrapped in
`{ success, data, message, timestamp }`; failures in
`{ success: false, code, message, path, correlationId, fieldErrors, timestamp }`
with a stable `code`. List endpoints are paginated (`page`, `size`, `sort`).
Every request carries an `X-Correlation-Id` through logs and error payloads.

Authorization is by **permission**, never role name, so a role's permission set
can change without touching a controller.

### Note on ordering

Bean validation runs before method-level authorization, so a malformed body on a
protected endpoint returns `400` rather than `403`. This reveals only that an
endpoint exists; tighten it with a filter-level rule if that matters to you.

## Where this stands

Every module in the instruction's roadmap is built, from Identity through to the
general ledger. The remaining items are the ones the instruction itself defers.

**BI and the data warehouse** (section 22, module 37). Reporting runs against
PostgreSQL, which is what the instruction asks for first. Moving behind ETL is
the next step when query volume justifies it; keeping each report area's queries
in a single repository class is what makes that a replacement rather than a
rewrite.

**Deliberate limits, worth knowing before going live:**

- **File storage defaults to local disk.** Fine for one instance and for
  development. Several application servers would each hold different files, so
  production wants a shared object store — swap in an implementation of
  `FileStorageService`; nothing else changes.
- **Reports are computed on demand,** capped at 366 days per request. That is
  the right trade at this scale.
- **The ledger has no period close.** Entries can be posted to any date. A
  closing lock is the natural next piece of finance work.
- **Supplier invoices are recorded but not yet posted to the ledger.** Goods
  receipt raises the payable; matching the supplier's invoice against it is not
  automated.
- **Notification transports log rather than send.** Registering a real email or
  SMS provider is one bean per channel.
