-- Storefront: everything the customer mobile app needs that the ERP did not
-- already own.
--
-- The app is one binary serving many companies; `storefront.tenant` maps the
-- key baked into a build (or typed as a shop code) to the company, and carries
-- the branding the app themes itself with. Editorial content (banners,
-- policies, stores, offers, brands, stories, about, category artwork) is kept
-- as documents: the app renders them as written, the ERP never queries inside
-- them, and a new content block must not need a migration.
--
-- Customer identity reuses customer.customer — a person who signs in with
-- their phone is the same customer the shop already knows — so wishlist and
-- addresses stay in the customer schema and are visible to staff.

CREATE SCHEMA IF NOT EXISTS storefront;

CREATE TABLE storefront.tenant (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id  uuid         NOT NULL UNIQUE REFERENCES organization.company (id),
    tenant_key  varchar(60)  NOT NULL UNIQUE,
    -- The TenantConfig document the app reads (brand, palettes, currency,
    -- locales, contact, feature flags, delivery and tax settings).
    config      jsonb        NOT NULL DEFAULT '{}'::jsonb,
    active      boolean      NOT NULL DEFAULT true,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE storefront.content (
    company_id  uuid         NOT NULL REFERENCES organization.company (id),
    kind        varchar(40)  NOT NULL,
    payload     jsonb        NOT NULL,
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    updated_by  varchar(100),
    PRIMARY KEY (company_id, kind)
);

-- Retail presentation of a product: sizes, audience, badges, stone, tags,
-- brand, original price. Rating and review count are computed from reviews.
CREATE TABLE storefront.product_retail (
    product_id  uuid PRIMARY KEY REFERENCES product.product (id) ON DELETE CASCADE,
    attributes  jsonb        NOT NULL DEFAULT '{}'::jsonb,
    updated_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE storefront.otp_challenge (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id   uuid         NOT NULL REFERENCES organization.company (id),
    phone        varchar(30)  NOT NULL,
    code_hash    varchar(100) NOT NULL,
    attempts     integer      NOT NULL DEFAULT 0,
    expires_at   timestamptz  NOT NULL,
    consumed_at  timestamptz,
    created_at   timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX ix_otp_challenge_phone ON storefront.otp_challenge (company_id, phone, created_at DESC);

-- App-side facts about a customer that the customer master has no place for.
CREATE TABLE storefront.customer_profile (
    customer_id       uuid PRIMARY KEY REFERENCES customer.customer (id) ON DELETE CASCADE,
    -- False until the customer has typed their name; the master needs a
    -- non-empty full_name, so it holds the phone number until then.
    profile_complete  boolean      NOT NULL DEFAULT false,
    avatar            varchar(500),
    -- Bumping this invalidates every token issued before (sign out everywhere).
    token_version     integer      NOT NULL DEFAULT 0,
    last_login_at     timestamptz,
    created_at        timestamptz  NOT NULL DEFAULT now()
);

-- The delivery contact may differ from the customer (a gift, an office).
ALTER TABLE customer.customer_address ADD COLUMN contact_name varchar(200);
ALTER TABLE customer.customer_address ADD COLUMN contact_phone varchar(30);

CREATE TABLE storefront.cart_item (
    customer_id  uuid         NOT NULL REFERENCES customer.customer (id) ON DELETE CASCADE,
    item_id      uuid         NOT NULL REFERENCES inventory.jewellery_item (id) ON DELETE CASCADE,
    size         varchar(30)  NOT NULL DEFAULT '',
    quantity     integer      NOT NULL CHECK (quantity > 0),
    position     integer      NOT NULL DEFAULT 0,
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (customer_id, item_id, size)
);

-- A label and a masked detail only; card numbers never reach this platform.
CREATE TABLE storefront.payment_method (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id  uuid         NOT NULL REFERENCES customer.customer (id) ON DELETE CASCADE,
    kind         varchar(20)  NOT NULL CHECK (kind IN ('card', 'wallet', 'cod')),
    label        varchar(100) NOT NULL,
    detail       varchar(100) NOT NULL DEFAULT '',
    is_default   boolean      NOT NULL DEFAULT false,
    created_at   timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX ix_payment_method_customer ON storefront.payment_method (customer_id);

CREATE TABLE storefront.online_order (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id       uuid          NOT NULL REFERENCES organization.company (id),
    customer_id      uuid          NOT NULL REFERENCES customer.customer (id),
    order_number     varchar(40)   NOT NULL,
    status           varchar(20)   NOT NULL DEFAULT 'placed'
        CHECK (status IN ('placed', 'confirmed', 'packed', 'shipped', 'delivered', 'cancelled')),
    subtotal         numeric(19,2) NOT NULL,
    shipping         numeric(19,2) NOT NULL DEFAULT 0,
    tax              numeric(19,2) NOT NULL DEFAULT 0,
    discount         numeric(19,2) NOT NULL DEFAULT 0,
    total            numeric(19,2) NOT NULL,
    currency         varchar(3)    NOT NULL,
    offer_code       varchar(40),
    -- The address as it was when ordered; later edits must not rewrite history.
    address_text     varchar(600)  NOT NULL,
    contact_name     varchar(200),
    contact_phone    varchar(30),
    payment_kind     varchar(20)   NOT NULL,
    payment_label    varchar(100),
    courier          varchar(100),
    tracking_number  varchar(100),
    eta              jsonb,
    placed_at        timestamptz   NOT NULL DEFAULT now(),
    updated_at       timestamptz   NOT NULL DEFAULT now(),
    updated_by       varchar(100),
    CONSTRAINT uq_online_order_number UNIQUE (company_id, order_number)
);
CREATE INDEX ix_online_order_customer ON storefront.online_order (customer_id, placed_at DESC);
CREATE INDEX ix_online_order_company_status ON storefront.online_order (company_id, status, placed_at DESC);

CREATE TABLE storefront.online_order_item (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    uuid          NOT NULL REFERENCES storefront.online_order (id) ON DELETE CASCADE,
    item_id     uuid          REFERENCES inventory.jewellery_item (id),
    item_code   varchar(60),
    name        varchar(200)  NOT NULL,
    image       varchar(500),
    price       numeric(19,2) NOT NULL,
    quantity    integer       NOT NULL CHECK (quantity > 0),
    size        varchar(30),
    -- Set when the order is cancelled, so the piece can be ordered again.
    released    boolean       NOT NULL DEFAULT false
);
CREATE INDEX ix_online_order_item_order ON storefront.online_order_item (order_id);

-- A piece in an open online order must not be promised twice.
CREATE UNIQUE INDEX uq_online_order_item_open ON storefront.online_order_item (item_id)
    WHERE item_id IS NOT NULL AND NOT released;

CREATE TABLE storefront.review (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id   uuid          NOT NULL REFERENCES organization.company (id),
    product_id   uuid          NOT NULL REFERENCES product.product (id) ON DELETE CASCADE,
    customer_id  uuid          REFERENCES customer.customer (id) ON DELETE SET NULL,
    author       varchar(150)  NOT NULL,
    avatar       varchar(500),
    rating       numeric(2,1)  NOT NULL CHECK (rating >= 1 AND rating <= 5),
    body         varchar(2000) NOT NULL,
    verified     boolean       NOT NULL DEFAULT false,
    featured     boolean       NOT NULL DEFAULT false,
    published    boolean       NOT NULL DEFAULT true,
    created_at   timestamptz   NOT NULL DEFAULT now()
);
CREATE INDEX ix_review_product ON storefront.review (product_id, created_at DESC);
CREATE INDEX ix_review_featured ON storefront.review (company_id, created_at DESC) WHERE featured;

CREATE TABLE storefront.feedback (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id   uuid          NOT NULL REFERENCES organization.company (id),
    customer_id  uuid          REFERENCES customer.customer (id) ON DELETE SET NULL,
    ticket       varchar(20)   NOT NULL,
    happy        boolean       NOT NULL,
    topic        varchar(60)   NOT NULL,
    message      varchar(2000) NOT NULL,
    email        varchar(150),
    follow_up    boolean       NOT NULL DEFAULT false,
    created_at   timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT uq_feedback_ticket UNIQUE (company_id, ticket)
);

CREATE TABLE storefront.app_rating (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id   uuid          NOT NULL REFERENCES organization.company (id),
    customer_id  uuid          REFERENCES customer.customer (id) ON DELETE SET NULL,
    stars        integer       NOT NULL CHECK (stars BETWEEN 1 AND 5),
    comment      varchar(1000),
    created_at   timestamptz   NOT NULL DEFAULT now()
);

-- The customer's in-app inbox. customer_id NULL is a broadcast to everyone
-- of the company; read state for those is per customer, below.
CREATE TABLE storefront.customer_notification (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id   uuid          NOT NULL REFERENCES organization.company (id),
    customer_id  uuid          REFERENCES customer.customer (id) ON DELETE CASCADE,
    kind         varchar(20)   NOT NULL DEFAULT 'info',
    title        varchar(200)  NOT NULL,
    body         varchar(1000) NOT NULL,
    link         varchar(300),
    created_at   timestamptz   NOT NULL DEFAULT now()
);
CREATE INDEX ix_customer_notification ON storefront.customer_notification (company_id, customer_id, created_at DESC);

CREATE TABLE storefront.customer_notification_read (
    notification_id  uuid NOT NULL REFERENCES storefront.customer_notification (id) ON DELETE CASCADE,
    customer_id      uuid NOT NULL REFERENCES customer.customer (id) ON DELETE CASCADE,
    read_at          timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (notification_id, customer_id)
);

-- Same rule as the staff table: the token is the identity, and a handset that
-- changes hands is re-owned rather than refused.
CREATE TABLE storefront.customer_device (
    token         varchar(512) PRIMARY KEY,
    company_id    uuid         NOT NULL REFERENCES organization.company (id),
    customer_id   uuid         NOT NULL REFERENCES customer.customer (id) ON DELETE CASCADE,
    platform      varchar(20)  NOT NULL,
    last_seen_at  timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX ix_customer_device_customer ON storefront.customer_device (customer_id);

INSERT INTO identity.permission (id, version, code, module, description, created_at, created_by, updated_at, updated_by)
VALUES
    (gen_random_uuid(), 0, 'STOREFRONT_VIEW', 'STOREFRONT', 'View the customer app configuration and online orders', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'STOREFRONT_MANAGE', 'STOREFRONT', 'Manage customer app branding, content and online orders', now(), 'system', now(), 'system');

INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM identity.role r
JOIN identity.permission p ON p.code IN ('STOREFRONT_VIEW', 'STOREFRONT_MANAGE')
WHERE r.code IN ('SUPER_ADMIN', 'BRANCH_MANAGER')
  AND NOT EXISTS (SELECT 1 FROM identity.role_permission rp
                  WHERE rp.role_id = r.id AND rp.permission_id = p.id);
