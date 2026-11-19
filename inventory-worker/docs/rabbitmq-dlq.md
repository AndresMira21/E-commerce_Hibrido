# Preparacion de la DLQ de inventario

Procedimiento preferido para la entrega final: `scripts/configure-dlq.ps1` desde
la raiz del repositorio. Inspecciona las tres colas, destinos y politicas antes de
escribir; rechaza politicas ajenas coincidentes o argumentos incompatibles. Por
defecto solo consulta; `-Apply` crea destinos faltantes y aplica/verifica politicas
exactas. No consume ni purga mensajes. No se ejecuto contra RabbitMQ en esta auditoria.
Los comandos manuales siguientes se conservan como referencia; no ejecutarlos si
hay politicas coincidentes que deban preservarse o combinarse.

```powershell
$cred = Get-Credential
./scripts/configure-dlq.ps1 -Credential $cred
# Solo despues de revisar el resultado:
./scripts/configure-dlq.ps1 -Credential $cred -Apply
```

Estas instrucciones NO se han ejecutado. Ejecutarlas mas adelante desde la raiz
del repositorio, con RabbitMQ disponible. No consumen mensajes ni borran colas.
No iniciar inventory-worker antes de verificar la topologia y la politica.

## 1. Crear el destino antes de rechazar mensajes

Spring AMQP declara inventory_dlx (directo, durable), inventory_dead_queue
(durable) y el binding inventory.failed mediante InventoryRabbitConfig.
Para preparar el destino sin arrancar consumidores, en la integracion futura se
puede iniciar el worker con SPRING_RABBITMQ_LISTENER_SIMPLE_AUTO_STARTUP=false
y PostgreSQL configurado. RabbitAdmin declarara la topologia al abrir la conexion.
Verificar su existencia en la interfaz de administracion antes de continuar.
No redeclarar inventory_queue ni agregarle argumentos x-dead-letter-*.

## 2. Comprobar las politicas existentes

En PowerShell, desde la raiz del repositorio:

```powershell
docker compose -f compose.infra.yml exec -T rabbitmq rabbitmqctl list_policies -p /
```

Si existe otra politica que afecte inventory_queue, revisar su prioridad y sus
definiciones antes de aplicar esta. RabbitMQ aplica la politica de mayor prioridad;
no combinar ni sobrescribir politicas de otros integrantes sin revisar su efecto.
Si se usa otro virtual host, reemplazar / en todos los comandos.

## 3. Aplicar la politica especifica (no ejecutada todavia)

Se envia el JSON por stdin para evitar diferencias de escape entre PowerShell y Docker.

```powershell
'{"dead-letter-exchange":"inventory_dlx","dead-letter-routing-key":"inventory.failed"}' | docker compose -f compose.infra.yml exec -T rabbitmq sh -c 'IFS= read -r definition; rabbitmqctl set_policy -p / inventory-dlq "^inventory_queue$" "$definition" --apply-to queues --priority 10'
```

La expresion exacta solo afecta inventory_queue. No modifica billing_queue ni
notification_queue. La politica se aplica sin recrear la cola y sin modificar
su declaracion original sin argumentos. No incluye credenciales en el archivo.

## 4. Comprobar politica, destino y binding

```powershell
docker compose -f compose.infra.yml exec -T rabbitmq rabbitmqctl list_policies -p /
docker compose -f compose.infra.yml exec -T rabbitmq rabbitmqctl list_queues -p / name durable arguments policy messages_ready
docker compose -f compose.infra.yml exec -T rabbitmq rabbitmqctl list_exchanges -p / name type durable
docker compose -f compose.infra.yml exec -T rabbitmq rabbitmqctl list_bindings -p / source_name destination_name destination_kind routing_key
```

Verificar: inventory_queue usa inventory-dlq; inventory_dlx es direct y durable;
inventory_dead_queue es durable; el binding usa inventory.failed. Revisar tambien
los permisos del usuario sobre la cola y el exchange.

## Comportamiento y comprobaciones pendientes

- Errores de formato/validacion: un intento y rechazo sin reencolacion.
- Errores transitorios de persistencia o Kafka: hasta tres intentos totales,
  separados por 200 y 400 ms. Cada llamada al servicio abre una nueva transaccion.
- Si Kafka falla despues del commit, la reserva no se revierte: el siguiente
  intento recupera el resultado existente y vuelve a publicar sin descontar stock.
- Tras agotar intentos, RejectAndDontRequeueRecoverer lanza una excepcion de
  rechazo; no devuelve una respuesta exitosa ni confirma el mensaje fallido.
- No hay TTL ni binding de retorno desde la DLQ: no se crea un ciclo automatico.
- Sin politica o sin destino disponible, un mensaje rechazado puede perderse.
  La DLQ no constituye una garantia de entrega exactamente una vez; Kafka puede
  recibir duplicados, por ejemplo si una confirmacion se pierde.
- Pendiente con RabbitMQ real: verificar ACK/NACK, x-death, llegada del cuerpo
  original a la DLQ, ausencia de PRECONDITION_FAILED y que las otras colas no se
  afecten. Probar fallos solo con ordenes nuevas y controladas, no con la pendiente.

Referencia: https://www.rabbitmq.com/docs/dlx
