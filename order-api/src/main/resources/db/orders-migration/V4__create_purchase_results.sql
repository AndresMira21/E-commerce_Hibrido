CREATE TABLE orders.purchase_results (
    order_id UUID NOT NULL REFERENCES orders.purchase_lines(order_id),
    worker VARCHAR(12) NOT NULL CHECK (worker IN ('payment','stock','notification')),
    status VARCHAR(32) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (order_id, worker)
);
