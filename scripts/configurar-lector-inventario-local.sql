\set ON_ERROR_STOP on
BEGIN;
DO $guard$
BEGIN
    IF current_database() <> 'ecommerce_inventory' THEN
        RAISE EXCEPTION 'El lector solo se concede en ecommerce_inventory';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'storefront_inventory_reader') THEN
        CREATE ROLE storefront_inventory_reader LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
    END IF;
END
$guard$;
GRANT CONNECT ON DATABASE ecommerce_inventory TO storefront_inventory_reader;
GRANT USAGE ON SCHEMA public TO storefront_inventory_reader;
GRANT SELECT ON public.inventory_items TO storefront_inventory_reader;
ALTER ROLE storefront_inventory_reader IN DATABASE ecommerce_inventory SET default_transaction_read_only = on;
COMMIT;
