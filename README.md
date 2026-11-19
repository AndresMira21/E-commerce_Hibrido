# E-commerce Hibrido: integracion local

## Guia de exposicion y revision final

Esta guia complementa las instrucciones existentes. Las pruebas automatizadas no
demuestran por si solas entrega entre brokers, persistencia real ni recepcion WebSocket.
En esta revision no se enviaron ordenes ni se consumieron mensajes manualmente.

### 1. Objetivo y arquitectura

Simulamos la recepcion y procesamiento asincrono de pedidos. La API acepta una
solicitud, deja una orden auditable y emite un evento. Cada trabajador tiene una
responsabilidad independiente y comunica su resultado, sin llamadas HTTP entre workers.

### 2. Flujo completo

```text
POST /order (o /api/orders) -> validacion -> Orden + auditoria PostgreSQL (orders)
  -> Kafka new_orders -> order-processor -> RabbitMQ Fanout order_tasks
       |                                  | billing_queue -> billing_events
       |                                  | inventory_queue -> inventory_events
       |                                  | notification_queue -> notification_events
       +-> analytics-service              +-> Dashboard -> /topic/orders (WebSocket)
                                            inventory-worker -> PostgreSQL (public)
```

### 3. Responsabilidades de los siete microservicios

| Servicio | Responsabilidad |
|---|---|
| order-api | Valida, construye Orden mediante Builder, persiste y confirma publicacion |
| order-processor | Traduce new_orders al comando PROCESS_ORDER y confirma RabbitMQ |
| billing-worker | Simula pago determinista y descuentos Strategy; publica resultado |
| inventory-worker | Reserva stock transaccional e idempotentemente con @Version |
| notification-worker | Simula aviso al cliente; publica EMAIL_SENT |
| analytics-service | Deduplica new_orders por UUID y calcula agregados recibidos |
| dashboard-aggregator | Combina estados por UUID y actualiza la web por WebSocket |

### 4. Por que Kafka

El log permite consumidores independientes: Order Processor y Analytics leen
new_orders con grupos diferentes. Los topics de resultados alimentan el Dashboard.
Un consumidor de inspeccion nunca debe usar los grupos de las aplicaciones.

### 5. Por que RabbitMQ

Distribuye tareas a workers mediante colas independientes. Los acknowledgements,
reintentos del consumidor y dead lettering permiten controlar errores de tareas.

### 6. Evento y comando

Un evento describe un hecho: una orden recibida o un resultado PAYMENT_SUCCESS.
Un comando pide una accion: PROCESS_ORDER. Se conservan todos los contratos JSON,
topics, exchanges, nombres de colas y puertos existentes.

### 7. Fanout y confirmaciones

order_tasks entrega una copia a cada cola vinculada; la routing key de envio es
vacia. Order Processor usa publisher confirms correlacionados y mandatory returns,
y espera hasta 10 segundos la confirmacion antes de completar el listener Kafka.
Un ACK del broker no significa que los workers terminaron. Un retorno NO_ROUTE
detecta ausencia total de rutas, no que las tres colas esten presentes: verificar
los tres bindings. Si falta solo uno, otro binding puede permitir ACK sin retorno.

El consumidor Kafka usa ack-mode=record y auto-commit desactivado. Tiene hasta tres
intentos totales separados por 500 ms; errores permanentes no se reintentan. Si
persisten, detiene orderProcessor y lanza error sin dar el offset por recuperado.
Revisar logs, corregir la causa y reiniciar manualmente el servicio. No salta un
registro invalido silenciosamente ni crea un topic nuevo. Un registro invalido
puede bloquear el avance hasta intervencion, una decision explicita para no perderlo.

### 8. PostgreSQL y auditoria

Inventario usa public y su historial Flyway original; Orden usa orders y un historial
separado. Orden y cambios se confirman antes de enviar a Kafka. Estados de publicacion:
PENDIENTE_PUBLICACION, PUBLICADA y PUBLICACION_INCIERTA. No se borra una orden por un
error del broker. Las migraciones existentes no se alteraron en esta revision.

### 9. Modelo de dominio rico

Orden protege sus invariantes y expone transiciones semanticas, sin setters indiscriminados.
Inventario reserva dentro de una transaccion. Su clave por order_id evita dos filas
para la misma orden; @Version detecta competencia entre ordenes por un mismo producto.

