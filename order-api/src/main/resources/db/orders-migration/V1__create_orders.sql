-- Historial independiente en orders.flyway_schema_history; nunca toca public.
CREATE TABLE orders.ordenes (
    order_id UUID PRIMARY KEY,
    cliente VARCHAR(255) NOT NULL,
    correo VARCHAR(255) NOT NULL,
    producto VARCHAR(255) NOT NULL,
    cantidad INTEGER NOT NULL CHECK (cantidad > 0),
    monto NUMERIC NOT NULL CHECK (monto > 0),
    estado VARCHAR(255) NOT NULL CHECK (estado IN ('PENDIENTE_PUBLICACION','PUBLICADA','PUBLICACION_INCIERTA')),
    creada_en TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    actualizada_en TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL
);
CREATE TABLE orders.orden_cambios (
    order_id UUID NOT NULL REFERENCES orders.ordenes(order_id),
    secuencia INTEGER NOT NULL,
    estado VARCHAR(255) NOT NULL CHECK (estado IN ('PENDIENTE_PUBLICACION','PUBLICADA','PUBLICACION_INCIERTA')),
    fecha TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    motivo VARCHAR(255) NOT NULL,
    PRIMARY KEY (order_id, secuencia)
);
