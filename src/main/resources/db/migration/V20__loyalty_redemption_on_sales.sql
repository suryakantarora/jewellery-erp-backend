-- Loyalty redemption reduces what the customer pays, alongside exchange credit.
ALTER TABLE sales.sale
    ADD COLUMN loyalty_points_redeemed  bigint        NOT NULL DEFAULT 0,
    ADD COLUMN loyalty_redemption_value numeric(19,4) NOT NULL DEFAULT 0;

ALTER TABLE sales.sale
    ADD CONSTRAINT ck_sale_redemption_not_negative
        CHECK (loyalty_points_redeemed >= 0 AND loyalty_redemption_value >= 0),
    -- Credits can cover the price but never exceed it.
    ADD CONSTRAINT ck_sale_credits_within_total
        CHECK (exchange_credit + loyalty_redemption_value <= total_amount);

-- The tier entitlement is frozen onto the line, like every other price component,
-- so an invoice reproduces exactly even after the customer changes tier.
ALTER TABLE sales.sale_line
    ADD COLUMN tier_discount_amount numeric(19,4) NOT NULL DEFAULT 0,
    ADD COLUMN loyalty_tier_code    varchar(30);
