-- Company tenancy.
--
-- Until now the branch was the only authorization boundary and the company was
-- a label on it: product master data, metals, gemstones, customers, suppliers
-- and users had no company column at all, so a second company on the same
-- platform would have read (and shared) the first one's catalogue. This
-- migration gives every company-owned master table a company_id, backfills the
-- rows that exist to the single company in use today, and then makes the
-- column mandatory. Codes that used to be unique across the platform become
-- unique per company — the same lesson as location and bin codes: every
-- company naturally wants its own "GOLD", "DIA" or "SUP-001".
--
-- What is scoped here: identity.app_user, product.product,
-- product.product_category, product.jewellery_design, product.metal,
-- product.gemstone, customer.customer, procurement.supplier.
--
-- What is not: product_type, brand, collection and size stay platform-wide
-- lookup lists (they are referenced by products but carry no business data);
-- purity is a child of metal and inherits its company; stone_certificate hangs
-- off the item; inventory items and every transaction already carry a branch,
-- and the branch carries the company.
--
-- Gemstone is scoped, not shared, for the same reason metal is: the list is
-- what a company's staff pick from when setting stones, its codes are the
-- company's own vocabulary, and a shared list would let one tenant rename or
-- deactivate what another tenant's items refer to.

-- The company every existing row belongs to: the oldest one. On the single
-- deployment that exists today there is exactly one.
CREATE TEMP TABLE home_company AS
    SELECT id FROM organization.company ORDER BY created_at, id LIMIT 1;

-- ---------- identity.app_user ----------
-- Nullable on purpose: a super administrator is a platform user with no home
-- company (the bootstrap admin is created before any company exists). Every
-- other user is stamped by the service on creation.
ALTER TABLE identity.app_user
    ADD COLUMN company_id uuid REFERENCES organization.company (id);

UPDATE identity.app_user u
   SET company_id = b.company_id
  FROM organization.branch b
 WHERE b.id = u.primary_branch_id
   AND u.company_id IS NULL;

UPDATE identity.app_user u
   SET company_id = (SELECT b.company_id
                       FROM identity.user_branch ub
                       JOIN organization.branch b ON b.id = ub.branch_id
                      WHERE ub.user_id = u.id
                      ORDER BY b.created_at LIMIT 1)
 WHERE u.company_id IS NULL
   AND EXISTS (SELECT 1 FROM identity.user_branch ub WHERE ub.user_id = u.id);

-- Users with no branch at all who are not super administrators still belong
-- to the home company; super administrators stay platform-level.
UPDATE identity.app_user u
   SET company_id = (SELECT id FROM home_company)
 WHERE u.company_id IS NULL
   AND NOT EXISTS (SELECT 1
                     FROM identity.user_role ur
                     JOIN identity.role r ON r.id = ur.role_id
                    WHERE ur.user_id = u.id AND r.super_admin);

CREATE INDEX ix_app_user_company ON identity.app_user (company_id);

-- ---------- product.product ----------
ALTER TABLE product.product ADD COLUMN company_id uuid REFERENCES organization.company (id);
UPDATE product.product SET company_id = (SELECT id FROM home_company) WHERE company_id IS NULL;
ALTER TABLE product.product ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE product.product DROP CONSTRAINT uq_product_sku;
ALTER TABLE product.product ADD CONSTRAINT uq_product_company_sku UNIQUE (company_id, sku);
CREATE INDEX ix_product_company ON product.product (company_id);

-- ---------- product.product_category ----------
ALTER TABLE product.product_category ADD COLUMN company_id uuid REFERENCES organization.company (id);
UPDATE product.product_category SET company_id = (SELECT id FROM home_company) WHERE company_id IS NULL;
ALTER TABLE product.product_category ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE product.product_category DROP CONSTRAINT uq_product_category_code;
ALTER TABLE product.product_category
    ADD CONSTRAINT uq_product_category_company_code UNIQUE (company_id, code);
CREATE INDEX ix_product_category_company ON product.product_category (company_id);

-- ---------- product.jewellery_design ----------
ALTER TABLE product.jewellery_design ADD COLUMN company_id uuid REFERENCES organization.company (id);
UPDATE product.jewellery_design SET company_id = (SELECT id FROM home_company) WHERE company_id IS NULL;
ALTER TABLE product.jewellery_design ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE product.jewellery_design DROP CONSTRAINT uq_jewellery_design_code;
ALTER TABLE product.jewellery_design
    ADD CONSTRAINT uq_jewellery_design_company_code UNIQUE (company_id, design_code);
CREATE INDEX ix_jewellery_design_company ON product.jewellery_design (company_id);

-- ---------- product.metal (purity inherits through metal_id) ----------
ALTER TABLE product.metal ADD COLUMN company_id uuid REFERENCES organization.company (id);
UPDATE product.metal SET company_id = (SELECT id FROM home_company) WHERE company_id IS NULL;
ALTER TABLE product.metal ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE product.metal DROP CONSTRAINT uq_metal_code;
ALTER TABLE product.metal ADD CONSTRAINT uq_metal_company_code UNIQUE (company_id, code);
CREATE INDEX ix_metal_company ON product.metal (company_id);

-- ---------- product.gemstone ----------
ALTER TABLE product.gemstone ADD COLUMN company_id uuid REFERENCES organization.company (id);
UPDATE product.gemstone SET company_id = (SELECT id FROM home_company) WHERE company_id IS NULL;
ALTER TABLE product.gemstone ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE product.gemstone DROP CONSTRAINT uq_gemstone_code;
ALTER TABLE product.gemstone ADD CONSTRAINT uq_gemstone_company_code UNIQUE (company_id, code);
CREATE INDEX ix_gemstone_company ON product.gemstone (company_id);

-- ---------- customer.customer ----------
-- A customer belongs to the company that registered them; the registered
-- branch, where known, says which. The phone number was unique platform-wide,
-- which would have stopped the same person being a customer of two companies.
ALTER TABLE customer.customer ADD COLUMN company_id uuid REFERENCES organization.company (id);
UPDATE customer.customer c
   SET company_id = b.company_id
  FROM organization.branch b
 WHERE b.id = c.registered_branch_id
   AND c.company_id IS NULL;
UPDATE customer.customer SET company_id = (SELECT id FROM home_company) WHERE company_id IS NULL;
ALTER TABLE customer.customer ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE customer.customer DROP CONSTRAINT uq_customer_code;
ALTER TABLE customer.customer DROP CONSTRAINT uq_customer_phone;
ALTER TABLE customer.customer
    ADD CONSTRAINT uq_customer_company_code UNIQUE (company_id, customer_code);
ALTER TABLE customer.customer
    ADD CONSTRAINT uq_customer_company_phone UNIQUE (company_id, phone);
CREATE INDEX ix_customer_company ON customer.customer (company_id);

-- ---------- procurement.supplier ----------
ALTER TABLE procurement.supplier ADD COLUMN company_id uuid REFERENCES organization.company (id);
UPDATE procurement.supplier SET company_id = (SELECT id FROM home_company) WHERE company_id IS NULL;
ALTER TABLE procurement.supplier ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE procurement.supplier DROP CONSTRAINT uq_supplier_code;
ALTER TABLE procurement.supplier ADD CONSTRAINT uq_supplier_company_code UNIQUE (company_id, code);
CREATE INDEX ix_supplier_company ON procurement.supplier (company_id);

DROP TABLE home_company;
