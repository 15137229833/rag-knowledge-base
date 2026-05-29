CREATE TABLE IF NOT EXISTS support_ticket (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    created_by UUID NOT NULL REFERENCES app_user (id),
    topic VARCHAR(128),
    content TEXT NOT NULL,
    contact VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    admin_note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_support_ticket_created_by ON support_ticket (created_by);
CREATE INDEX IF NOT EXISTS idx_support_ticket_status ON support_ticket (status);
CREATE INDEX IF NOT EXISTS idx_support_ticket_updated_at ON support_ticket (updated_at DESC);
