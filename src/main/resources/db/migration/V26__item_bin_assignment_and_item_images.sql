-- 1. An item's storage bin.
--
-- `storage_bin` and `stock_count_line.bin_id` already existed, so a stock count
-- could record where an item was *found* — but the item itself had no bin, so
-- there was nothing to compare that against and no way to say where a piece
-- belongs. Bins could be created and listed and never filled.
ALTER TABLE inventory.jewellery_item
    ADD COLUMN bin_id uuid REFERENCES inventory.storage_bin (id);

-- Backs "what is in this tray?", the question the vault screen exists to answer.
CREATE INDEX ix_jewellery_item_bin ON inventory.jewellery_item (bin_id);

-- 2. Bin codes are unique per location, not globally.
--
-- The same mistake organization.location had: every vault naturally wants a
-- TRAY-1, so the second one onward could not use a natural code.
ALTER TABLE inventory.storage_bin
    DROP CONSTRAINT uq_storage_bin_code;

ALTER TABLE inventory.storage_bin
    ADD CONSTRAINT uq_storage_bin_location_code UNIQUE (location_id, code);

-- 3. Item images.
--
-- `product.product_image` already exists and is already mapped on the Product
-- entity — it was simply never exposed through the API. This mirrors it rather
-- than inventing a second shape, because the two answer different questions:
-- a product image is catalogue artwork shared by every item made to that
-- product, while an item image is *this* physical piece, which is what a member
-- of staff needs when identifying stock in a tray.
--
-- Only the storage reference is held here; the binary stays in MinIO behind
-- /api/v1/files, the same as every other upload.
CREATE TABLE inventory.item_image (
    id                uuid PRIMARY KEY,
    version           bigint      NOT NULL DEFAULT 0,
    jewellery_item_id uuid        NOT NULL REFERENCES inventory.jewellery_item (id) ON DELETE CASCADE,
    storage_key       varchar(500) NOT NULL,
    file_name         varchar(255),
    content_type      varchar(100),
    size_bytes        bigint,
    primary_image     boolean     NOT NULL DEFAULT false,
    display_order     integer,
    created_at        timestamptz NOT NULL,
    created_by        varchar(100),
    updated_at        timestamptz NOT NULL,
    updated_by        varchar(100)
);

CREATE INDEX ix_item_image_item ON inventory.item_image (jewellery_item_id, display_order);

-- At most one primary image per item: the passport header has room for exactly
-- one, and "whichever comes back first" is not an answer.
CREATE UNIQUE INDEX uq_item_image_primary
    ON inventory.item_image (jewellery_item_id)
    WHERE primary_image;
