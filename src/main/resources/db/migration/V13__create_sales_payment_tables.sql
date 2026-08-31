CREATE TABLE sales.quotation (
    id                uuid PRIMARY KEY,
    version           bigint      NOT NULL DEFAULT 0,
    quotation_number  varchar(50)  NOT NULL,
    customer_id       uuid        NOT NULL REFERENCES customer.customer (id),
    branch_id         uuid        NOT NULL REFERENCES organization.branch (id),
    status            varchar(30)  NOT NULL,
    quotation_date    date        NOT NULL,
    valid_until       date        NOT NULL,
    currency          varchar(3)   NOT NULL DEFAULT 'LAK',
    sub_total         numeric(19,4) NOT NULL DEFAULT 0,
    tax_total         numeric(19,4) NOT NULL DEFAULT 0,
    discount_total    numeric(19,4) NOT NULL DEFAULT 0,
    total_amount      numeric(19,4) NOT NULL DEFAULT 0,
    converted_sale_id uuid,
    notes             varchar(500),
    created_at        timestamptz NOT NULL,
    created_by        varchar(100),
    updated_at        timestamptz NOT NULL,
    updated_by        varchar(100),
    CONSTRAINT uq_quotation_number UNIQUE (quotation_number)
);

CREATE INDEX ix_quotation_customer ON sales.quotation (customer_id);
CREATE INDEX ix_quotation_status ON sales.quotation (status, branch_id);

CREATE TABLE sales.quotation_line (
    id                uuid PRIMARY KEY,
    version           bigint      NOT NULL DEFAULT 0,
    quotation_id      uuid        NOT NULL REFERENCES sales.quotation (id) ON DELETE CASCADE,
    jewellery_item_id uuid        NOT NULL REFERENCES inventory.jewellery_item (id),
    item_code         varchar(50)  NOT NULL,
    metal_value       numeric(19,4),
    making_charge     numeric(19,4),
    wastage_value     numeric(19,4),
    stone_value       numeric(19,4),
    discount_amount   numeric(19,4) DEFAULT 0,
    tax_amount        numeric(19,4) DEFAULT 0,
    line_total        numeric(19,4) NOT NULL DEFAULT 0,
    created_at        timestamptz NOT NULL,
    created_by        varchar(100),
    updated_at        timestamptz NOT NULL,
    updated_by        varchar(100)
);

CREATE INDEX ix_quotation_line ON sales.quotation_line (quotation_id);

CREATE TABLE sales.sale (
    id                   uuid PRIMARY KEY,
    version              bigint      NOT NULL DEFAULT 0,
    sale_number          varchar(50)  NOT NULL,
    invoice_number       varchar(50),
    customer_id          uuid        NOT NULL REFERENCES customer.customer (id),
    branch_id            uuid        NOT NULL REFERENCES organization.branch (id),
    location_id          uuid        REFERENCES organization.location (id),
    quotation_id         uuid        REFERENCES sales.quotation (id),
    status               varchar(30)  NOT NULL,
    sale_date            date        NOT NULL,
    currency             varchar(3)   NOT NULL DEFAULT 'LAK',
    sub_total            numeric(19,4) NOT NULL DEFAULT 0,
    discount_total       numeric(19,4) NOT NULL DEFAULT 0,
    tax_total            numeric(19,4) NOT NULL DEFAULT 0,
    exchange_credit      numeric(19,4) NOT NULL DEFAULT 0,
    total_amount         numeric(19,4) NOT NULL DEFAULT 0,
    paid_amount          numeric(19,4) NOT NULL DEFAULT 0,
    refunded_amount      numeric(19,4) NOT NULL DEFAULT 0,
    salesperson_id       uuid        REFERENCES identity.app_user (id),
    discount_approved_by varchar(100),
    confirmed_at         timestamptz,
    delivered_at         timestamptz,
    cancellation_reason  varchar(500),
    external_reference   varchar(100),
    notes                varchar(500),
    created_at           timestamptz NOT NULL,
    created_by           varchar(100),
    updated_at           timestamptz NOT NULL,
    updated_by           varchar(100),
    CONSTRAINT uq_sale_number UNIQUE (sale_number),
    CONSTRAINT uq_sale_invoice_number UNIQUE (invoice_number),
    CONSTRAINT ck_sale_paid_not_negative CHECK (paid_amount >= 0)
);