### 10. Records y Fail-Fast

Dinero, IdentificadorOrden y DetalleItem son Value Objects inmutables. Los records de
mensajes validan al construirse. JSON invalido y datos permanentes no deben llegar a
la logica de negocio; los mensajes que ya existen pueden no cumplir las validaciones
actuales y requieren diagnostico, no borrado automatico.

### 11. Strategy, Builder y Repository

Strategy separa SinDescuento y DescuentoPorMonto (10% desde 100000). El pago se aprueba
si el total final no supera 1000000; es una simulacion sin proveedor ni cobro real.
Builder construye Orden completa. Spring Data implementa los repositorios para
Orden, InventoryItem y StockReservation. Los montos se calculan con BigDecimal.

### 12. SOLID y DIP

Recepcion, negocio y publicacion se mantienen separados y se inyectan por constructor.
OrderTaskPublisher abstrae RabbitMQ, PaymentEventPublisher/InventoryEventPublisher
abstraen Kafka y OrderEventPublisher separa el dominio de la API de su adaptador Kafka.
No se introdujeron Saga, outbox ni plataformas nuevas para resolver esta revision.

### 13. JUnit 5 y Mockito con AAA

Arrange prepara entradas y mocks; Act ejecuta; Assert verifica resultados e interacciones.
Las pruebas ejercitan validaciones, descuentos, transacciones con gestor simulado,
confirmaciones, reintentos acotados, idempotencia y agregacion incremental. Se conservan
las pruebas previas. Los Dockerfiles de los siete servicios ahora ejecutan mvn verify.
Esto verifica Java 17 durante el build; las ejecuciones Maven locales usan el JDK disponible.

### 14. Reintentos y DLQ

Billing, Inventory y Notification: tres intentos totales, pausas de 200 y 400 ms para
errores transitorios, sin reencolacion infinita. Formato/validacion permanente e
interrupciones no se reintentan. El interceptor de recepcion rechaza tras agotar intentos;
no debe confundirse con setRetryTemplate de una fabrica, que se refiere al envio de replies.

| Cola origen | Exchange directo durable | DLQ durable | Routing key |
|---|---|---|---|
| billing_queue | billing_dlx | billing_dead_queue | billing.failed |
| inventory_queue | inventory_dlx | inventory_dead_queue | inventory.failed |
| notification_queue | notification_dlx | notification_dead_queue | notification.failed |

No se agregan argumentos DLX a las colas origen existentes, evitando PRECONDITION_FAILED.
La configuracion requiere politicas RabbitMQ, no solo beans Java. El script siguiente
consulta primero y rehusa modificar politicas ajenas coincidentes. No fue aplicado
en esta revision; sin politica/destino verificados, un rechazo puede perderse.

Desde PowerShell en la raiz, con el usuario local autorizado (sin escribir claves en Git):

```powershell
$cred = Get-Credential
./scripts/configure-dlq.ps1 -Credential $cred
# Revisar la salida; solo despues aplicar y comprobar:
./scripts/configure-dlq.ps1 -Credential $cred -Apply
./scripts/configure-dlq.ps1 -Credential $cred
```

El script no borra, no consume, no purga ni configura retorno automatico desde DLQ.
Antes de modificar politicas se revisan las tres colas para evitar cambios parciales
por un conflicto conocido. Un fallo de red durante la aplicacion aun puede dejar
una configuracion parcial: repetir inspeccion antes de arrancar consumers.

### 15. Observabilidad

Buscar order_id en logs de recepcion, pagos, reservas y publicaciones confirmadas.
Los reintentos registran numero y tipo de error. Las recuperaciones registran cola y
tipo sin imprimir el cuerpo. Analytics registra UUID y si fue insertado o duplicado;
Dashboard registra el agregado y el destino /topic/orders. No activar logs de cuerpos
ni registrar credenciales, correo o nombre del cliente para diagnosticar un pedido.

### 16. Limites conocidos

- No existe atomicidad distribuida PostgreSQL/Kafka ni Kafka/RabbitMQ. Puede haber
  eventos duplicados al perder confirmaciones. Nunca se promete exactamente una vez.
