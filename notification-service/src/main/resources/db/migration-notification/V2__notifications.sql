CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    user_id UUID NOT NULL,
    kind VARCHAR(32) NOT NULL,
    message VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(order_id,kind)
);
CREATE INDEX notifications_user_idx ON notifications(user_id,created_at DESC);