-- Backs the idempotency check when a sale is opened.
CREATE UNIQUE INDEX uq_sale_external_reference
    ON sales.sale (external_reference) WHERE external_reference IS NOT NULL;
CREATE INDEX ix_sale_customer ON sales.sale (customer_id);
CREATE INDEX ix_sale_branch_date ON sales.sale (branch_id, sale_date);
CREATE INDEX ix_sale_status ON sales.sale (status);

CREATE TABLE sales.sale_line (
    id                  uuid PRIMARY KEY,
    version             bigint      NOT NULL DEFAULT 0,
    sale_id             uuid        NOT NULL REFERENCES sales.sale (id) ON DELETE CASCADE,
    jewellery_item_id   uuid        NOT NULL REFERENCES inventory.jewellery_item (id),
    item_code           varchar(50)  NOT NULL,
    metal_rate_id       uuid        REFERENCES product.metal_rate (id),
    metal_rate_per_unit numeric(19,4),
    net_metal_weight    numeric(12,3),
    metal_value         numeric(19,4) NOT NULL DEFAULT 0,
    wastage_value       numeric(19,4) NOT NULL DEFAULT 0,
    making_charge       numeric(19,4) NOT NULL DEFAULT 0,
    stone_value         numeric(19,4) NOT NULL DEFAULT 0,
    discount_amount     numeric(19,4) NOT NULL DEFAULT 0,
    tax_amount          numeric(19,4) NOT NULL DEFAULT 0,
    line_total          numeric(19,4) NOT NULL DEFAULT 0,
    returned            boolean     NOT NULL DEFAULT false,
    return_reason       varchar(500),
    created_at          timestamptz NOT NULL,
    created_by          varchar(100),
    updated_at          timestamptz NOT NULL,
    updated_by          varchar(100),
    -- An item can appear at most once on a given sale.
    CONSTRAINT uq_sale_line_item UNIQUE (sale_id, jewellery_item_id)
);

CREATE INDEX ix_sale_line_item ON sales.sale_line (jewellery_item_id);

CREATE TABLE payment.payment (
    id                    uuid PRIMARY KEY,
    version               bigint      NOT NULL DEFAULT 0,
    payment_number        varchar(50)  NOT NULL,
    sale_id               uuid        NOT NULL REFERENCES sales.sale (id),
    customer_id           uuid        REFERENCES customer.customer (id),
    branch_id             uuid        NOT NULL REFERENCES organization.branch (id),
    direction             varchar(20)  NOT NULL,
    method                varchar(30)  NOT NULL,
    status                varchar(20)  NOT NULL,
    amount                numeric(19,4) NOT NULL CHECK (amount > 0),
    currency              varchar(3)   NOT NULL DEFAULT 'LAK',
    transaction_reference varchar(100),
    card_last_four        varchar(4),
    bank_name             varchar(150),
    idempotency_key       varchar(100),
    original_payment_id   uuid        REFERENCES payment.payment (id),
    captured_at           timestamptz,
    failure_reason        varchar(500),
    reconciled            boolean     NOT NULL DEFAULT false,
    reconciled_at         timestamptz,
    notes                 varchar(500),
    created_at            timestamptz NOT NULL,
    created_by            varchar(100),
    updated_at            timestamptz NOT NULL,
    updated_by            varchar(100),
    CONSTRAINT uq_payment_number UNIQUE (payment_number)
);

-- The database-level guarantee behind duplicate-payment prevention: even two
-- genuinely concurrent retries cannot both insert.
CREATE UNIQUE INDEX uq_payment_idempotency_key
    ON payment.payment (idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX ix_payment_sale ON payment.payment (sale_id);
CREATE INDEX ix_payment_branch_captured ON payment.payment (branch_id, captured_at);
CREATE INDEX ix_payment_status ON payment.payment (status);
