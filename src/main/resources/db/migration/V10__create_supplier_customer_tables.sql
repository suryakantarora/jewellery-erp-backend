CREATE TABLE procurement.supplier (
    id                 uuid PRIMARY KEY,
    version            bigint      NOT NULL DEFAULT 0,
    code               varchar(30)  NOT NULL,
    name               varchar(200) NOT NULL,
    legal_name         varchar(200),
    tax_number         varchar(50),
    supplier_type      varchar(30),
    address_line       varchar(255),
    city               varchar(100),
    country            varchar(100),
    phone              varchar(30),
    email              varchar(150),
    currency           varchar(3)   NOT NULL DEFAULT 'LAK',
    payment_terms_days integer,
    credit_limit       numeric(19,4),
    status             varchar(20)  NOT NULL,
    notes              varchar(500),
    created_at         timestamptz NOT NULL,
    created_by         varchar(100),
    updated_at         timestamptz NOT NULL,
    updated_by         varchar(100),
    CONSTRAINT uq_supplier_code UNIQUE (code)
);

CREATE INDEX ix_supplier_name ON procurement.supplier (lower(name));
CREATE INDEX ix_supplier_status ON procurement.supplier (status);

CREATE TABLE procurement.supplier_contact (
    id              uuid PRIMARY KEY,
    version         bigint      NOT NULL DEFAULT 0,
    supplier_id     uuid        NOT NULL REFERENCES procurement.supplier (id) ON DELETE CASCADE,
    name            varchar(150) NOT NULL,
    designation     varchar(100),
    phone           varchar(30),
    email           varchar(150),
    primary_contact boolean     NOT NULL DEFAULT false,
    created_at      timestamptz NOT NULL,
    created_by      varchar(100),
    updated_at      timestamptz NOT NULL,
    updated_by      varchar(100)
);

CREATE INDEX ix_supplier_contact ON procurement.supplier_contact (supplier_id);

CREATE TABLE procurement.supplier_bank_account (
    id              uuid PRIMARY KEY,
    version         bigint      NOT NULL DEFAULT 0,
    supplier_id     uuid        NOT NULL REFERENCES procurement.supplier (id) ON DELETE CASCADE,
    bank_name       varchar(150) NOT NULL,
    account_name    varchar(150) NOT NULL,
    account_number  varchar(50)  NOT NULL,
    branch_name     varchar(150),
    swift_code      varchar(20),
    currency        varchar(3),
    primary_account boolean     NOT NULL DEFAULT false,
    created_at      timestamptz NOT NULL,
    created_by      varchar(100),
    updated_at      timestamptz NOT NULL,
    updated_by      varchar(100)
);

CREATE INDEX ix_supplier_bank ON procurement.supplier_bank_account (supplier_id);

CREATE TABLE procurement.supplier_document (
    id              uuid PRIMARY KEY,
    version         bigint      NOT NULL DEFAULT 0,
    supplier_id     uuid        NOT NULL REFERENCES procurement.supplier (id) ON DELETE CASCADE,
    document_type   varchar(50)  NOT NULL,
    document_number varchar(100),
    storage_key     varchar(500) NOT NULL,
    file_name       varchar(255),
    issue_date      date,
    expiry_date     date,
    created_at      timestamptz NOT NULL,
    created_by      varchar(100),
    updated_at      timestamptz NOT NULL,
    updated_by      varchar(100)
);

CREATE INDEX ix_supplier_document ON procurement.supplier_document (supplier_id);

-- ---------- customer ----------

CREATE TABLE customer.customer (
    id                   uuid PRIMARY KEY,
    version              bigint      NOT NULL DEFAULT 0,
    customer_code        varchar(30)  NOT NULL,
    customer_type        varchar(20)  NOT NULL,
    full_name            varchar(200) NOT NULL,
    company_name         varchar(200),
    phone                varchar(30)  NOT NULL,
    alternate_phone      varchar(30),
    email                varchar(150),
    date_of_birth        date,
    anniversary_date     date,
    gender               varchar(20),
    tax_number           varchar(50),
    registered_branch_id uuid        REFERENCES organization.branch (id),
    kyc_status           varchar(20)  NOT NULL,
    kyc_verified_at      date,
    kyc_verified_by      varchar(100),
    status               varchar(20)  NOT NULL,
    notes                varchar(500),
    created_at           timestamptz NOT NULL,
    created_by           varchar(100),
    updated_at           timestamptz NOT NULL,
    updated_by           varchar(100),
    CONSTRAINT uq_customer_code UNIQUE (customer_code),
    CONSTRAINT uq_customer_phone UNIQUE (phone)
);

CREATE INDEX ix_customer_name ON customer.customer (lower(full_name));
CREATE INDEX ix_customer_status ON customer.customer (status);
CREATE INDEX ix_customer_kyc ON customer.customer (kyc_status);
CREATE INDEX ix_customer_branch ON customer.customer (registered_branch_id);

CREATE TABLE customer.customer_address (
    id              uuid PRIMARY KEY,
    version         bigint      NOT NULL DEFAULT 0,
    customer_id     uuid        NOT NULL REFERENCES customer.customer (id) ON DELETE CASCADE,
    address_type    varchar(20),
    address_line1   varchar(255) NOT NULL,
    address_line2   varchar(255),
    city            varchar(100),
    province        varchar(100),
    postal_code     varchar(20),
    country         varchar(100),
    default_address boolean     NOT NULL DEFAULT false,
    created_at      timestamptz NOT NULL,
    created_by      varchar(100),
    updated_at      timestamptz NOT NULL,
    updated_by      varchar(100)
);

CREATE INDEX ix_customer_address ON customer.customer_address (customer_id);

CREATE TABLE customer.customer_document (
    id              uuid PRIMARY KEY,
    version         bigint      NOT NULL DEFAULT 0,
    customer_id     uuid        NOT NULL REFERENCES customer.customer (id) ON DELETE CASCADE,
    document_type   varchar(50)  NOT NULL,
    document_number varchar(100) NOT NULL,
    storage_key     varchar(500),
    file_name       varchar(255),
    issue_date      date,
    expiry_date     date,
    verified        boolean     NOT NULL DEFAULT false,
    created_at      timestamptz NOT NULL,
    created_by      varchar(100),
    updated_at      timestamptz NOT NULL,
    updated_by      varchar(100)
);

CREATE INDEX ix_customer_document ON customer.customer_document (customer_id);

CREATE TABLE customer.customer_preference (
    id               uuid PRIMARY KEY,
    version          bigint      NOT NULL DEFAULT 0,
    customer_id      uuid        NOT NULL REFERENCES customer.customer (id) ON DELETE CASCADE,
    preference_key   varchar(50)  NOT NULL,
    preference_value varchar(255),
    created_at       timestamptz NOT NULL,
    created_by       varchar(100),
    updated_at       timestamptz NOT NULL,
    updated_by       varchar(100),
    CONSTRAINT uq_customer_preference UNIQUE (customer_id, preference_key)
);
