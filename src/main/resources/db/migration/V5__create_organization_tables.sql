CREATE TABLE organization.company (
    id                  uuid PRIMARY KEY,
    version             bigint      NOT NULL DEFAULT 0,
    code                varchar(30)  NOT NULL,
    name                varchar(150) NOT NULL,
    legal_name          varchar(200),
    tax_number          varchar(50),
    registration_number varchar(50),
    base_currency       varchar(3)   NOT NULL DEFAULT 'LAK',
    address_line        varchar(255),
    city                varchar(100),
    country             varchar(100),
    phone               varchar(30),
    email               varchar(150),
    status              varchar(20)  NOT NULL,
    created_at          timestamptz NOT NULL,
    created_by          varchar(100),
    updated_at          timestamptz NOT NULL,
    updated_by          varchar(100),
    CONSTRAINT uq_company_code UNIQUE (code)
);

CREATE TABLE organization.branch (
    id           uuid PRIMARY KEY,
    version      bigint      NOT NULL DEFAULT 0,
    company_id   uuid        NOT NULL REFERENCES organization.company (id),
    code         varchar(30)  NOT NULL,
    name         varchar(150) NOT NULL,
    head_office  boolean     NOT NULL DEFAULT false,
    address_line varchar(255),
    city         varchar(100),
    country      varchar(100),
    phone        varchar(30),
    email        varchar(150),
    timezone     varchar(50),
    status       varchar(20)  NOT NULL,
    created_at   timestamptz NOT NULL,
    created_by   varchar(100),
    updated_at   timestamptz NOT NULL,
    updated_by   varchar(100),
    CONSTRAINT uq_branch_code UNIQUE (code)
);

CREATE INDEX ix_branch_company ON organization.branch (company_id);

CREATE TABLE organization.location (
    id                 uuid PRIMARY KEY,
    version            bigint      NOT NULL DEFAULT 0,
    branch_id          uuid        NOT NULL REFERENCES organization.branch (id),
    parent_id          uuid        REFERENCES organization.location (id),
    code               varchar(40)  NOT NULL,
    name               varchar(150) NOT NULL,
    type               varchar(30)  NOT NULL,
    dual_authorization boolean     NOT NULL DEFAULT false,
    description        varchar(255),
    status             varchar(20)  NOT NULL,
    created_at         timestamptz NOT NULL,
    created_by         varchar(100),
    updated_at         timestamptz NOT NULL,
    updated_by         varchar(100),
    CONSTRAINT uq_location_code UNIQUE (code)
);

CREATE INDEX ix_location_branch ON organization.location (branch_id);
CREATE INDEX ix_location_parent ON organization.location (parent_id);
CREATE INDEX ix_location_type ON organization.location (branch_id, type);
