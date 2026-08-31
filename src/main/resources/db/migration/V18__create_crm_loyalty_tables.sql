-- ---------- loyalty ----------

CREATE TABLE crm.loyalty_program (
    id                        uuid PRIMARY KEY,
    version                   bigint      NOT NULL DEFAULT 0,
    code                      varchar(40)  NOT NULL,
    name                      varchar(150) NOT NULL,
    company_id                uuid        REFERENCES organization.company (id),
    points_per_currency_unit  numeric(15,8) NOT NULL CHECK (points_per_currency_unit > 0),
    currency_value_per_point  numeric(15,4) NOT NULL CHECK (currency_value_per_point > 0),
    points_validity_months    integer,
    minimum_redeemable_points integer,
    earn_on_making_charge_only boolean    NOT NULL DEFAULT false,
    effective_from            date        NOT NULL,
    effective_to              date,
    active                    boolean     NOT NULL DEFAULT true,
    created_at                timestamptz NOT NULL,
    created_by                varchar(100),
    updated_at                timestamptz NOT NULL,
    updated_by                varchar(100),
    CONSTRAINT uq_loyalty_program_code UNIQUE (code),
    CONSTRAINT ck_loyalty_program_window CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE TABLE crm.loyalty_tier (
    id                  uuid PRIMARY KEY,
    version             bigint      NOT NULL DEFAULT 0,
    program_id          uuid        NOT NULL REFERENCES crm.loyalty_program (id) ON DELETE CASCADE,
    code                varchar(30)  NOT NULL,
    name                varchar(100) NOT NULL,
    minimum_points      bigint      NOT NULL CHECK (minimum_points >= 0),
    earn_multiplier     numeric(7,4) NOT NULL DEFAULT 1 CHECK (earn_multiplier >= 1),
    discount_percentage numeric(7,4),
    display_order       integer,
    benefits            varchar(1000),
    created_at          timestamptz NOT NULL,
    created_by          varchar(100),
    updated_at          timestamptz NOT NULL,
    updated_by          varchar(100),
    CONSTRAINT uq_tier_program_code UNIQUE (program_id, code),
    -- Two tiers at the same threshold would make the resulting tier ambiguous.
    CONSTRAINT uq_tier_program_threshold UNIQUE (program_id, minimum_points)
);

CREATE TABLE crm.loyalty_account (
    id               uuid PRIMARY KEY,
    version          bigint      NOT NULL DEFAULT 0,
    customer_id      uuid        NOT NULL REFERENCES customer.customer (id),
    program_id       uuid        NOT NULL REFERENCES crm.loyalty_program (id),
    tier_id          uuid        REFERENCES crm.loyalty_tier (id),
    points_balance   bigint      NOT NULL DEFAULT 0,
    lifetime_points  bigint      NOT NULL DEFAULT 0,
    points_redeemed  bigint      NOT NULL DEFAULT 0,
    points_expired   bigint      NOT NULL DEFAULT 0,
    enrolled_at      timestamptz NOT NULL,
    last_activity_at timestamptz,
    active           boolean     NOT NULL DEFAULT true,
    created_at       timestamptz NOT NULL,
    created_by       varchar(100),
    updated_at       timestamptz NOT NULL,
    updated_by       varchar(100),
    CONSTRAINT uq_loyalty_account_customer UNIQUE (customer_id),
    -- A loyalty balance must never go negative, whatever the service does.
    CONSTRAINT ck_loyalty_balance_not_negative CHECK (points_balance >= 0),
    CONSTRAINT ck_loyalty_lifetime_not_negative CHECK (lifetime_points >= 0)
);

CREATE INDEX ix_loyalty_account_tier ON crm.loyalty_account (tier_id);

CREATE TABLE crm.loyalty_transaction (
    id               uuid PRIMARY KEY,
    version          bigint      NOT NULL DEFAULT 0,
    account_id       uuid        NOT NULL REFERENCES crm.loyalty_account (id) ON DELETE CASCADE,
    customer_id      uuid        NOT NULL REFERENCES customer.customer (id),
    transaction_type varchar(20)  NOT NULL,
    points           bigint      NOT NULL,
    balance_after    bigint      NOT NULL,
    monetary_value   numeric(19,4),
    reference_type   varchar(50),
    reference_id     varchar(100),
    branch_id        uuid        REFERENCES organization.branch (id),
    expires_on       date,
    expired          boolean     NOT NULL DEFAULT false,
    reason           varchar(500),
    occurred_at      timestamptz NOT NULL,
    created_at       timestamptz NOT NULL,
    created_by       varchar(100),
    updated_at       timestamptz NOT NULL,
    updated_by       varchar(100)
);

-- The real guarantee that a sale cannot award points twice, even under a race.
CREATE UNIQUE INDEX uq_loyalty_earn_per_reference
    ON crm.loyalty_transaction (reference_type, reference_id)
    WHERE transaction_type = 'EARN' AND reference_id IS NOT NULL;
CREATE INDEX ix_loyalty_transaction_customer
    ON crm.loyalty_transaction (customer_id, occurred_at DESC);
CREATE INDEX ix_loyalty_transaction_expiry
    ON crm.loyalty_transaction (expires_on) WHERE expired = false AND expires_on IS NOT NULL;

-- ---------- crm ----------

CREATE TABLE crm.customer_activity (
    id             uuid PRIMARY KEY,
    version        bigint      NOT NULL DEFAULT 0,
    customer_id    uuid        NOT NULL REFERENCES customer.customer (id) ON DELETE CASCADE,
    activity_type  varchar(30)  NOT NULL,
    subject        varchar(200) NOT NULL,
    details        text,
    branch_id      uuid        REFERENCES organization.branch (id),
    handled_by     varchar(100),
    reference_type varchar(50),
    reference_id   varchar(100),
    occurred_at    timestamptz NOT NULL,
    created_at     timestamptz NOT NULL,
    created_by     varchar(100),
    updated_at     timestamptz NOT NULL,
    updated_by     varchar(100)
);

CREATE INDEX ix_activity_customer ON crm.customer_activity (customer_id, occurred_at DESC);
CREATE INDEX ix_activity_type ON crm.customer_activity (activity_type, occurred_at DESC);

CREATE TABLE crm.follow_up (
    id             uuid PRIMARY KEY,
    version        bigint      NOT NULL DEFAULT 0,
    customer_id    uuid        NOT NULL REFERENCES customer.customer (id) ON DELETE CASCADE,
    title          varchar(200) NOT NULL,
    details        varchar(1000),
    due_date       date        NOT NULL,
    assigned_to    varchar(100) NOT NULL,
    branch_id      uuid        REFERENCES organization.branch (id),
    status         varchar(20)  NOT NULL,
    priority       varchar(20),
    reference_type varchar(50),
    reference_id   varchar(100),
    completed_at   timestamptz,
    completed_by   varchar(100),
    outcome        varchar(1000),
    created_at     timestamptz NOT NULL,
    created_by     varchar(100),
    updated_at     timestamptz NOT NULL,
    updated_by     varchar(100)
);

-- Backs the "my open follow-ups" queue.
CREATE INDEX ix_follow_up_queue ON crm.follow_up (assigned_to, status, due_date);
CREATE INDEX ix_follow_up_customer ON crm.follow_up (customer_id, status);

CREATE TABLE crm.customer_segment (
    id                 uuid PRIMARY KEY,
    version            bigint      NOT NULL DEFAULT 0,
    code               varchar(40)  NOT NULL,
    name               varchar(150) NOT NULL,
    description        varchar(500),
    branch_id          uuid        REFERENCES organization.branch (id),
    tier_code          varchar(30),
    min_lifetime_spend numeric(19,4),
    min_purchase_count integer,
    inactive_days      integer,
    birthday_month     integer CHECK (birthday_month IS NULL OR birthday_month BETWEEN 1 AND 12),
    active             boolean     NOT NULL DEFAULT true,
    created_at         timestamptz NOT NULL,
    created_by         varchar(100),
    updated_at         timestamptz NOT NULL,
    updated_by         varchar(100),
    CONSTRAINT uq_segment_code UNIQUE (code)
);

CREATE TABLE crm.campaign (
    id            uuid PRIMARY KEY,
    version       bigint      NOT NULL DEFAULT 0,
    code          varchar(40)  NOT NULL,
    name          varchar(150) NOT NULL,
    description   varchar(1000),
    status        varchar(20)  NOT NULL,
    segment_id    uuid        REFERENCES crm.customer_segment (id),
    template_code varchar(60),
    branch_id     uuid        REFERENCES organization.branch (id),
    starts_on     date,
    ends_on       date,
    target_count  integer     NOT NULL DEFAULT 0,
    sent_count    integer     NOT NULL DEFAULT 0,
    launched_at   timestamptz,
    completed_at  timestamptz,
    created_at    timestamptz NOT NULL,
    created_by    varchar(100),
    updated_at    timestamptz NOT NULL,
    updated_by    varchar(100),
    CONSTRAINT uq_campaign_code UNIQUE (code),
    CONSTRAINT ck_campaign_window CHECK (ends_on IS NULL OR starts_on IS NULL OR ends_on >= starts_on)
);

CREATE INDEX ix_campaign_status ON crm.campaign (status, branch_id);
