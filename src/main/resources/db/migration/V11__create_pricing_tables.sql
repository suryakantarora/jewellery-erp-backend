CREATE TABLE sales.making_charge_rule (
    id                 uuid PRIMARY KEY,
    version            bigint      NOT NULL DEFAULT 0,
    code               varchar(40)  NOT NULL,
    name               varchar(150) NOT NULL,
    product_id         uuid        REFERENCES product.product (id),
    product_type_id    uuid        REFERENCES product.product_type (id),
    metal_id           uuid        REFERENCES product.metal (id),
    purity_id          uuid        REFERENCES product.purity (id),
    branch_id          uuid        REFERENCES organization.branch (id),
    charge_type        varchar(30)  NOT NULL,
    charge_value       numeric(15,4) NOT NULL CHECK (charge_value >= 0),
    wastage_percentage numeric(7,4) CHECK (wastage_percentage >= 0),
    min_charge         numeric(19,4),
    max_charge         numeric(19,4),
    effective_from     date        NOT NULL,
    effective_to       date,
    priority           integer     NOT NULL DEFAULT 0,
    active             boolean     NOT NULL DEFAULT true,
    created_at         timestamptz NOT NULL,
    created_by         varchar(100),
    updated_at         timestamptz NOT NULL,
    updated_by         varchar(100),
    CONSTRAINT uq_making_charge_rule_code UNIQUE (code),
    CONSTRAINT ck_making_charge_window CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

-- Backs the candidate-rule lookup done on every price calculation.
CREATE INDEX ix_making_charge_lookup
    ON sales.making_charge_rule (active, effective_from, effective_to);

CREATE TABLE sales.tax_rate (
    id              uuid PRIMARY KEY,
    version         bigint      NOT NULL DEFAULT 0,
    code            varchar(30)  NOT NULL,
    name            varchar(100) NOT NULL,
    percentage      numeric(7,4) NOT NULL CHECK (percentage >= 0),
    branch_id       uuid        REFERENCES organization.branch (id),
    product_type_id uuid        REFERENCES product.product_type (id),
    inclusive       boolean     NOT NULL DEFAULT false,
    effective_from  date        NOT NULL,
    effective_to    date,
    active          boolean     NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL,
    created_by      varchar(100),
    updated_at      timestamptz NOT NULL,
    updated_by      varchar(100),
    CONSTRAINT ck_tax_window CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE INDEX ix_tax_rate_lookup ON sales.tax_rate (active, effective_from, effective_to);

CREATE TABLE sales.discount_policy (
    id                              uuid PRIMARY KEY,
    version                         bigint      NOT NULL DEFAULT 0,
    code                            varchar(40)  NOT NULL,
    name                            varchar(150) NOT NULL,
    branch_id                       uuid        REFERENCES organization.branch (id),
    max_percentage_without_approval numeric(7,4) NOT NULL DEFAULT 0,
    max_percentage_with_approval    numeric(7,4) NOT NULL DEFAULT 0,
    applies_to_making_charge        boolean     NOT NULL DEFAULT true,
    applies_to_metal_value          boolean     NOT NULL DEFAULT false,
    effective_from                  date        NOT NULL,
    effective_to                    date,
    active                          boolean     NOT NULL DEFAULT true,
    created_at                      timestamptz NOT NULL,
    created_by                      varchar(100),
    updated_at                      timestamptz NOT NULL,
    updated_by                      varchar(100),
    CONSTRAINT uq_discount_policy_code UNIQUE (code),
    CONSTRAINT ck_discount_limits CHECK (max_percentage_without_approval <= max_percentage_with_approval),
    CONSTRAINT ck_discount_window CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE INDEX ix_discount_policy_lookup ON sales.discount_policy (active, branch_id);
