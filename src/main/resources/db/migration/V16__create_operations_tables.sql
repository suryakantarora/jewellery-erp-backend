-- ---------- exchange & buyback ----------

CREATE TABLE sales.exchange_intake (
    id                   uuid PRIMARY KEY,
    version              bigint      NOT NULL DEFAULT 0,
    reference_number     varchar(50)  NOT NULL,
    exchange_type        varchar(20)  NOT NULL,
    status               varchar(30)  NOT NULL,
    customer_id          uuid        NOT NULL REFERENCES customer.customer (id),
    branch_id            uuid        NOT NULL REFERENCES organization.branch (id),
    location_id          uuid        REFERENCES organization.location (id),
    received_date        date        NOT NULL,
    description          varchar(500) NOT NULL,
    item_count           integer     NOT NULL DEFAULT 1,
    original_item_id     uuid        REFERENCES inventory.jewellery_item (id),
    metal_id             uuid        NOT NULL REFERENCES product.metal (id),
    gross_weight         numeric(12,3),
    stone_weight         numeric(12,3),
    net_weight           numeric(12,3),
    weighed_by           varchar(100),
    weighed_at           timestamptz,
    declared_purity_id   uuid        REFERENCES product.purity (id),
    tested_purity_id     uuid        REFERENCES product.purity (id),
    tested_fineness      numeric(9,6),
    test_method          varchar(50),
    tested_by            varchar(100),
    tested_at            timestamptz,
    rate_id              uuid        REFERENCES product.metal_rate (id),
    rate_per_unit        numeric(19,4),
    pure_weight          numeric(12,3),
    gross_valuation      numeric(19,4),
    deduction_percentage numeric(7,4),
    deduction_amount     numeric(19,4),
    net_valuation        numeric(19,4),
    currency             varchar(3)   NOT NULL DEFAULT 'LAK',
    valued_by            varchar(100),
    valued_at            timestamptz,
    approved_by          varchar(100),
    approved_at          timestamptz,
    rejection_reason     varchar(500),
    applied_sale_id      uuid        REFERENCES sales.sale (id),
    scrap_batch_id       uuid        REFERENCES inventory.scrap_metal (id),
    completed_at         timestamptz,
    notes                varchar(500),
    created_at           timestamptz NOT NULL,
    created_by           varchar(100),
    updated_at           timestamptz NOT NULL,
    updated_by           varchar(100),
    CONSTRAINT uq_exchange_reference UNIQUE (reference_number),
    -- The money paid out can never exceed the metal it was valued from.
    CONSTRAINT ck_exchange_valuation CHECK (
        net_valuation IS NULL OR gross_valuation IS NULL OR net_valuation <= gross_valuation)
);

CREATE INDEX ix_exchange_status ON sales.exchange_intake (status, branch_id);
CREATE INDEX ix_exchange_customer ON sales.exchange_intake (customer_id);

-- ---------- repair ----------

CREATE TABLE sales.repair_request (
    id                   uuid PRIMARY KEY,
    version              bigint      NOT NULL DEFAULT 0,
    request_number       varchar(50)  NOT NULL,
    customer_id          uuid        NOT NULL REFERENCES customer.customer (id),
    branch_id            uuid        NOT NULL REFERENCES organization.branch (id),
    jewellery_item_id    uuid        REFERENCES inventory.jewellery_item (id),
    item_description     varchar(500) NOT NULL,
    status               varchar(30)  NOT NULL,
    received_date        date        NOT NULL,
    promised_date        date,
    reported_problem     varchar(1000) NOT NULL,
    condition_on_arrival varchar(1000),
    received_weight      numeric(12,3),
    condition_photo_keys varchar(1000),
    estimated_cost       numeric(19,4),
    estimated_days       integer,
    estimate_notes       varchar(1000),
    customer_approved    boolean     NOT NULL DEFAULT false,
    customer_response_at timestamptz,
    decline_reason       varchar(500),
    assigned_to          varchar(100),
    assigned_at          timestamptz,
    final_cost           numeric(19,4),
    currency             varchar(3)   NOT NULL DEFAULT 'LAK',
    delivered_weight     numeric(12,3),
    ready_at             timestamptz,
    delivered_at         timestamptz,
    delivered_to         varchar(150),
    notes                varchar(500),
    created_at           timestamptz NOT NULL,
    created_by           varchar(100),
    updated_at           timestamptz NOT NULL,
    updated_by           varchar(100),
    CONSTRAINT uq_repair_request_number UNIQUE (request_number)
);