- Billing y Notification son simulaciones deterministas, sin efectos externos reales:
  un reintento puede volver a publicar el mismo UUID. No tienen un libro de cobros
  persistente ni deduplicacion durable; no son implementaciones de pagos/correo reales.
- Inventario reutiliza la reserva confirmada tras un error Kafka sin descontar otra vez.
- Analytics y Dashboard guardan datos en memoria: el reinicio puede perder la vista
  reconstruida si los offsets ya avanzaron. No reiniciar grupos ni offsets automaticamente.
- Analytics mide ordenes recibidas: total_amount NO representa ingresos cobrados.
  average_amount es promedio de montos recibidos, aunque PAYMENT_FAILED llegue despues.
- EMAIL_SENT significa simulacion exitosa y evento confirmado, no entrega SMTP real.
- El Dashboard conserva campos entre eventos y admite su llegada en distinto orden;
  sin version en el contrato, un evento atrasado contradictorio es last-arrival-wins.
- DLQ clasicas, confirmaciones y datos durables no constituyen por si solos una
  garantia absoluta de entrega frente a todos los fallos del cluster.
- No se ejecutaron fallos reales ni E2E en esta revision si Docker no esta disponible.

### 17. Docker Compose y preparacion segura

Los tres Compose deben producir exactamente diez servicios (siete aplicaciones y
tres centrales), con red ecommerce, limites y volumenes existentes. No usar otro
project-name si se quieren reutilizar los volumenes originales. No modificar .env.

```bash
dc() { docker compose -f compose.infra.yml -f compose.services.yml -f compose.integration.yml "$@"; }
docker info
dc config --quiet
dc config --services
dc ps
dc logs --tail=50 kafka rabbitmq postgres
# Revisar el backlog SIN consumirlo ni purgarlo:
dc exec -T rabbitmq rabbitmqctl list_queues -p / name messages_ready messages_unacknowledged policy
dc exec -T rabbitmq rabbitmqctl list_policies -p /
dc exec -T rabbitmq rabbitmqctl list_bindings -p / source_name destination_name routing_key
```

Antes de reconstruir/iniciar los workers, revisar backlog y aplicar/verificar las
politicas con el script. Arrancar workers puede consumir mensajes pendientes: no
presentarlo como una operacion de solo lectura. Destinos faltantes pueden prepararse
con el script sin arrancar workers, siempre que las colas origen ya existan.
Una vez autorizada la integracion manual:

```bash
dc build order-api order-processor billing-worker inventory-worker notification-worker analytics-service dashboard-aggregator
dc up -d kafka rabbitmq postgres
dc ps
# Continuar solo con brokers disponibles y postgres healthy.
dc up -d order-processor billing-worker inventory-worker notification-worker analytics-service dashboard-aggregator order-api
```

Kafka/RabbitMQ tienen dependencia service_started, no healthcheck: revisar disponibilidad
antes del POST. No ejecutar down -v, prune, DROP, TRUNCATE ni purgas.

### 18. Una unica prueba E2E manual controlada

No se envia automaticamente. Antes comprobar Libro y Analytics, abrir Dashboard y
la suscripcion WebSocket de la seccion 20. Verificar stock suficiente y una sola
ventana de envio, sin otras ordenes simultaneas. Los comandos usan Python 3 para JSON.

