-- Notification lives in the default schema: it is cross-cutting rather than
-- owned by one business domain.
CREATE TABLE public.notification_template (
    id         uuid PRIMARY KEY,
    version    bigint      NOT NULL DEFAULT 0,
    code       varchar(60)  NOT NULL,
    event_type varchar(60)  NOT NULL,
    channel    varchar(20)  NOT NULL,
    locale     varchar(10)  NOT NULL DEFAULT 'en',
    subject    varchar(255),
    body       text        NOT NULL,
    active     boolean     NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL,
    created_by varchar(100),
    updated_at timestamptz NOT NULL,
    updated_by varchar(100),
    CONSTRAINT uq_template_code UNIQUE (code),
    CONSTRAINT uq_template_event_channel_locale UNIQUE (event_type, channel, locale)
);

CREATE INDEX ix_template_event ON public.notification_template (event_type, active);

CREATE TABLE public.notification (
    id                uuid PRIMARY KEY,
    version           bigint      NOT NULL DEFAULT 0,
    event_type        varchar(60)  NOT NULL,
    channel           varchar(20)  NOT NULL,
    recipient_type    varchar(20)  NOT NULL,
    recipient_id      uuid,
    recipient_address varchar(255),
    subject           varchar(255),
    body              text        NOT NULL,
    status            varchar(20)  NOT NULL,
    branch_id         uuid,
    reference_type    varchar(50),
    reference_id      varchar(100),
    attempt_count     integer     NOT NULL DEFAULT 0,
    last_attempt_at   timestamptz,
    sent_at           timestamptz,
    failure_reason    varchar(500),
    created_at        timestamptz NOT NULL,
    created_by        varchar(100),
    updated_at        timestamptz NOT NULL,
    updated_by        varchar(100)
);

-- Backs the dispatch queue scan.
CREATE INDEX ix_notification_queue ON public.notification (status, attempt_count, created_at);
CREATE INDEX ix_notification_recipient ON public.notification (recipient_id, created_at DESC);
CREATE INDEX ix_notification_reference ON public.notification (reference_type, reference_id);

-- Seed the events from section 21 with sensible default wording.
INSERT INTO public.notification_template
    (id, version, code, event_type, channel, locale, subject, body, active,
     created_at, created_by, updated_at, updated_by)
VALUES
    (gen_random_uuid(), 0, 'SALE_COMPLETED_SMS', 'SALE_COMPLETED', 'SMS', 'en', NULL,
     'Thank you {{customerName}}. Your purchase is complete. Invoice {{invoiceNumber}}, total {{totalAmount}}.',
     true, now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'PAYMENT_RECEIVED_SMS', 'PAYMENT_RECEIVED', 'SMS', 'en', NULL,
     'We have received your payment of {{amount}} by {{method}}. Thank you.',
     true, now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'REPAIR_READY_SMS', 'REPAIR_READY', 'SMS', 'en', NULL,
     'Your repair {{requestNumber}} is ready for collection.',
     true, now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'EXCHANGE_COMPLETED_SMS', 'EXCHANGE_COMPLETED', 'SMS', 'en', NULL,
     'Your exchange {{referenceNumber}} is complete. Value allowed: {{valuation}}.',
     true, now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'ITEM_TRANSFERRED_INAPP', 'ITEM_TRANSFERRED', 'IN_APP', 'en',
     'Transfer received',
     'Transfer {{referenceNumber}} of {{itemCount}} item(s) has been received.',
     true, now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'LOW_STOCK_INAPP', 'LOW_STOCK', 'IN_APP', 'en',
     'Low stock',
     'Location {{locationId}} has {{availableCount}} item(s), below the threshold of {{threshold}}.',
     true, now(), 'system', now(), 'system');
