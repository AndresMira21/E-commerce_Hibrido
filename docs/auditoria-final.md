# Informe de revision final - 18 de noviembre de 2026

Rama: feature/alejandra-mensajeria. No se crearon ni cambiaron ramas y no se
realizaron commits, push, merge ni PR. No se modificaron .env, credenciales,
contratos JSON, topics, exchanges/colas existentes, puertos ni migraciones SQL.
Los nuevos destinos DLQ se declaran sin argumentos incompatibles en las colas origen.

## Correcciones comprobadas en codigo

- Billing: reintentos de recepcion acotados (tres intentos, pausas 200/400 ms),
  errores permanentes rechazados, DLQ declarada y reencolacion ilimitada desactivada.
  Error Kafka sigue siendo error de infraestructura, no PAYMENT_FAILED.
- Notification: interceptor de recepcion en lugar de retryTemplate de replies;
  tres intentos acotados, DLQ compatible, clasificacion de conversion/validacion,
  inferencia de tipo local para el header Java ajeno y confirmacion Kafka acotada.
- Inventory: se conserva reserva transaccional, @Version, idempotencia y reintentos;
  solo se mejora clasificacion de conversion y diagnostico de intentos/rechazos.
- Order Processor: confirms correlacionados y mandatory returns; espera hasta 10 s;
  Kafka con RECORD y sin auto-commit; hasta tres intentos, luego parada del consumidor
  sin dar el registro por recuperado. Requiere revision y reinicio manual.
- Analytics: conserva agregados por orden recibida, deduplicacion y BigDecimal;
  se agrega correlacion y prueba decimal. No contabiliza ingresos cobrados.
- Dashboard: se prueban seis permutaciones, fallos y ordenes aisladas. Snapshot tras
  suscripcion/reconexion evita perder eventos durante la carga; conserva campos.
- Order API: se conserva dominio rico, PostgreSQL antes de Kafka, auditoria y
  esquema orders separado. Solo se agregan logs de UUID/resultado sin PII.
- Docker: Notification, Analytics y Dashboard pasan de skipTests a mvn verify;
  permanecen las mismas imagenes, JRE Alpine, usuario no root y puertos.

## Resultados reales de Maven

Todos ejecutados con mvn -f <modulo>/pom.xml verify. Se conservaron las pruebas
anteriores y se adaptaron a la clasificacion de rechazo/confirmaciones donde cambio.
JDK local 24; compilacion release 17. Build de imagenes Java 17 pendiente en esta revision.

| Modulo | Pruebas | Fallos | Errores | Omitidas | Resultado |
|---|---:|---:|---:|---:|---|
| order-api | 72 | 0 | 0 | 0 | BUILD SUCCESS |
| order-processor | 53 | 0 | 0 | 0 | BUILD SUCCESS |
| billing-worker | 56 | 0 | 0 | 0 | BUILD SUCCESS |
| inventory-worker | 84 | 0 | 0 | 0 | BUILD SUCCESS |
| notification-worker | 17 | 0 | 0 | 0 | BUILD SUCCESS |
| analytics-service | 6 | 0 | 0 | 0 | BUILD SUCCESS |
| dashboard-aggregator | 12 | 0 | 0 | 0 | BUILD SUCCESS |
| Total | 300 | 0 | 0 | 0 | |

Logs de esta ejecucion: <modulo>/target/final-audit-verification.log (ignorados por Git).
No hubo fallos de compilacion/pruebas en las pasadas de esta revision.

## Docker, DLQ y verificaciones pendientes

Docker no esta disponible en PowerShell ni en Git Bash de esta sesion. No se ejecuto
docker compose config, build, up ni inspeccion de contenedores reales. Inventario
estatico: exactamente siete microservicios y Kafka/RabbitMQ/PostgreSQL; no equivale
a una validacion del motor Compose. Los Compose quedaron sin cambios.

scripts/configure-dlq.ps1 tiene sintaxis PowerShell valida, pero NO se ejecuto contra
la API Management. Consulta por defecto; -Apply requiere credencial interactiva.
Hace preflight de conflictos, declara destinos y aplica politicas exactas idempotentes.
No borra, consume, purga ni sobrescribe politicas ajenas. Estado efectivo de las tres
politicas: NO VERIFICADO. Detalle y comandos en README, secciones 14 y 17.

Publisher confirms: configurados y probados con mocks para ACK/NACK/retorno/interrupcion.
Su funcionamiento real con RabbitMQ no se comprobo en esta revision. Los reintentos
Rabbit/Kafka tienen pruebas locales; no se indujeron fallos reales de brokers.

Pendiente: construir imagenes actuales, verificar disponibilidad y limites reales,
topologia y politicas efectivas, ACK/NACK, x-death, proteccion de offsets Kafka al
detener, independencia de los historiales Flyway y una unica prueba E2E manual.
La prueba debe abrir primero WebSocket, comprobar stock Libro, enviar un unico POST
/order y correlacionar UUID con SQL, logs, Dashboard y Analytics. README secciones
18-20 contiene comandos exactos. No se ejecuto ni se simula como resultado real.

