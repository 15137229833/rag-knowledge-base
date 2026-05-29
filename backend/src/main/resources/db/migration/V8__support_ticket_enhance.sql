ALTER TABLE support_ticket
    ADD COLUMN IF NOT EXISTS priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    ADD COLUMN IF NOT EXISTS attachments_json TEXT;

UPDATE support_ticket SET attachments_json = '[]' WHERE attachments_json IS NULL;

CREATE TABLE IF NOT EXISTS support_ticket_event (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    ticket_id UUID NOT NULL REFERENCES support_ticket (id) ON DELETE CASCADE,
    actor_user_id UUID REFERENCES app_user (id),
    event_type VARCHAR(32) NOT NULL,
    message TEXT,
    meta_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_support_ticket_event_ticket ON support_ticket_event (ticket_id, created_at DESC);
