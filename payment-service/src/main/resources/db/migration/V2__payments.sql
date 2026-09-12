CREATE TABLE payments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    amount NUMERIC(18,2) NOT NULL CHECK(amount>0),
    status VARCHAR(16) NOT NULL CHECK(status IN ('COMPLETED','FAILED')),
    reason VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX payments_user_idx ON payments(user_id,created_at DESC);
