CREATE TABLE inventory.jewellery_item (
    id                       uuid PRIMARY KEY,
    version                  bigint      NOT NULL DEFAULT 0,
    item_code                varchar(50)  NOT NULL,
    product_id               uuid        NOT NULL REFERENCES product.product (id),
    design_id                uuid        REFERENCES product.jewellery_design (id),
    metal_id                 uuid        NOT NULL REFERENCES product.metal (id),
    purity_id                uuid        NOT NULL REFERENCES product.purity (id),
    gross_weight             numeric(12,3) NOT NULL CHECK (gross_weight > 0),
    net_metal_weight         numeric(12,3) NOT NULL CHECK (net_metal_weight > 0),
    stone_weight             numeric(12,3) DEFAULT 0,
    stone_count              integer     NOT NULL DEFAULT 0,
    total_carat              numeric(10,3) DEFAULT 0,
    size_id                  uuid        REFERENCES product.size (id),
    rfid_tag                 varchar(100),
    qr_code                  varchar(100),
    barcode                  varchar(100),
    hallmark_number          varchar(50),
    purchase_cost            numeric(19,4),
    making_cost              numeric(19,4),
    stone_cost               numeric(19,4),
    total_cost               numeric(19,4),
    current_price            numeric(19,4),
    price_calculated_at      timestamptz,
    currency                 varchar(3)   NOT NULL DEFAULT 'LAK',
    status                   varchar(20)  NOT NULL,
    current_location_id      uuid        REFERENCES organization.location (id),
    current_branch_id        uuid        REFERENCES organization.branch (id),
    reserved_for_customer_id uuid,
    reserved_until           timestamptz,
    reserved_by              varchar(100),
    supplier_id              uuid,
    received_date            date,
    sold_date                date,
    owner_customer_id        uuid,
    quality_checked          boolean     NOT NULL DEFAULT false,
    notes                    varchar(500),
    created_at               timestamptz NOT NULL,
    created_by               varchar(100),
    updated_at               timestamptz NOT NULL,
    updated_by               varchar(100),
    CONSTRAINT uq_item_code UNIQUE (item_code),
    CONSTRAINT uq_item_rfid UNIQUE (rfid_tag),
    CONSTRAINT uq_item_qr UNIQUE (qr_code),
    CONSTRAINT uq_item_barcode UNIQUE (barcode)
);

-- High-volume operational lookups: stock by location, by status, by product.
CREATE INDEX ix_item_location_status ON inventory.jewellery_item (current_location_id, status);
CREATE INDEX ix_item_branch_status ON inventory.jewellery_item (current_branch_id, status);
CREATE INDEX ix_item_product ON inventory.jewellery_item (product_id);
CREATE INDEX ix_item_metal_purity ON inventory.jewellery_item (metal_id, purity_id);
CREATE INDEX ix_item_owner ON inventory.jewellery_item (owner_customer_id)
    WHERE owner_customer_id IS NOT NULL;
CREATE INDEX ix_item_reserved ON inventory.jewellery_item (reserved_for_customer_id, reserved_until)
    WHERE reserved_for_customer_id IS NOT NULL;

CREATE TABLE inventory.jewellery_stone (
    id                uuid PRIMARY KEY,
    version           bigint      NOT NULL DEFAULT 0,
    jewellery_item_id uuid        NOT NULL REFERENCES inventory.jewellery_item (id) ON DELETE CASCADE,
    gemstone_id       uuid        NOT NULL REFERENCES product.gemstone (id),
    certificate_id    uuid        REFERENCES product.stone_certificate (id),
    stone_count       integer     NOT NULL DEFAULT 1,
    carat_weight      numeric(10,3) NOT NULL,
    shape             varchar(20),
    cut               varchar(30),
    colour            varchar(30),
    clarity           varchar(30),
    setting_type      varchar(20),
    rate_per_carat    numeric(19,4),
    stone_value       numeric(19,4),
    weight_grams      numeric(12,3),
    notes             varchar(255),
    created_at        timestamptz NOT NULL,
    created_by        varchar(100),
    updated_at        timestamptz NOT NULL,
    updated_by        varchar(100)
);

CREATE INDEX ix_stone_item ON inventory.jewellery_stone (jewellery_item_id);
CREATE INDEX ix_stone_certificate ON inventory.jewellery_stone (certificate_id);

CREATE TABLE inventory.item_lifecycle_event (
    id                uuid PRIMARY KEY,
    jewellery_item_id uuid        NOT NULL REFERENCES inventory.jewellery_item (id) ON DELETE CASCADE,
    event_type        varchar(30)  NOT NULL,
    from_status       varchar(20),
    to_status         varchar(20),
    from_location_id  uuid,
    to_location_id    uuid,
    reference_type    varchar(50),
    reference_id      varchar(100),
    performed_by      varchar(100),
    notes             varchar(500),
    occurred_at       timestamptz NOT NULL
);

CREATE INDEX ix_lifecycle_item ON inventory.item_lifecycle_event (jewellery_item_id, occurred_at);

CREATE TABLE inventory.inventory_movement (
    id                 uuid PRIMARY KEY,
    version            bigint      NOT NULL DEFAULT 0,
    reference_number   varchar(50)  NOT NULL,
    movement_type      varchar(30)  NOT NULL,
    status             varchar(30)  NOT NULL,
    from_location_id   uuid        REFERENCES organization.location (id),
    to_location_id     uuid        REFERENCES organization.location (id),
    from_branch_id     uuid        REFERENCES organization.branch (id),
    to_branch_id       uuid        REFERENCES organization.branch (id),
    requires_approval  boolean     NOT NULL DEFAULT false,
    approved_by        varchar(100),
    approved_at        timestamptz,
    second_approved_by varchar(100),
    second_approved_at timestamptz,
    dispatched_by      varchar(100),
    dispatched_at      timestamptz,
    received_by        varchar(100),
    completed_at       timestamptz,
    rejection_reason   varchar(500),
    external_reference varchar(100),
    notes              varchar(500),
    created_at         timestamptz NOT NULL,
    created_by         varchar(100),
    updated_at         timestamptz NOT NULL,
    updated_by         varchar(100),
    CONSTRAINT uq_movement_reference UNIQUE (reference_number)
);

-- Backs the idempotency check on movement creation.
CREATE UNIQUE INDEX uq_movement_external_reference
    ON inventory.inventory_movement (external_reference)
    WHERE external_reference IS NOT NULL;
CREATE INDEX ix_movement_status ON inventory.inventory_movement (status, created_at DESC);
CREATE INDEX ix_movement_from ON inventory.inventory_movement (from_location_id);
CREATE INDEX ix_movement_to ON inventory.inventory_movement (to_location_id);

CREATE TABLE inventory.inventory_movement_line (
    id                uuid PRIMARY KEY,
    version           bigint      NOT NULL DEFAULT 0,
    movement_id       uuid        NOT NULL REFERENCES inventory.inventory_movement (id) ON DELETE CASCADE,
    jewellery_item_id uuid        NOT NULL REFERENCES inventory.jewellery_item (id),
    item_code         varchar(50)  NOT NULL,
    dispatched_weight numeric(12,3),
    received_weight   numeric(12,3),
    received          boolean     NOT NULL DEFAULT false,
    discrepancy_note  varchar(500),
    created_at        timestamptz NOT NULL,
    created_by        varchar(100),
    updated_at        timestamptz NOT NULL,
    updated_by        varchar(100),
    CONSTRAINT uq_movement_line_item UNIQUE (movement_id, jewellery_item_id)
);

CREATE INDEX ix_movement_line_item ON inventory.inventory_movement_line (jewellery_item_id);
