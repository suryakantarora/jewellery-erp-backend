CREATE TABLE procurement.purchase_requisition (
    id               uuid PRIMARY KEY,
    version          bigint      NOT NULL DEFAULT 0,
    reference_number varchar(50)  NOT NULL,
    branch_id        uuid        NOT NULL REFERENCES organization.branch (id),
    status           varchar(30)  NOT NULL,
    required_by      date,
    justification    varchar(500),
    approved_by      varchar(100),
    approved_at      timestamptz,
    rejection_reason varchar(500),
    created_at       timestamptz NOT NULL,
    created_by       varchar(100),
    updated_at       timestamptz NOT NULL,
    updated_by       varchar(100),
    CONSTRAINT uq_requisition_reference UNIQUE (reference_number)
);

CREATE INDEX ix_requisition_status ON procurement.purchase_requisition (status, branch_id);

CREATE TABLE procurement.purchase_requisition_line (
    id               uuid PRIMARY KEY,
    version          bigint      NOT NULL DEFAULT 0,
    requisition_id   uuid        NOT NULL REFERENCES procurement.purchase_requisition (id) ON DELETE CASCADE,
    product_id       uuid        NOT NULL REFERENCES product.product (id),
    quantity         integer     NOT NULL CHECK (quantity > 0),
    estimated_weight numeric(12,3),
    notes            varchar(255),
    created_at       timestamptz NOT NULL,
    created_by       varchar(100),
    updated_at       timestamptz NOT NULL,
    updated_by       varchar(100)
);

CREATE INDEX ix_requisition_line ON procurement.purchase_requisition_line (requisition_id);

CREATE TABLE procurement.purchase_order (
    id                     uuid PRIMARY KEY,
    version                bigint      NOT NULL DEFAULT 0,
    order_number           varchar(50)  NOT NULL,
    supplier_id            uuid        NOT NULL REFERENCES procurement.supplier (id),
    branch_id              uuid        NOT NULL REFERENCES organization.branch (id),
    delivery_location_id   uuid        NOT NULL REFERENCES organization.location (id),
    requisition_id         uuid        REFERENCES procurement.purchase_requisition (id),
    status                 varchar(30)  NOT NULL,
    order_date             date        NOT NULL,
    expected_delivery_date date,
    currency               varchar(3)   NOT NULL DEFAULT 'LAK',
    estimated_total        numeric(19,4),
    approved_by            varchar(100),
    approved_at            timestamptz,
    rejection_reason       varchar(500),
    external_reference     varchar(100),
    notes                  varchar(500),
    created_at             timestamptz NOT NULL,
    created_by             varchar(100),
    updated_at             timestamptz NOT NULL,
    updated_by             varchar(100),
    CONSTRAINT uq_purchase_order_number UNIQUE (order_number)
);

CREATE UNIQUE INDEX uq_purchase_order_external
    ON procurement.purchase_order (external_reference)
    WHERE external_reference IS NOT NULL;
CREATE INDEX ix_purchase_order_status ON procurement.purchase_order (status, branch_id);
CREATE INDEX ix_purchase_order_supplier ON procurement.purchase_order (supplier_id);

CREATE TABLE procurement.purchase_order_line (
    id                     uuid PRIMARY KEY,
    version                bigint      NOT NULL DEFAULT 0,
    purchase_order_id      uuid        NOT NULL REFERENCES procurement.purchase_order (id) ON DELETE CASCADE,
    product_id             uuid        NOT NULL REFERENCES product.product (id),
    metal_id               uuid        REFERENCES product.metal (id),
    purity_id              uuid        REFERENCES product.purity (id),
    ordered_quantity       integer     NOT NULL CHECK (ordered_quantity > 0),
    received_quantity      integer     NOT NULL DEFAULT 0 CHECK (received_quantity >= 0),
    estimated_weight       numeric(12,3),
    rate_per_gram          numeric(19,4),
    making_charge_per_unit numeric(19,4),
    line_total             numeric(19,4),
    notes                  varchar(255),
    created_at             timestamptz NOT NULL,
    created_by             varchar(100),
    updated_at             timestamptz NOT NULL,
    updated_by             varchar(100),
    CONSTRAINT ck_received_not_over_ordered CHECK (received_quantity <= ordered_quantity)
);

CREATE INDEX ix_purchase_order_line ON procurement.purchase_order_line (purchase_order_id);

