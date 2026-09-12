CREATE TABLE orders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    state VARCHAR(24) NOT NULL CHECK(state IN ('CREATED','INVENTORY_RESERVED','PAYMENT_PENDING','CONFIRMED','CANCELLED')),
    total NUMERIC(18,2) NOT NULL CHECK(total>0),
    saga_data TEXT NOT NULL,
    cancellation_reason VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id,idempotency_key)
);
CREATE INDEX orders_user_created_idx ON orders(user_id,created_at DESC);
CREATE INDEX orders_pending_idx ON orders(updated_at) WHERE state NOT IN ('CONFIRMED','CANCELLED');
CREATE TABLE order_items (
    order_id UUID NOT NULL REFERENCES orders(id),
    product_id UUID NOT NULL,
    quantity INTEGER NOT NULL CHECK(quantity>0),
    unit_price NUMERIC(12,2) NOT NULL CHECK(unit_price>0),
    PRIMARY KEY(order_id,product_id)
);
CREATE TABLE order_transitions (
    id BIGSERIAL PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    state VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX transitions_order_idx ON order_transitions(order_id,id);