CREATE INDEX ix_repair_status ON sales.repair_request (status, branch_id);
CREATE INDEX ix_repair_customer ON sales.repair_request (customer_id);
CREATE INDEX ix_repair_promised ON sales.repair_request (promised_date)
    WHERE promised_date IS NOT NULL;
CREATE INDEX ix_repair_assigned ON sales.repair_request (assigned_to)
    WHERE assigned_to IS NOT NULL;

CREATE TABLE sales.repair_status_history (
    id                uuid PRIMARY KEY,
    repair_request_id uuid        NOT NULL REFERENCES sales.repair_request (id) ON DELETE CASCADE,
    from_status       varchar(30),
    to_status         varchar(30)  NOT NULL,
    performed_by      varchar(100),
    notes             varchar(1000),
    occurred_at       timestamptz NOT NULL
);

CREATE INDEX ix_repair_history ON sales.repair_status_history (repair_request_id, occurred_at);

-- ---------- warehouse & vault ----------

CREATE TABLE inventory.storage_bin (
    id          uuid PRIMARY KEY,
    version     bigint      NOT NULL DEFAULT 0,
    location_id uuid        NOT NULL REFERENCES organization.location (id),
    parent_id   uuid        REFERENCES inventory.storage_bin (id),
    code        varchar(40)  NOT NULL,
    name        varchar(150) NOT NULL,
    bin_type    varchar(20)  NOT NULL,
    capacity    integer,
    description varchar(255),
    active      boolean     NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL,
    created_by  varchar(100),
    updated_at  timestamptz NOT NULL,
    updated_by  varchar(100),
    CONSTRAINT uq_storage_bin_code UNIQUE (code)
);

CREATE INDEX ix_storage_bin_location ON inventory.storage_bin (location_id);
CREATE INDEX ix_storage_bin_parent ON inventory.storage_bin (parent_id);

CREATE TABLE inventory.stock_count (
    id                 uuid PRIMARY KEY,
    version            bigint      NOT NULL DEFAULT 0,
    reference_number   varchar(50)  NOT NULL,
    location_id        uuid        NOT NULL REFERENCES organization.location (id),
    branch_id          uuid        NOT NULL REFERENCES organization.branch (id),
    status             varchar(30)  NOT NULL,
    count_date         date        NOT NULL,
    dual_authorization boolean     NOT NULL DEFAULT false,
    expected_count     integer     NOT NULL DEFAULT 0,
    counted_count      integer     NOT NULL DEFAULT 0,
    missing_count      integer     NOT NULL DEFAULT 0,
    unexpected_count   integer     NOT NULL DEFAULT 0,
    counted_by         varchar(100),
    counted_at         timestamptz,
    approved_by        varchar(100),
    approved_at        timestamptz,
    second_approved_by varchar(100),
    second_approved_at timestamptz,
    closed_at          timestamptz,
    notes              varchar(1000),
    created_at         timestamptz NOT NULL,
    created_by         varchar(100),
    updated_at         timestamptz NOT NULL,
    updated_by         varchar(100),
    CONSTRAINT uq_stock_count_reference UNIQUE (reference_number)
);

-- At most one open count per location; enforced in the database as well as in
-- the service, because two overlapping counts make both untrustworthy.
CREATE UNIQUE INDEX uq_stock_count_open_per_location
    ON inventory.stock_count (location_id)
    WHERE status IN ('IN_PROGRESS', 'PENDING_REVIEW', 'APPROVED');
CREATE INDEX ix_stock_count_status ON inventory.stock_count (status, branch_id);

CREATE TABLE inventory.stock_count_line (
    id                uuid PRIMARY KEY,
    version           bigint      NOT NULL DEFAULT 0,
    stock_count_id    uuid        NOT NULL REFERENCES inventory.stock_count (id) ON DELETE CASCADE,
    jewellery_item_id uuid        NOT NULL,
    item_code         varchar(50)  NOT NULL,
    expected          boolean     NOT NULL DEFAULT false,
    counted           boolean     NOT NULL DEFAULT false,
    bin_id            uuid        REFERENCES inventory.storage_bin (id),
    variance_note     varchar(500),
    created_at        timestamptz NOT NULL,
    created_by        varchar(100),
    updated_at        timestamptz NOT NULL,
    updated_by        varchar(100),
    CONSTRAINT uq_stock_count_line_item UNIQUE (stock_count_id, jewellery_item_id)
);

CREATE INDEX ix_stock_count_line ON inventory.stock_count_line (stock_count_id);
