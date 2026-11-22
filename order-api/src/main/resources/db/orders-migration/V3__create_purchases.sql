CREATE TABLE orders.purchases (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES orders.customer_accounts(id),
    idempotency_key UUID NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    delivery JSONB NOT NULL,
    payment_method VARCHAR(20) NOT NULL CHECK (payment_method IN ('tarjeta','contraentrega')),
    subtotal NUMERIC NOT NULL,
    discount NUMERIC NOT NULL,
    total NUMERIC NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (customer_id, idempotency_key)
);
CREATE INDEX purchases_owner_date ON orders.purchases(customer_id, created_at DESC, id);

CREATE TABLE orders.purchase_lines (
    order_id UUID PRIMARY KEY REFERENCES orders.ordenes(order_id),
    purchase_id UUID NOT NULL REFERENCES orders.purchases(id),
    sku VARCHAR(40) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    unit_price NUMERIC NOT NULL,
    discount NUMERIC NOT NULL,
    UNIQUE(purchase_id, sku)
);
CREATE INDEX purchase_lines_purchase ON orders.purchase_lines(purchase_id);

CREATE TABLE orders.purchase_outbox (
    order_id UUID PRIMARY KEY REFERENCES orders.purchase_lines(order_id),
    status VARCHAR(12) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','SENDING','SENT','UNCERTAIN')),
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX purchase_outbox_pending ON orders.purchase_outbox(status, changed_at);