CREATE TABLE procurement.goods_receipt (
    id                    uuid PRIMARY KEY,
    version               bigint      NOT NULL DEFAULT 0,
    receipt_number        varchar(50)  NOT NULL,
    purchase_order_id     uuid        NOT NULL REFERENCES procurement.purchase_order (id),
    supplier_id           uuid        NOT NULL REFERENCES procurement.supplier (id),
    location_id           uuid        NOT NULL REFERENCES organization.location (id),
    branch_id             uuid        NOT NULL REFERENCES organization.branch (id),
    status                varchar(30)  NOT NULL,
    receipt_date          date        NOT NULL,
    supplier_delivery_note varchar(100),
    quality_checked_by    varchar(100),
    quality_checked_at    timestamptz,
    rejection_reason      varchar(500),
    external_reference    varchar(100),
    notes                 varchar(500),
    created_at            timestamptz NOT NULL,
    created_by            varchar(100),
    updated_at            timestamptz NOT NULL,
    updated_by            varchar(100),
    CONSTRAINT uq_goods_receipt_number UNIQUE (receipt_number)
);

CREATE UNIQUE INDEX uq_goods_receipt_external
    ON procurement.goods_receipt (external_reference)
    WHERE external_reference IS NOT NULL;
CREATE INDEX ix_goods_receipt_order ON procurement.goods_receipt (purchase_order_id);
CREATE INDEX ix_goods_receipt_status ON procurement.goods_receipt (status, branch_id);

CREATE TABLE procurement.goods_receipt_line (
    id                     uuid PRIMARY KEY,
    version                bigint      NOT NULL DEFAULT 0,
    goods_receipt_id       uuid        NOT NULL REFERENCES procurement.goods_receipt (id) ON DELETE CASCADE,
    purchase_order_line_id uuid        NOT NULL REFERENCES procurement.purchase_order_line (id),
    product_id             uuid        NOT NULL REFERENCES product.product (id),
    metal_id               uuid        REFERENCES product.metal (id),
    purity_id              uuid        REFERENCES product.purity (id),
    gross_weight           numeric(12,3) NOT NULL CHECK (gross_weight > 0),
    stone_weight           numeric(12,3),
    purchase_cost          numeric(19,4),
    making_cost            numeric(19,4),
    stone_cost             numeric(19,4),
    hallmark_number        varchar(50),
    barcode                varchar(100),
    rfid_tag               varchar(100),
    jewellery_item_id      uuid        REFERENCES inventory.jewellery_item (id),
    notes                  varchar(255),
    created_at             timestamptz NOT NULL,
    created_by             varchar(100),
    updated_at             timestamptz NOT NULL,
    updated_by             varchar(100)
);

CREATE INDEX ix_goods_receipt_line ON procurement.goods_receipt_line (goods_receipt_id);
CREATE INDEX ix_goods_receipt_line_item ON procurement.goods_receipt_line (jewellery_item_id);

CREATE TABLE procurement.supplier_invoice (
    id                uuid PRIMARY KEY,
    version           bigint      NOT NULL DEFAULT 0,
    invoice_number    varchar(100) NOT NULL,
    supplier_id       uuid        NOT NULL REFERENCES procurement.supplier (id),
    purchase_order_id uuid        REFERENCES procurement.purchase_order (id),
    goods_receipt_id  uuid        REFERENCES procurement.goods_receipt (id),
    invoice_date      date        NOT NULL,
    due_date          date,
    currency          varchar(3)   NOT NULL DEFAULT 'LAK',
    sub_total         numeric(19,4) NOT NULL DEFAULT 0,
    tax_amount        numeric(19,4) DEFAULT 0,
    total_amount      numeric(19,4) NOT NULL DEFAULT 0,
    paid_amount       numeric(19,4) NOT NULL DEFAULT 0,
    status            varchar(30)  NOT NULL,
    notes             varchar(500),
    created_at        timestamptz NOT NULL,
    created_by        varchar(100),
    updated_at        timestamptz NOT NULL,
    updated_by        varchar(100),
    -- The same supplier cannot bill the same invoice number twice.
    CONSTRAINT uq_supplier_invoice UNIQUE (supplier_id, invoice_number)
);

CREATE INDEX ix_supplier_invoice_status ON procurement.supplier_invoice (status, due_date);
