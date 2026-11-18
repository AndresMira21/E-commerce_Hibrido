# E-commerce Hibrido: integracion local

Siete microservicios comparten la red `ecommerce` con Kafka, RabbitMQ y PostgreSQL.
Los tres archivos Compose se combinan en este orden: infraestructura, servicios
existentes e integracion. No ejecutar `down -v`, borrar volumenes ni purgar colas.

## Flujo y contratos

`POST /api/orders` (8084) valida `cliente`, `correo`, `item`, `cantidad` y `monto`.
order-api genera un UUID String y publica JSON en `new_orders`:

```json
{"order_id":"a1b2c3d4-1234-5678-9012-abcdefabcdef","cliente":"Alejandra","correo":"alejandra@example.com","item":"Libro","cantidad":1,"monto":15000}
```

Solo despues de confirmar Kafka responde 202 con `order_id` y `status: ACCEPTED`.
Datos invalidos devuelven 400; errores de publicacion devuelven 503 sin detalles internos.
El envio se ejecuta fuera del hilo HTTP. Un timeout no demuestra que Kafka nunca
recibiera el mensaje: no reenviar automaticamente un POST con resultado incierto.

order-processor escucha `new_orders` y distribuye `PROCESS_ORDER` mediante el
exchange Fanout `order_tasks` a billing_queue, inventory_queue y notification_queue.
Los workers publican billing_events, inventory_events y notification_events.
Dashboard consume esos estados; Analytics consume `new_orders` y mantiene agregados
en memoria de pedidos recibidos: `total_orders`, `total_amount` y `average_amount`
representan órdenes aceptadas desde el topic, no ventas confirmadas ni necesariamente
pagadas. El servicio evita volver a contar el mismo `order_id` durante una ejecución;
sus métricas se reinician al reiniciar Analytics.
202 confirma la entrada a Kafka, no el procesamiento completo de los workers.

## Configuracion local

Usar Docker Desktop con contenedores Linux, Git Bash y Python 3 (para extraer JSON
en los comandos siguientes). Mantener el mismo directorio/proyecto Compose de las
pruebas anteriores para reutilizar volumenes; no pasar un nombre de proyecto nuevo.
Los limites de memoria del conjunto suman unos 5.2 GiB, sin contar Docker Desktop.

Configurar `.env` localmente a partir de `.env.example` solo si aun no existe.
No sobrescribir el archivo existente. POSTGRES_PASSWORD es obligatoria, no se
versiona. Las credenciales deben coincidir con las ya inicializadas en los volumenes;
cambiar variables no cambia la contraseña de una base ya creada.

Dentro de Docker: Kafka `kafka:9092`, RabbitMQ `rabbitmq:5672`, PostgreSQL
`postgres:5432/ecommerce_inventory`. Desde Windows: Kafka localhost:9094,
RabbitMQ localhost:5672, administracion localhost:15672 y PostgreSQL localhost:5433.
Dashboard: http://localhost:8083/; Analytics: http://localhost:8082/analytics.

## Validar y arrancar desde Git Bash

Estos comandos son instrucciones pendientes de ejecucion si Docker no esta
disponible en la terminal del asistente. No representan resultados de integracion.
Ejecutar desde la raiz E-commerce_Hibrido:

```bash
dc() { docker compose -f compose.infra.yml -f compose.services.yml -f compose.integration.yml "$@"; }
docker info
dc config --quiet
dc config --services
# Debe listar 10 servicios: siete microservicios y tres de infraestructura.
docker build -t ecommerce-order-api:local ./order-api
dc up -d kafka rabbitmq postgres
dc ps
# Verificar postgres healthy y que Kafka/RabbitMQ estan listos antes de continuar.
dc logs --tail=60 kafka rabbitmq postgres
dc up -d order-processor billing-worker inventory-worker notification-worker analytics-service dashboard-aggregator
dc logs --tail=80 order-processor billing-worker inventory-worker notification-worker analytics-service dashboard-aggregator
dc up -d order-api
dc logs --tail=60 order-api
```

Los seis microservicios conservan sus imagenes/configuracion anteriores. Reconstruir
solo los que hayan cambiado con `dc build nombre-servicio`. `service_started` no
garantiza disponibilidad de Kafka, RabbitMQ ni existencia de las colas. Inventario
si espera el healthcheck de PostgreSQL. Confirmar que la politica inventory-dlq
sigue aplicada; instrucciones en inventory-worker/docs/rabbitmq-dlq.md.
Flyway prepara el esquema; no carga productos. La prueba requiere Libro con stock.
Antes del POST, comprobar sin modificar datos:

```bash
dc exec -T rabbitmq rabbitmqctl list_policies -p /
dc exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT product_name,available_quantity,version FROM inventory_items WHERE product_name = '\''Libro'\'';"'
```

