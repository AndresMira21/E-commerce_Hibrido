# Microservicios de Miranda

Los servicios son tres aplicaciones Spring Boot independientes y usan los contratos JSON acordados. Las estadísticas y estados agregados viven en memoria: al reiniciar cada servicio se reinician esos datos.

## Pruebas y ejecución local

Ejecuta desde cada carpeta:

```bash
cd notification-worker && mvn test
cd analytics-service && mvn test
cd dashboard-aggregator && mvn test
```

Para compilar y empaquetar: `mvn package` en la carpeta correspondiente. Para ejecutar localmente, exporta `KAFKA_BOOTSTRAP_SERVERS=localhost:9092`; en el worker define también `RABBITMQ_HOST=localhost`. Los endpoints son `http://localhost:8081` (worker), `http://localhost:8082/analytics`, `http://localhost:8083/orders/status` y el dashboard web `http://localhost:8083/`.

## Integración en Docker Compose

Combina los servicios de `compose.services.yml` con el `docker-compose.yml` principal del equipo, conservando una sola instancia de Kafka y RabbitMQ. Requieren que los servicios de infraestructura se llamen `kafka` y `rabbitmq` en la misma red. Los límites de los servicios propios suman 0.5 CPU y 256–320 MB de memoria por servicio. Ajusta memoria del host teniendo en cuenta Kafka y RabbitMQ, que no están incluidos aquí.

Credenciales RabbitMQ se inyectan por `RABBITMQ_USERNAME` y `RABBITMQ_PASSWORD`; los valores `guest` son solo un fallback para desarrollo local. Para contenedores, configura credenciales que coincidan con las del servicio compartido.

## Mensajes de prueba de integración

Crear los topics en el broker compartido (`new_orders`, `billing_events`, `inventory_events`, `notification_events`) y publicar los contratos tal como se acordaron. Ejemplos para un Kafka con `kafka-console-producer` ejecutado dentro del contenedor `kafka`:

```bash
docker compose exec -T kafka kafka-console-producer --bootstrap-server kafka:9092 --topic new_orders <<'EOF'
{"order_id":"a1b2c3d4-1234-5678-9012-abcdefabcdef","cliente":"Alejandra","correo":"alejandra@example.com","item":"Libro","cantidad":2,"monto":30000}
EOF

docker compose exec -T kafka kafka-console-producer --bootstrap-server kafka:9092 --topic billing_events <<'EOF'
{"order_id":"a1b2c3d4-1234-5678-9012-abcdefabcdef","status":"PAYMENT_SUCCESS"}
EOF

docker compose exec -T kafka kafka-console-producer --bootstrap-server kafka:9092 --topic inventory_events <<'EOF'
{"order_id":"a1b2c3d4-1234-5678-9012-abcdefabcdef","status":"STOCK_RESERVED"}
EOF

docker compose exec -T kafka kafka-console-producer --bootstrap-server kafka:9092 --topic notification_events <<'EOF'
{"order_id":"a1b2c3d4-1234-5678-9012-abcdefabcdef","status":"EMAIL_SENT"}
EOF
```

Revisa `GET /analytics` para observar órdenes recibidas y `GET /orders/status` o la página `/` para comprobar el agregado. Para probar `notification-worker`, con el plugin de administración habilitado y el puerto 15672 publicado, ejecuta desde el host:

```bash
curl -u "$RABBITMQ_USERNAME:$RABBITMQ_PASSWORD" \
  -H 'content-type: application/json' \
  -X POST 'http://localhost:15672/api/exchanges/%2F/order_tasks/publish' \
  -d '{"properties":{"content_type":"application/json"},"routing_key":"","payload":"{\"order_id\":\"a1b2c3d4-1234-5678-9012-abcdefabcdef\",\"accion\":\"PROCESS_ORDER\",\"item\":\"Libro\",\"cantidad\":2,\"monto\":30000}","payload_encoding":"string"}'
```

El exchange fanout ignora la routing key. El worker simula la notificación y publica `EMAIL_SENT` en `notification_events`.

El consumidor de notificaciones reintenta hasta tres veces con pausa exponencial. Si el procesamiento sigue fallando, rechaza el mensaje sin reencolarlo para evitar un ciclo infinito; un dead-letter queue debe ser configurado por el Compose principal si el equipo desea conservar los mensajes rechazados.
