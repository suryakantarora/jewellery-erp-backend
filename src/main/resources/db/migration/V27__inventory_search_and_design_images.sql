-- Design images.
--
-- V26 gave physical items their own photographs and noted that
-- `product.product_image` had been mapped but never exposed. Designs had
-- nothing at all, yet a design is the thing a customer points at in a
-- catalogue and a workshop makes to — "show me what this design looks like" is
-- asked before any item of it exists. Same shape as item_image and
-- product_image so the three are served by one client component.
CREATE TABLE product.design_image (
    id            uuid PRIMARY KEY,
    version       bigint      NOT NULL DEFAULT 0,
    design_id     uuid        NOT NULL REFERENCES product.jewellery_design (id) ON DELETE CASCADE,
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

CREATE INDEX ix_design_image_design ON product.design_image (design_id, display_order);

-- One primary per design, for the same reason as uq_item_image_primary.
CREATE UNIQUE INDEX uq_design_image_primary
    ON product.design_image (design_id)
    WHERE primary_image;

-- product_image predates that rule. Nothing has ever written to the table
-- through the API, so the index can be added without a data fix-up.
CREATE UNIQUE INDEX uq_product_image_primary
    ON product.product_image (product_id)
    WHERE primary_image;

-- Price-range search on the item list ("rings under 5M kip") filters on
-- current_price; without an index it is a full scan of the whole stock table.
CREATE INDEX ix_jewellery_item_current_price ON inventory.jewellery_item (current_price);