## Riesgos y limites que permanecen

- Sin transaccion distribuida, outbox ni exactamente una vez. Confirmacion perdida
  puede duplicar eventos. API puede dejar publicacion incierta y exige conciliacion.
- Billing y Notification son simulaciones deterministas sin efecto externo real;
  no hay deduplicacion durable de cobros/envios ni garantia de no repetir eventos.
- Inventory reutiliza reservas confirmadas; la concurrencia PostgreSQL real no se
  demuestra con mocks. No se repitio la prueba real anterior durante esta revision.
- Sin politica DLQ y destino verificados, un rechazo puede perderse. Politicas
  existentes conflictivas requieren intervencion manual; el script las rechaza.
- mandatory detecta cero rutas, no la presencia de los tres bindings Fanout.
- Order Processor detenido requiere intervencion; un JSON invalido no se salta solo.
- Analytics/Dashboard en memoria, sin reconstruccion automatica garantizada tras
  reinicio; eventos contradictorios sin version son last-arrival-wins.
- La web usa librerias CDN y requiere acceso a ellas para SockJS/STOMP.
- Los limites actuales suman unos 5.2 GiB mas Docker Desktop; el consumo real y
  posibles OOM solo pueden comprobarse al ejecutar. No se prometen SLA de memoria.
- EMAIL_SENT no demuestra entrega de correo; Analytics no demuestra pagos cobrados.

## Archivos por modulo

La lista detallada siguiente se obtiene del estado Git al finalizar. No incluye
target, .env, logs ni datos de volumenes.

- `README.md`
- `analytics-service/Dockerfile`
- `analytics-service/src/main/java/com/ecommerce/hibrido/analytics/consumer/OrderReceivedListener.java`
- `analytics-service/src/test/java/com/ecommerce/hibrido/analytics/service/OrderAnalyticsTest.java`
- `billing-worker/src/main/java/com/ecommerce/billingworker/listener/BillingQueueListener.java`
- `billing-worker/src/main/resources/application.properties`
- `billing-worker/src/test/java/com/ecommerce/billingworker/BillingWorkerApplicationTests.java`
- `billing-worker/src/test/java/com/ecommerce/billingworker/listener/BillingQueueListenerTests.java`
- `dashboard-aggregator/Dockerfile`
- `dashboard-aggregator/src/main/java/com/ecommerce/hibrido/dashboard/service/OrderStatusService.java`
- `dashboard-aggregator/src/main/resources/static/index.html`
- `dashboard-aggregator/src/test/java/com/ecommerce/hibrido/dashboard/service/OrderStatusServiceTest.java`
- `inventory-worker/docs/rabbitmq-dlq.md`
- `inventory-worker/src/main/java/com/ecommerce/inventoryworker/config/InventoryRabbitConfig.java`
- `notification-worker/Dockerfile`
- `notification-worker/src/main/java/com/ecommerce/hibrido/notification/config/RabbitConfiguration.java`
- `notification-worker/src/main/java/com/ecommerce/hibrido/notification/consumer/NotificationQueueListener.java`
- `notification-worker/src/main/java/com/ecommerce/hibrido/notification/service/NotificationService.java`
- `notification-worker/src/main/resources/application.yml`
- `order-api/src/main/java/com/ecommerce/orderapi/service/OrderService.java`
- `order-processor/src/main/java/com/ecommerce/orderprocessor/listener/OrderEventListener.java`
- `order-processor/src/main/java/com/ecommerce/orderprocessor/publisher/RabbitTaskPublisher.java`
- `order-processor/src/main/resources/application.properties`
- `order-processor/src/test/java/com/ecommerce/orderprocessor/OrderProcessorApplicationTests.java`
- `order-processor/src/test/java/com/ecommerce/orderprocessor/publisher/RabbitTaskPublisherTests.java`
- `billing-worker/src/main/java/com/ecommerce/billingworker/config/BillingRabbitConfig.java`
- `billing-worker/src/test/java/com/ecommerce/billingworker/config/BillingRabbitConfigTests.java`
- `docs/auditoria-final.md`
- `notification-worker/src/main/java/com/ecommerce/hibrido/notification/config/NotificationRetryConfig.java`
- `notification-worker/src/test/java/com/ecommerce/hibrido/notification/config/NotificationConversionTest.java`
- `notification-worker/src/test/java/com/ecommerce/hibrido/notification/config/NotificationRetryConfigTests.java`
- `notification-worker/src/test/java/com/ecommerce/hibrido/notification/service/KafkaNotificationEventPublisherTest.java`
- `order-processor/src/main/java/com/ecommerce/orderprocessor/config/KafkaFailureConfig.java`
- `order-processor/src/test/java/com/ecommerce/orderprocessor/config/KafkaFailureConfigTests.java`
- `scripts/configure-dlq.ps1`
