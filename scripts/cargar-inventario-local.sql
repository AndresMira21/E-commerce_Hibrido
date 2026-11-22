\set ON_ERROR_STOP on
BEGIN;
DO $guard$
BEGIN
    IF current_database() <> 'ecommerce_inventory' THEN
        RAISE EXCEPTION 'La carga solo se permite en ecommerce_inventory';
    END IF;
END
$guard$;

INSERT INTO public.inventory_items (product_name, available_quantity, version) VALUES
    ('nexo', 16, 0), ('trazo', 12, 0), ('vector', 14, 0), ('puente', 10, 0),
    ('onda', 8, 0), ('foco', 5, 0), ('bloque', 8, 0), ('carga', 14, 0),
    ('plano', 10, 0), ('bit', 8, 0), ('voz', 4, 0), ('aire', 9, 0),
    ('pixel', 10, 0), ('superficie', 15, 0), ('marco24', 3, 0), ('marco27', 0, 0),
    ('halo', 0, 0), ('nivel', 2, 0), ('clave', 1, 0), ('flujo', 0, 0)
ON CONFLICT (product_name) DO NOTHING;

COMMIT;
