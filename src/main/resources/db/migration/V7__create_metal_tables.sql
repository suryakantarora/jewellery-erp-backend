CREATE TABLE product.metal (
    id          uuid PRIMARY KEY,
    version     bigint      NOT NULL DEFAULT 0,
    code        varchar(30)  NOT NULL,
    name        varchar(100) NOT NULL,
    symbol      varchar(10),
    weight_unit varchar(10)  NOT NULL DEFAULT 'GRAM',
    description varchar(255),
    active      boolean     NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL,
    created_by  varchar(100),
    updated_at  timestamptz NOT NULL,
    updated_by  varchar(100),
    CONSTRAINT uq_metal_code UNIQUE (code)
);

CREATE TABLE product.purity (
    id            uuid PRIMARY KEY,
    version       bigint      NOT NULL DEFAULT 0,
    metal_id      uuid        NOT NULL REFERENCES product.metal (id),
    code          varchar(20)  NOT NULL,
    name          varchar(50)  NOT NULL,
    fineness      numeric(9,6) NOT NULL CHECK (fineness > 0 AND fineness <= 1),
    display_order integer,
    active        boolean     NOT NULL DEFAULT true,
    created_at    timestamptz NOT NULL,
    created_by    varchar(100),
    updated_at    timestamptz NOT NULL,
    updated_by    varchar(100),
    CONSTRAINT uq_purity_metal_code UNIQUE (metal_id, code)
);

CREATE TABLE product.metal_rate (
    id             uuid PRIMARY KEY,
    version        bigint      NOT NULL DEFAULT 0,
    metal_id       uuid        NOT NULL REFERENCES product.metal (id),
    purity_id      uuid        NOT NULL REFERENCES product.purity (id),
    rate_type      varchar(20)  NOT NULL,
    effective_date date        NOT NULL,
    rate_per_unit  numeric(19,4) NOT NULL CHECK (rate_per_unit > 0),
    currency       varchar(3)   NOT NULL DEFAULT 'LAK',
    branch_id      uuid,
    published_at   timestamptz NOT NULL,
    published_by   varchar(100),
    notes          varchar(255),
    created_at     timestamptz NOT NULL,
    created_by     varchar(100),
    updated_at     timestamptz NOT NULL,
    updated_by     varchar(100)
);

-- One rate per metal/purity/type/date/branch; company-wide rows share the NULL branch.
CREATE UNIQUE INDEX uq_metal_rate_branch
    ON product.metal_rate (metal_id, purity_id, rate_type, effective_date, branch_id)
    WHERE branch_id IS NOT NULL;
CREATE UNIQUE INDEX uq_metal_rate_global
    ON product.metal_rate (metal_id, purity_id, rate_type, effective_date)
    WHERE branch_id IS NULL;
CREATE INDEX ix_metal_rate_lookup
    ON product.metal_rate (metal_id, purity_id, rate_type, effective_date DESC);

CREATE TABLE inventory.scrap_metal (
    id               uuid PRIMARY KEY,
    version          bigint      NOT NULL DEFAULT 0,
    batch_number     varchar(50)  NOT NULL,
    metal_id         uuid        NOT NULL REFERENCES product.metal (id),
    purity_id        uuid        REFERENCES product.purity (id),
    gross_weight     numeric(12,3) NOT NULL,
    pure_weight      numeric(12,3) NOT NULL,
    location_id      uuid        NOT NULL REFERENCES organization.location (id),
    source           varchar(30),
    source_reference varchar(100),
    received_date    date        NOT NULL,
    valuation_amount numeric(19,4),
    status           varchar(20)  NOT NULL,
    notes            varchar(255),
    created_at       timestamptz NOT NULL,
    created_by       varchar(100),
    updated_at       timestamptz NOT NULL,
    updated_by       varchar(100),
    CONSTRAINT uq_scrap_batch UNIQUE (batch_number)
);

CREATE INDEX ix_scrap_metal_location ON inventory.scrap_metal (location_id, status);
