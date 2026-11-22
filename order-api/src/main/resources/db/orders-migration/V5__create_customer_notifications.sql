CREATE TABLE orders.customer_notifications (
    id UUID PRIMARY KEY,
    purchase_id UUID NOT NULL REFERENCES orders.purchases(id),
    event_key VARCHAR(90) NOT NULL,
    title VARCHAR(120) NOT NULL,
    description VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMPTZ,
    UNIQUE (purchase_id, event_key)
);
CREATE INDEX customer_notifications_purchase_date ON orders.customer_notifications(purchase_id, created_at DESC, id);
