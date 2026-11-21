CREATE TABLE inventory (
    product_id UUID PRIMARY KEY,
    available INTEGER NOT NULL DEFAULT 0 CHECK(available>=0),
    reserved INTEGER NOT NULL DEFAULT 0 CHECK(reserved>=0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE reservations (
    order_id UUID PRIMARY KEY,
    status VARCHAR(16) NOT NULL CHECK(status IN ('RESERVED','REJECTED','RELEASED','COMMITTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE reservation_items (
    order_id UUID NOT NULL REFERENCES reservations(order_id),
    product_id UUID NOT NULL REFERENCES inventory(product_id),
    quantity INTEGER NOT NULL CHECK(quantity>0),
    PRIMARY KEY(order_id,product_id)
);
CREATE INDEX reservation_items_product_idx ON reservation_items(product_id);
CREATE TABLE restocks (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES inventory(product_id),
    quantity INTEGER NOT NULL CHECK(quantity>0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