Si Libro no existe o no tiene unidades, detener la prueba y preparar los datos por
separado. No reinicializar ni reemplazar el inventario existente. Los mensajes
pendientes pueden consumirse al iniciar workers; revisar las colas previamente.

## Prueba HTTP e integracion por order_id

Sin otras ordenes simultaneas, capturar Analytics antes de enviar exactamente un POST.
No repetir el POST de prueba sin necesidad: cada llamada genera una orden nueva.

```bash
ANALYTICS_BEFORE=$(curl -fsS http://localhost:8082/analytics)
printf 'Analytics antes: %s\n' "$ANALYTICS_BEFORE"
RESPONSE=$(curl -sS --max-time 25 -w '\n%{http_code}' \
  -H 'Content-Type: application/json' \
  -d '{"cliente":"Alejandra","correo":"alejandra@example.com","item":"Libro","cantidad":1,"monto":15000}' \
  http://localhost:8084/api/orders)
HTTP_CODE=${RESPONSE##*$'\n'}
BODY=${RESPONSE%$'\n'*}
printf 'HTTP %s\n%s\n' "$HTTP_CODE" "$BODY"
# Continuar solo si HTTP_CODE es 202.
test "$HTTP_CODE" = 202 || { echo 'POST no confirmado; revisar logs sin reenviar a ciegas.'; false; }
ORDER_ID=$(printf '%s' "$BODY" | python -c 'import json,sys,uuid; d=json.load(sys.stdin); assert d["status"]=="ACCEPTED"; print(uuid.UUID(d["order_id"]))')
printf 'Orden: %s\n' "$ORDER_ID"
```

En una sesion interactiva, si falla un comando anterior, detenerse antes de continuar.
Esperar unos segundos a que terminen los consumidores y consultar:

```bash
curl -fsS http://localhost:8083/orders/status | python -c 'import json,sys; rows=json.load(sys.stdin); print(json.dumps([r for r in rows if r["order_id"]==sys.argv[1]],ensure_ascii=False,indent=2))' "$ORDER_ID"
dc logs --since=10m order-processor billing-worker inventory-worker notification-worker | grep -F "$ORDER_ID"
dc exec -T postgres sh -c "psql -U \"\$POSTGRES_USER\" -d \"\$POSTGRES_DB\" -c \"SELECT order_id,product_name,quantity,status FROM stock_reservations WHERE order_id = '$ORDER_ID'::uuid; SELECT product_name,available_quantity,version FROM inventory_items WHERE product_name = 'Libro';\""
curl -fsS http://localhost:8082/analytics
dc exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:9092 --describe --group analytics-service
```

Dashboard debe contener para ese UUID payment=PAYMENT_SUCCESS, stock=STOCK_RESERVED
(si habia stock) y email=EMAIL_SENT. PostgreSQL debe mostrar una reserva por una unidad
y el stock debe haber bajado en uno. Analytics debe aumentar total_orders en 1 y
total_amount en 15000, sin otros envios ni reinicios entre capturas. Analytics no
expone consultas individuales por UUID: su evidencia es agregada, junto al avance
del offset del consumidor. Un Dashboard sin la orden aun no demuestra fallo: revisar
logs y volver a consultar, sin crear otra orden.

Verificar los eventos retenidos en Kafka sin usar los grupos de los microservicios:

```bash
for TOPIC in new_orders billing_events inventory_events notification_events; do
  printf '\nTopic: %s\n' "$TOPIC"
  dc exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh \
    --bootstrap-server kafka:9092 --topic "$TOPIC" --from-beginning \
    --timeout-ms 10000 --consumer-property enable.auto.commit=false 2>/dev/null | grep -F "$ORDER_ID"
done
```

El consumidor de inspeccion tiene timeout; puede terminar con codigo no cero al
agotarlo. No altera los grupos de aplicacion ni consume mensajes de RabbitMQ.

## Caso invalido

```bash
curl -sS --max-time 25 -w '\nHTTP %{http_code}\n' \
  -H 'Content-Type: application/json' \
  -d '{"cliente":" ","correo":"invalido","item":"Libro","cantidad":0,"monto":0}' \
  http://localhost:8084/api/orders
```

Esperado: HTTP 400 con error y campos; no debe generar una nueva orden ni incrementar
Analytics. El caso 503 y la espera de confirmacion tienen pruebas con mocks; no
detener Kafka para probar fallos hasta planificar esa prueba por separado.

## Verificacion automatizada

```bash
mvn -f order-api/pom.xml verify
git diff --check
git status --short
git check-ignore .env order-api/target/order-api-0.0.1-SNAPSHOT.jar
```

No versionar .env, target, logs, respuestas temporales ni datos de volumenes.
La existencia de pruebas automatizadas aprobadas no sustituye la comprobacion real
de los diez contenedores y la cadena completa con el UUID devuelto por la API.
