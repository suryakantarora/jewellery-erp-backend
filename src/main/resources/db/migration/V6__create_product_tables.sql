CREATE TABLE product.product_category (
    id            uuid PRIMARY KEY,
    version       bigint      NOT NULL DEFAULT 0,
    code          varchar(40)  NOT NULL,
    name          varchar(150) NOT NULL,
    parent_id     uuid REFERENCES product.product_category (id),
    description   varchar(255),
    display_order integer,
    status        varchar(20)  NOT NULL,
    created_at    timestamptz NOT NULL,
    created_by    varchar(100),
    updated_at    timestamptz NOT NULL,
    updated_by    varchar(100),
    CONSTRAINT uq_product_category_code UNIQUE (code)
);

CREATE TABLE product.product_type (
    id          uuid PRIMARY KEY,
    version     bigint      NOT NULL DEFAULT 0,
    code        varchar(40)  NOT NULL,
    name        varchar(150) NOT NULL,
    category_id uuid REFERENCES product.product_category (id),
    sizeable    boolean     NOT NULL DEFAULT false,
    description varchar(255),
    status      varchar(20)  NOT NULL,
    created_at  timestamptz NOT NULL,
    created_by  varchar(100),
    updated_at  timestamptz NOT NULL,
    updated_by  varchar(100),
    CONSTRAINT uq_product_type_code UNIQUE (code)
);

CREATE TABLE product.brand (
    id          uuid PRIMARY KEY,
    version     bigint      NOT NULL DEFAULT 0,
    code        varchar(40)  NOT NULL,
    name        varchar(150) NOT NULL,
    description varchar(255),
    status      varchar(20)  NOT NULL,
    created_at  timestamptz NOT NULL,
    created_by  varchar(100),
    updated_at  timestamptz NOT NULL,
    updated_by  varchar(100),
    CONSTRAINT uq_brand_code UNIQUE (code)
);

CREATE TABLE product.collection (
    id          uuid PRIMARY KEY,
    version     bigint      NOT NULL DEFAULT 0,
    code        varchar(40)  NOT NULL,
    name        varchar(150) NOT NULL,
    description varchar(255),
    status      varchar(20)  NOT NULL,
    created_at  timestamptz NOT NULL,
    created_by  varchar(100),
    updated_at  timestamptz NOT NULL,
    updated_by  varchar(100),
    CONSTRAINT uq_collection_code UNIQUE (code)
);

CREATE TABLE product.jewellery_design (
    id                   uuid PRIMARY KEY,
    version              bigint      NOT NULL DEFAULT 0,
    design_code          varchar(50)  NOT NULL,
    name                 varchar(150) NOT NULL,
    product_type_id      uuid REFERENCES product.product_type (id),
    collection_id        uuid REFERENCES product.collection (id),
    brand_id             uuid REFERENCES product.brand (id),
    designer             varchar(150),
    nominal_gross_weight numeric(12,3),
    description          text,
    status               varchar(20)  NOT NULL,
    created_at           timestamptz NOT NULL,
    created_by           varchar(100),
    updated_at           timestamptz NOT NULL,
    updated_by           varchar(100),
    CONSTRAINT uq_jewellery_design_code UNIQUE (design_code)
);

CREATE TABLE product.product (
    id                          uuid PRIMARY KEY,
    version                     bigint      NOT NULL DEFAULT 0,
    sku                         varchar(50)  NOT NULL,
    name                        varchar(200) NOT NULL,
    design_id                   uuid REFERENCES product.jewellery_design (id),
    product_type_id             uuid        NOT NULL REFERENCES product.product_type (id),
    category_id                 uuid REFERENCES product.product_category (id),
    brand_id                    uuid REFERENCES product.brand (id),
    collection_id               uuid REFERENCES product.collection (id),
    default_metal_id            uuid,
    default_purity_id           uuid,
    nominal_gross_weight        numeric(12,3),
    default_making_charge_type  varchar(20),
    default_making_charge_value numeric(15,4),
    default_wastage_percentage  numeric(7,4),
    hsn_code                    varchar(30),
    description                 text,
    status                      varchar(20)  NOT NULL,
    created_at                  timestamptz NOT NULL,
    created_by                  varchar(100),
    updated_at                  timestamptz NOT NULL,
    updated_by                  varchar(100),
    CONSTRAINT uq_product_sku UNIQUE (sku)
);

CREATE INDEX ix_product_type ON product.product (product_type_id);
CREATE INDEX ix_product_category ON product.product (category_id);
CREATE INDEX ix_product_design ON product.product (design_id);
CREATE INDEX ix_product_name ON product.product (lower(name));

CREATE TABLE product.product_image (
    id            uuid PRIMARY KEY,
    version       bigint      NOT NULL DEFAULT 0,
    product_id    uuid        NOT NULL REFERENCES product.product (id) ON DELETE CASCADE,
    storage_key   varchar(500) NOT NULL,
    file_name     varchar(255),
    content_type  varchar(100),
    size_bytes    bigint,
    primary_image boolean     NOT NULL DEFAULT false,
    display_order integer,
    created_at    timestamptz NOT NULL,
    created_by    varchar(100),
    updated_at    timestamptz NOT NULL,
    updated_by    varchar(100)
);

CREATE INDEX ix_product_image_product ON product.product_image (product_id);

CREATE TABLE product.size (
    id              uuid PRIMARY KEY,
    version         bigint      NOT NULL DEFAULT 0,
    product_type_id uuid        NOT NULL REFERENCES product.product_type (id),
    code            varchar(30)  NOT NULL,
    label           varchar(50)  NOT NULL,
    standard        varchar(30),
    display_order   integer,
    status          varchar(20)  NOT NULL,
    created_at      timestamptz NOT NULL,
    created_by      varchar(100),
    updated_at      timestamptz NOT NULL,
    updated_by      varchar(100),
    CONSTRAINT uq_size_type_code UNIQUE (product_type_id, code)
);
