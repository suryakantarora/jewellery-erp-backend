CREATE TABLE product.gemstone (
    id          uuid PRIMARY KEY,
    version     bigint      NOT NULL DEFAULT 0,
    code        varchar(30)  NOT NULL,
    name        varchar(100) NOT NULL,
    diamond     boolean     NOT NULL DEFAULT false,
    precious    boolean     NOT NULL DEFAULT false,
    description varchar(255),
    active      boolean     NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL,
    created_by  varchar(100),
    updated_at  timestamptz NOT NULL,
    updated_by  varchar(100),
    CONSTRAINT uq_gemstone_code UNIQUE (code)
);

CREATE TABLE product.stone_certificate (
    id                 uuid PRIMARY KEY,
    version            bigint      NOT NULL DEFAULT 0,
    certificate_number varchar(100) NOT NULL,
    issuing_lab        varchar(100) NOT NULL,
    issue_date         date,
    storage_key        varchar(500),
    verification_url   varchar(500),
    notes              varchar(255),
    created_at         timestamptz NOT NULL,
    created_by         varchar(100),
    updated_at         timestamptz NOT NULL,
    updated_by         varchar(100),
    CONSTRAINT uq_certificate_number UNIQUE (certificate_number)
);
