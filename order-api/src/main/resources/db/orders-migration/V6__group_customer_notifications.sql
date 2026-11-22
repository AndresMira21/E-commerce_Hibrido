ALTER TABLE orders.customer_notifications ADD COLUMN deleted_at TIMESTAMPTZ;

WITH existing AS (
    SELECT DISTINCT ON (purchase_id) id,
           CASE WHEN bool_or(read_at IS NULL) OVER (PARTITION BY purchase_id) THEN NULL ELSE read_at END AS read_at
    FROM orders.customer_notifications
    WHERE event_key LIKE 'notification:%'
    ORDER BY purchase_id, created_at, id
)
UPDATE orders.customer_notifications n
SET event_key = 'notification', read_at = existing.read_at,
    title = 'Aviso de compra registrado',
    description = 'Puedes consultar el estado de tu compra en Mis pedidos.'
FROM existing WHERE existing.id = n.id;
