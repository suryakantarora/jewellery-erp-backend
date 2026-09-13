-- 1. Unified approvals: information requests and decision idempotency.
--
-- Five modules each carry their own approval workflow (transfers, purchase
-- orders and requisitions, exchange valuations, stock counts, goods receipts)
-- and none of them could say "tell me more before I decide". The information
-- request sits beside the underlying record and never changes its status: the
-- approver is asking, not deciding. The decision table records every decision
-- taken through the unified endpoint so a retried request with the same
-- X-Idempotency-Key returns the original outcome instead of approving twice.
CREATE TABLE public.approval_information_request (
    id             uuid PRIMARY KEY,
    version        bigint       NOT NULL DEFAULT 0,
    approval_type  varchar(30)  NOT NULL,
    reference_id   uuid         NOT NULL,
    requested_by   varchar(100) NOT NULL,
    message        varchar(500) NOT NULL,
    answered_at    timestamptz,
    answer         varchar(500),
    answered_by    varchar(100),
    created_at     timestamptz  NOT NULL,
    created_by     varchar(100),
    updated_at     timestamptz  NOT NULL,
    updated_by     varchar(100)
);

CREATE INDEX ix_approval_info_reference
    ON public.approval_information_request (approval_type, reference_id, answered_at);

CREATE TABLE public.approval_decision (
    id              uuid PRIMARY KEY,
    version         bigint       NOT NULL DEFAULT 0,
    approval_type   varchar(30)  NOT NULL,
    reference_id    uuid         NOT NULL,
    decision        varchar(20)  NOT NULL,
    result_status   varchar(30)  NOT NULL,
    decided_by      varchar(100) NOT NULL,
    reason          varchar(500),
    idempotency_key varchar(100),
    created_at      timestamptz  NOT NULL,
    created_by      varchar(100),
    updated_at      timestamptz  NOT NULL,
    updated_by      varchar(100)
);

CREATE UNIQUE INDEX uq_approval_decision_idempotency_key
    ON public.approval_decision (idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX ix_approval_decision_reference
    ON public.approval_decision (approval_type, reference_id);

-- 2. Discount requests.
--
-- DISCOUNT_REQUEST and DISCOUNT_APPROVE were seeded from the start but nothing
-- referenced them: a discount beyond the branch policy could only be taken by
-- someone who held DISCOUNT_APPROVE themselves. This is the request a
-- salesperson raises and a manager decides, which a later sale then consumes.
-- Exactly one of percentage / amount is set. An approval is time-boxed because
-- metal rates move; a discount agreed yesterday is not an open cheque.
CREATE TABLE sales.discount_request (
    id                   uuid PRIMARY KEY,
    version              bigint       NOT NULL DEFAULT 0,
    branch_id            uuid         NOT NULL REFERENCES organization.branch (id),
    customer_id          uuid         REFERENCES customer.customer (id),
    jewellery_item_id    uuid         REFERENCES inventory.jewellery_item (id),
    quotation_id         uuid         REFERENCES sales.quotation (id),
    requested_percentage numeric(5,2),
    requested_amount     numeric(19,2),
    currency             varchar(3),
    reason               varchar(500) NOT NULL,
    status               varchar(20)  NOT NULL,
    decided_by           varchar(100),
    decided_at           timestamptz,
    decision_note        varchar(500),
    expires_at           timestamptz  NOT NULL,
    consumed_by_sale_id  uuid         REFERENCES sales.sale (id),
    created_at           timestamptz  NOT NULL,
    created_by           varchar(100),
    updated_at           timestamptz  NOT NULL,
    updated_by           varchar(100),
    CONSTRAINT ck_discount_request_one_of CHECK (
        (requested_percentage IS NOT NULL) <> (requested_amount IS NOT NULL))
);

CREATE INDEX ix_discount_request_status ON sales.discount_request (status, branch_id, created_at);
CREATE INDEX ix_discount_request_expiry ON sales.discount_request (expires_at)
    WHERE status IN ('PENDING', 'APPROVED');

-- 3. Idempotent intake creation.
--
-- Sales, payments and goods receipts already accept X-Idempotency-Key; taking
-- in a customer's old jewellery or a repair job did not, and a retried POST on
-- a flaky connection created two intakes for one piece.
ALTER TABLE sales.exchange_intake ADD COLUMN idempotency_key varchar(100);
CREATE UNIQUE INDEX uq_exchange_intake_idempotency_key
    ON sales.exchange_intake (idempotency_key) WHERE idempotency_key IS NOT NULL;

ALTER TABLE sales.repair_request ADD COLUMN idempotency_key varchar(100);
CREATE UNIQUE INDEX uq_repair_request_idempotency_key
    ON sales.repair_request (idempotency_key) WHERE idempotency_key IS NOT NULL;

-- 4. Customer wishlist.
--
-- A quotation prices specific pieces for a specific day; a wishlist is the
-- longer-lived "she liked this one" that any colleague at any branch can see
-- when the customer comes back. It can point at a physical item, a product, or
-- a design, since what a customer wants is not always something in stock yet.
CREATE TABLE customer.customer_wishlist (
    id                uuid PRIMARY KEY,
    version           bigint       NOT NULL DEFAULT 0,
    customer_id       uuid         NOT NULL REFERENCES customer.customer (id) ON DELETE CASCADE,
    jewellery_item_id uuid         REFERENCES inventory.jewellery_item (id),
    product_id        uuid         REFERENCES product.product (id),
    design_id         uuid         REFERENCES product.jewellery_design (id),
    note              varchar(500),
    added_by          varchar(100),
    branch_id         uuid         REFERENCES organization.branch (id),
    created_at        timestamptz  NOT NULL,
    created_by        varchar(100),
    updated_at        timestamptz  NOT NULL,
    updated_by        varchar(100),
    CONSTRAINT ck_customer_wishlist_target CHECK (
        jewellery_item_id IS NOT NULL OR product_id IS NOT NULL OR design_id IS NOT NULL)
);

CREATE INDEX ix_customer_wishlist_customer ON customer.customer_wishlist (customer_id, created_at);
CREATE UNIQUE INDEX uq_customer_wishlist_item
    ON customer.customer_wishlist (customer_id, jewellery_item_id)
    WHERE jewellery_item_id IS NOT NULL;