```bash
dc exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT product_name,available_quantity,version FROM inventory_items WHERE product_name = '\''Libro'\'';"'
curl -fsS http://localhost:8082/analytics
# Solo una vez; no repetir ante timeout o 503 sin revisar auditoria.
RESPONSE=$(curl -sS --max-time 25 -w '\n%{http_code}' -H 'Content-Type: application/json' \
  -d '{"cliente":"Alejandra","correo":"alejandra@example.com","item":"Libro","cantidad":1,"monto":15000}' \
  http://localhost:8084/order)
HTTP_CODE=${RESPONSE##*$'\n'}
BODY=${RESPONSE%$'\n'*}
printf 'HTTP %s\n%s\n' "$HTTP_CODE" "$BODY"
# Continuar solo con 202. Si no, detenerse y conservar la evidencia.
ORDER_ID=$(printf '%s' "$BODY" | python -c 'import json,sys,uuid; d=json.load(sys.stdin); assert d["status"]=="ACCEPTED"; print(uuid.UUID(d["order_id"]))')
dc exec -T postgres sh -c "psql -U \"\$POSTGRES_USER\" -d \"\$POSTGRES_DB\" -c \"SELECT order_id,estado FROM orders.ordenes WHERE order_id='$ORDER_ID'::uuid; SELECT estado,fecha,motivo FROM orders.orden_cambios WHERE order_id='$ORDER_ID'::uuid ORDER BY secuencia; SELECT order_id,status,quantity FROM stock_reservations WHERE order_id='$ORDER_ID'::uuid; SELECT product_name,available_quantity,version FROM inventory_items WHERE product_name='Libro';\""
dc logs --since=10m order-api order-processor billing-worker inventory-worker notification-worker analytics-service dashboard-aggregator | grep -F "$ORDER_ID"
curl -fsS http://localhost:8082/analytics
curl -fsS http://localhost:8083/orders/status | python -c 'import json,sys; print(json.dumps([r for r in json.load(sys.stdin) if r["order_id"]==sys.argv[1]],indent=2))' "$ORDER_ID"
```

Esperado: orden PUBLICADA, auditoria, una reserva, stock disminuye en uno y mismo
UUID con PAYMENT_SUCCESS, STOCK_RESERVED y EMAIL_SENT. Analytics sube una orden y
15000 si no hubo otras ordenes. Eso no demuestra cobro real. No reinicializar Libro.

### 19. Comprobar mensajes sin consumir manualmente los existentes

Usar logs correlacionados, metadatos de consumidores y Management UI. En RabbitMQ
ver los conteos/bindings, sin pulsar Get messages ni Purge. No ejecutar console-consumer
sobre mensajes retenidos durante esta auditoria. Para evidencia Kafka de la prueba
manual, configurar antes del POST un consumidor de observacion con grupo unico y
auto.offset.reset=latest; solo recibira eventos nuevos, nunca grupos de aplicacion.

```bash
dc exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:9092 --describe --group order-processor-group
dc exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:9092 --describe --group analytics-service
dc exec -T rabbitmq rabbitmqctl list_queues -p / name messages_ready messages_unacknowledged policy
```

Las instrucciones anteriores del README que usan --from-beginning se conservan como
referencia historica; NO ejecutarlas en la revision sin permiso para leer historicos.

### 20. Dashboard y WebSocket

Abrir http://localhost:8083/. DevTools -> Network -> WS/SockJS permite comprobar
CONNECTED, SUBSCRIBE /topic/orders y MESSAGE con el UUID de la unica orden manual.
Para suscribirse sin crear ordenes, en la consola de esa pagina:

```javascript
const auditClient = new StompJs.Client({
  webSocketFactory: () => new SockJS('/ws'),
  onConnect: () => auditClient.subscribe('/topic/orders', m => console.log(JSON.parse(m.body)))
});
auditClient.activate();
// Preparar antes del unico POST manual. Al terminar: auditClient.deactivate();
```

Sin eventos nuevos solo se verifica conexion/suscripcion, no entrega en vivo. Consultar
/orders/status no crea ordenes. La web recupera snapshot al conectar y reconectar;
las ordenes que recibieron un evento en vivo durante la carga no se reemplazan por
un snapshot anterior. PENDING se muestra gris, PAYMENT_FAILED/OUT_OF_STOCK rojo.
Las pruebas automatizadas de agregacion no sustituyen esta comprobacion visual real.

## Cumplimiento de rubrica: orden persistida antes de Kafka

Flujo: POST /order (alias compatible /api/orders) -> Jakarta Validation -> Builder
de Orden con IdentificadorOrden, DetalleItem y Dinero -> commit PostgreSQL ->
Kafka new_orders -> order-processor -> RabbitMQ -> workers -> Dashboard.
El JSON de new_orders y la respuesta 202 ACCEPTED se conservan. El 202 requiere
confirmacion de Kafka y registro del resultado en PostgreSQL. Solicitudes invalidas
devuelven 400; fallos de persistencia o publicacion devuelven 503 sin detalles internos.

