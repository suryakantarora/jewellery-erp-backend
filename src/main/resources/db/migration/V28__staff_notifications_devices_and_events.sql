-- 1. Push devices.
--
-- Every staff notification so far was either a branch broadcast or read by
-- polling the inbox; nothing could reach a locked phone. A device row binds an
-- FCM registration token to a user. The token, not the user, is unique: a
-- handset that changes hands re-registers under its new owner and must stop
-- receiving the old owner's mail, so registration re-owns rather than refuses.
CREATE TABLE public.notification_device (
    id           uuid PRIMARY KEY,
    version      bigint       NOT NULL DEFAULT 0,
    user_id      uuid         NOT NULL,
    token        varchar(512) NOT NULL,
    platform     varchar(20)  NOT NULL,
    app_version  varchar(50),
    last_seen_at timestamptz,
    created_at   timestamptz  NOT NULL,
    created_by   varchar(100),
    updated_at   timestamptz  NOT NULL,
    updated_by   varchar(100),
    CONSTRAINT uq_notification_device_token UNIQUE (token)
);

-- The sender asks "which phones does this user have?" per PUSH row.
CREATE INDEX ix_notification_device_user ON public.notification_device (user_id);

-- 2. Templates for staff-directed events.
--
-- These events name a person (the approver, the creator, the technician), so
-- each recipient gets their own row: one IN_APP for the inbox and one PUSH for
-- the phone. Placeholders match the event payload keys exactly.
INSERT INTO public.notification_template
    (id, version, code, event_type, channel, locale, subject, body, active,
     created_at, created_by, updated_at, updated_by)
VALUES
    (gen_random_uuid(), 0, 'TRANSFER_AWAITING_APPROVAL_INAPP', 'TRANSFER_AWAITING_APPROVAL', 'IN_APP', 'en',
     'Transfer awaiting your approval',
     'Transfer {{referenceNumber}} ({{itemCount}} item(s), {{fromLocation}} to {{toLocation}}) is waiting for your approval.',
     true, now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'TRANSFER_AWAITING_APPROVAL_PUSH', 'TRANSFER_AWAITING_APPROVAL', 'PUSH', 'en',
     'Transfer awaiting your approval',
     '{{referenceNumber}}: {{itemCount}} item(s) from {{fromLocation}} to {{toLocation}}.',
     true, now(), 'system', now(), 'system'),

    (gen_random_uuid(), 0, 'TRANSFER_APPROVED_INAPP', 'TRANSFER_APPROVED', 'IN_APP', 'en',
     'Transfer approved',
     'Your transfer {{referenceNumber}} has been approved and can be dispatched.',
     true, now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'TRANSFER_APPROVED_PUSH', 'TRANSFER_APPROVED', 'PUSH', 'en',
     'Transfer approved',
     'Transfer {{referenceNumber}} was approved.',
     true, now(), 'system', now(), 'system'),

    (gen_random_uuid(), 0, 'TRANSFER_REJECTED_INAPP', 'TRANSFER_REJECTED', 'IN_APP', 'en',
     'Transfer rejected',
     'Your transfer {{referenceNumber}} was rejected: {{rejectionReason}}',
     true, now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'TRANSFER_REJECTED_PUSH', 'TRANSFER_REJECTED', 'PUSH', 'en',
     'Transfer rejected',
     'Transfer {{referenceNumber}} was rejected: {{rejectionReason}}',
     true, now(), 'system', now(), 'system'),

    (gen_random_uuid(), 0, 'PURCHASE_ORDER_APPROVED_INAPP', 'PURCHASE_ORDER_APPROVED', 'IN_APP', 'en',
     'Purchase order approved',
     'Your purchase order {{orderNumber}} has been approved.',
     true, now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'PURCHASE_ORDER_APPROVED_PUSH', 'PURCHASE_ORDER_APPROVED', 'PUSH', 'en',
     'Purchase order approved',
     'Purchase order {{orderNumber}} was approved.',
     true, now(), 'system', now(), 'system'),

    (gen_random_uuid(), 0, 'PURCHASE_ORDER_REJECTED_INAPP', 'PURCHASE_ORDER_REJECTED', 'IN_APP', 'en',
     'Purchase order rejected',
     'Your purchase order {{orderNumber}} was rejected: {{rejectionReason}}',
     true, now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'PURCHASE_ORDER_REJECTED_PUSH', 'PURCHASE_ORDER_REJECTED', 'PUSH', 'en',
     'Purchase order rejected',
     'Purchase order {{orderNumber}} was rejected: {{rejectionReason}}',
     true, now(), 'system', now(), 'system'),

    (gen_random_uuid(), 0, 'REPAIR_READY_STAFF_INAPP', 'REPAIR_READY_STAFF', 'IN_APP', 'en',
     'Repair ready for collection',
     'Repair {{requestNumber}} passed quality check and is ready for the customer to collect.',
     true, now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'REPAIR_READY_STAFF_PUSH', 'REPAIR_READY_STAFF', 'PUSH', 'en',
     'Repair ready for collection',
     'Repair {{requestNumber}} is ready.',
     true, now(), 'system', now(), 'system'),

    (gen_random_uuid(), 0, 'HIGH_VALUE_SALE_INAPP', 'HIGH_VALUE_SALE', 'IN_APP', 'en',
     'High-value sale',
     'Invoice {{invoiceNumber}} closed at {{totalAmount}} {{currency}}, at or above the reporting threshold.',
     true, now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'HIGH_VALUE_SALE_PUSH', 'HIGH_VALUE_SALE', 'PUSH', 'en',
     'High-value sale',
     'Invoice {{invoiceNumber}}: {{totalAmount}} {{currency}}.',
     true, now(), 'system', now(), 'system');
