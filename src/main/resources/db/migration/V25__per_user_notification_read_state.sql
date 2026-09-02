-- Read state belongs to a (notification, user) pair, not to the notification.
--
-- V24 put `read_at` on the notification row, which is correct for a message
-- addressed to one person and wrong for a branch broadcast: those are a single
-- row seen by everyone in the branch, so one person opening it cleared the
-- badge for all their colleagues. Verified in dev before this migration:
-- khamla's "mark all read" took somchai's unread count from 1 to 0.
CREATE TABLE public.notification_read (
    notification_id uuid        NOT NULL REFERENCES public.notification (id) ON DELETE CASCADE,
    user_id         uuid        NOT NULL,
    read_at         timestamptz NOT NULL,
    PRIMARY KEY (notification_id, user_id)
);

-- The unread query filters by user first, so that is the leading column.
CREATE INDEX ix_notification_read_user ON public.notification_read (user_id, notification_id);

-- Carry over whatever V24 recorded. The owning user is unknown for a broadcast,
-- so only directly-addressed rows can be migrated; a broadcast simply returns
-- to unread, which is the safe direction to be wrong in.
INSERT INTO public.notification_read (notification_id, user_id, read_at)
SELECT id, recipient_id, read_at
FROM public.notification
WHERE read_at IS NOT NULL AND recipient_id IS NOT NULL;

ALTER TABLE public.notification DROP COLUMN read_at;