Las ordenes se almacenan en orders.ordenes y sus transiciones en orders.orden_cambios.
Las migraciones de order-api usan orders.flyway_schema_history, independiente de
public.flyway_schema_history utilizado por inventory-worker. Flyway crea el esquema
orders sin borrar ni reinicializar public; no hay baseline automatico, clean ni datos
de prueba. Hibernate sigue con ddl-auto=none. La cuenta debe poder crear ese esquema.
PostgreSQL y Flyway reales deben validarse antes de afirmar compatibilidad efectiva.

Orden es una entidad rica con Builder, estado inicial PENDIENTE_PUBLICACION,
metodos confirmarPublicacion y registrarPublicacionIncierta, @Version y sin setters
publicos. Los tres Value Objects son records con validaciones Fail-Fast.
Dinero conserva el monto recibido sin cambiar el JSON ni aplicar descuentos aqui.
OrdenRepository es Spring Data JPA; OrderService depende de OrderEventPublisher.
OrdenPersistence es un bean separado: sus transacciones REQUIRES_NEW confirman el
guardado antes del broker y luego auditan el resultado en otra transaccion.

No existe transaccion distribuida PostgreSQL/Kafka, outbox ni reenvio automatico.
Si Kafka falla, la orden queda PUBLICACION_INCIERTA: no se puede asegurar que Kafka
no la recibiera. Si cae el proceso, el executor o la base al registrar el resultado,
puede quedar PENDIENTE_PUBLICACION aunque Kafka haya recibido el evento. Esas ordenes
requieren conciliacion por UUID. Un POST nuevo genera otra orden: no repetirlo a
ciegas tras un 503 o timeout. La auditoria es transaccional, no un registro inmutable
contra administradores de base de datos. Cliente y correo son datos personales;
se deben restringir accesos y definir retencion antes de produccion.

### Verificacion pendiente en Git Bash con infraestructura existente

No usar un nombre de proyecto Compose diferente ni borrar volumenes. Desde la raiz:

```bash
dc() { docker compose -f compose.infra.yml -f compose.services.yml -f compose.integration.yml "$@"; }
dc config --quiet
docker build -t ecommerce-order-api:local ./order-api
dc up -d postgres kafka rabbitmq
dc ps
# Continuar cuando PostgreSQL este healthy y los brokers esten disponibles.
dc up -d order-processor billing-worker inventory-worker notification-worker analytics-service dashboard-aggregator
dc up -d --no-deps order-api
dc logs --tail=80 order-api
# Debe mostrar Flyway en esquema orders, sin errores de historial del esquema public.
RESPONSE=$(curl -sS --max-time 25 -w '\n%{http_code}' -H 'Content-Type: application/json' \
  -d '{"cliente":"Alejandra","correo":"alejandra@example.com","item":"Libro","cantidad":1,"monto":15000}' \
  http://localhost:8084/order)
HTTP_CODE=${RESPONSE##*$'\n'}
BODY=${RESPONSE%$'\n'*}
printf 'HTTP %s\n%s\n' "$HTTP_CODE" "$BODY"
# Detenerse si no es 202. No reenviar automaticamente.
ORDER_ID=$(printf '%s' "$BODY" | python -c 'import json,sys,uuid; d=json.load(sys.stdin); assert d["status"]=="ACCEPTED"; print(uuid.UUID(d["order_id"]))')
dc exec -T postgres sh -c "psql -U \"\$POSTGRES_USER\" -d \"\$POSTGRES_DB\" -c \"SELECT order_id,producto,cantidad,monto,estado,creada_en,actualizada_en FROM orders.ordenes WHERE order_id='$ORDER_ID'::uuid; SELECT estado,fecha,motivo FROM orders.orden_cambios WHERE order_id='$ORDER_ID'::uuid ORDER BY secuencia; SELECT installed_rank,version,success FROM orders.flyway_schema_history; SELECT installed_rank,version,success FROM public.flyway_schema_history;\""
curl -fsS http://localhost:8083/orders/status | python -c 'import json,sys; print(json.dumps([r for r in json.load(sys.stdin) if r["order_id"]==sys.argv[1]],indent=2))' "$ORDER_ID"
```

Para esa orden debe haber estado PUBLICADA, dos cambios auditados y el mismo UUID
en Dashboard una vez procesados los eventos. Libro debe tener stock previamente;
no reinicializar inventario para realizar esta prueba. Los comandos anteriores son
instrucciones, no evidencia de que la integracion se haya ejecutado en este paso.

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
