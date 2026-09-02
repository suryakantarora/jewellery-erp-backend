-- 1. Per-user read state.
--
-- A queued message is "sent" once the dispatcher has delivered it, but the
-- staff app also needs to know whether a person has actually seen it. Those are
-- different facts, so read state is its own column rather than a new status.
ALTER TABLE public.notification
    ADD COLUMN read_at timestamptz;

-- Backs the staff inbox: everything addressed to one user, or broadcast to a
-- branch, newest first.
CREATE INDEX ix_notification_inbox
    ON public.notification (recipient_type, recipient_id, branch_id, created_at DESC);

-- 2. (No template seeding needed.)
--
-- V15 already registers IN_APP templates for ITEM_TRANSFERRED and LOW_STOCK.
-- The reason no staff notification had ever appeared was not a missing
-- template: both events queue a row with a null recipient_id, and the app was
-- filtering by recipient_id = the signed-in user, which such a row can never
-- match. The new /notifications/mine endpoint treats a null recipient as a
-- branch-wide broadcast, which is what these operational events actually are.

-- 3. Location codes are unique per branch, not globally.
--
-- The global constraint meant a second branch could not have its own "VAULT",
-- forcing every deployment to invent branch prefixes inside the code column.
-- Scoping the constraint to the branch is what the column always meant.
ALTER TABLE organization.location
    DROP CONSTRAINT uq_location_code;

ALTER TABLE organization.location
    ADD CONSTRAINT uq_location_branch_code UNIQUE (branch_id, code);
