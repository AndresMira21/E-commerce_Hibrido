# E-commerce local

Ejecuta los comandos en PowerShell desde la raíz del repositorio. Necesitas un JDK 17 o superior con `java` y `keytool`, y una base **local de desarrollo** PostgreSQL. El usuario de la base necesita permisos de creación en el esquema `orders` para que Flyway ejecute las migraciones. Usa una base propia para las pruebas de registro.

Si ya tienes Order API abierto, detén esa instancia con **Ctrl+C** en su consola antes de empaquetar. En Windows el proceso puede mantener bloqueado el JAR anterior.

### 1. Kafka, RabbitMQ, inventario y servicios

En una consola PowerShell desde la raíz, usa Docker Desktop. El nombre de Compose de este ejemplo crea una red y volúmenes propios. La base PostgreSQL de las cuentas se configura por separado en la siguiente consola.

```powershell
$env:COMPOSE_PROJECT_NAME = 'ecommerce-storefront'
$env:POSTGRES_DB = 'storefront_inventory'
$env:POSTGRES_USERNAME = 'storefront_inventory'
$env:POSTGRES_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Clave de PostgreSQL de inventario' -AsSecureString)).Password
$env:RABBITMQ_USERNAME = 'storefront_rabbit'
$env:RABBITMQ_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Clave de RabbitMQ local' -AsSecureString)).Password
$env:OPERATIONS_USERNAME = 'operaciones'
$env:OPERATIONS_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Clave del operador local, 12 caracteres o más' -AsSecureString)).Password
$compose = @('-f','compose.infra.yml','-f','compose.integration.yml','-f','compose.services.yml','-f','compose.storefront.yml')

# Inicialización de permisos para un volumen Kafka nuevo en Docker Desktop.
docker compose @compose create kafka
$volume = docker volume inspect "${env:COMPOSE_PROJECT_NAME}_kafka_data" | ConvertFrom-Json
if ($volume.Labels.'com.docker.compose.project' -ne $env:COMPOSE_PROJECT_NAME) { throw 'Volumen de Kafka inesperado' }
docker run --rm --network none --mount "type=volume,source=$($volume.Name),target=/kafka-data" alpine:3.22 chown 1000:1000 /kafka-data
docker compose @compose up -d kafka rabbitmq postgres

# Espera a que estén listos los dos brokers. Repite estos comandos si el arranque todavía está en curso.
docker compose @compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --list
docker compose @compose exec -T rabbitmq rabbitmq-diagnostics -q ping

# Declara la topología existente antes de arrancar los consumidores.
$basic = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${env:RABBITMQ_USERNAME}:${env:RABBITMQ_PASSWORD}"))
$headers = @{ Authorization = "Basic $basic" }
$management = 'http://127.0.0.1:15672/api'
Invoke-RestMethod -Method Put -Uri "$management/exchanges/%2F/order_tasks" -Headers $headers -ContentType 'application/json' -Body '{"type":"fanout","durable":true,"auto_delete":false,"internal":false,"arguments":{}}'
foreach ($queue in 'billing_queue','inventory_queue','notification_queue') {
  Invoke-RestMethod -Method Put -Uri "$management/queues/%2F/$queue" -Headers $headers -ContentType 'application/json' -Body '{"durable":true,"auto_delete":false,"arguments":{}}'
  Invoke-RestMethod -Method Post -Uri "$management/bindings/%2F/e/order_tasks/q/$queue" -Headers $headers -ContentType 'application/json' -Body '{"routing_key":"","arguments":{}}'
}
docker compose @compose up -d --build order-processor billing-worker inventory-worker notification-worker analytics-service dashboard-aggregator
```

Inventory Worker usa `public.inventory_items`. Crea o carga existencias autorizadas en esa base con `product_name` igual al SKU del catálogo, por ejemplo `nexo` y `trazo`. Un SKU no registrado aparece agotado. Para comprobar el circuito en una base de pruebas recién creada, se pueden insertar existencias de prueba sin sobrescribir filas existentes:

```powershell
docker compose @compose exec -T postgres psql -U $env:POSTGRES_USERNAME -d $env:POSTGRES_DB -v ON_ERROR_STOP=1 -c "INSERT INTO public.inventory_items(product_name, available_quantity, version) VALUES ('nexo',16,0),('trazo',16,0) ON CONFLICT (product_name) DO NOTHING;"
```

### Inventario original en este equipo

Si ya funciona `e-commerce_hibrido-postgres-1`, usa su base `ecommerce_inventory` en **5433**. Evita arrancar otro PostgreSQL en el mismo puerto. Desde la raíz del repositorio puedes volver a ejecutar la carga aprobada con estos comandos. La carga inserta únicamente SKU nuevos y conserva cada existencia ya registrada, incluida la de `Libro`.

```powershell
Get-Content scripts/cargar-inventario-local.sql | docker exec -i e-commerce_hibrido-postgres-1 sh -c 'exec psql -X -U "$POSTGRES_USER" -d ecommerce_inventory'
Get-Content scripts/configurar-lector-inventario-local.sql | docker exec -i e-commerce_hibrido-postgres-1 sh -c 'exec psql -X -U "$POSTGRES_USER" -d ecommerce_inventory'
docker exec -it e-commerce_hibrido-postgres-1 sh -c 'exec psql -X -U "$POSTGRES_USER" -d ecommerce_inventory'
```

En la consola de PostgreSQL, ejecuta `\password storefront_inventory_reader` para elegir o rotar la clave sin mostrarla. Luego sal con `\q`. El lector tiene acceso de consulta a `public.inventory_items` y usa transacciones de solo lectura. El Dashboard local está en **http://127.0.0.1:8083/** y solicita credenciales de operaciones.

### 2. Order API y tienda

```powershell
Set-Location 'C:\Users\maria\Documents\ecommerce-hibrido\E-commerce_Hibrido'
if (-not (Get-Command keytool -ErrorAction SilentlyContinue)) {
  $env:Path = 'C:\Program Files\Java\jdk-24\bin;' + $env:Path
}
$env:POSTGRES_URL = 'jdbc:postgresql://127.0.0.1:55432/ecommerce_dev'
$env:POSTGRES_USERNAME = Read-Host 'Usuario PostgreSQL local'
$dbSecret = Read-Host 'Contraseña de PostgreSQL local' -AsSecureString
$env:POSTGRES_PASSWORD = [System.Net.NetworkCredential]::new('', $dbSecret).Password
$env:STOREFRONT_FRONTEND_ROOT = (Resolve-Path frontend).Path
$env:STOREFRONT_INVENTORY_URL = 'jdbc:postgresql://127.0.0.1:5433/ecommerce_inventory'
$env:STOREFRONT_INVENTORY_USERNAME = 'storefront_inventory_reader'
$env:STOREFRONT_INVENTORY_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Clave del lector de inventario' -AsSecureString)).Password
$env:KAFKA_BOOTSTRAP_SERVERS = 'localhost:9094'
$env:STOREFRONT_DISPATCH_ENABLED = 'true'

.\mvnw.cmd -f order-api/pom.xml package
if ($LASTEXITCODE) { throw 'No se pudo empaquetar Order API.' }

$localKey = Join-Path $env:TEMP 'ecommerce-storefront-local.p12'
if (-not (Test-Path -LiteralPath $localKey)) {
  keytool -genkeypair -alias storefront-local -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore $localKey -dname 'CN=localhost' -ext 'SAN=dns:localhost,ip:127.0.0.1' -validity 30
}
$tlsSecret = Read-Host 'Contraseña del certificado local' -AsSecureString
$env:SERVER_SSL_KEY_STORE_PASSWORD = [System.Net.NetworkCredential]::new('', $tlsSecret).Password
$env:SERVER_SSL_KEY_STORE = 'file:' + $localKey
$env:SERVER_SSL_KEY_STORE_TYPE = 'PKCS12'
$env:SERVER_SSL_ENABLED = 'true'
$env:SERVER_PORT = '8443'
java -jar order-api/target/order-api-0.0.1-SNAPSHOT.jar --spring.profiles.active=storefront
```

Abre **https://localhost:8443/** en Chrome. El certificado generado es autofirmado. Chrome puede mostrar un aviso local para ese certificado. Usa una cuenta de prueba. El perfil escucha en la interfaz de bucle local y sirve el frontend y la API desde el mismo origen. Para revisar cuentas y catálogo basta PostgreSQL.

Si levantaste un inventario alternativo en la sección 1, ajusta solo las tres variables `STOREFRONT_INVENTORY_*` para apuntar a su base y lector. Mantén `POSTGRES_URL` apuntando a la base de cuentas. Reinicia Order API al cambiar esas variables. La carga SQL se refleja sin reiniciar Inventory Worker, PostgreSQL, Kafka ni RabbitMQ.

Si todos los productos muestran **Inventario no disponible**, comprueba las variables en la misma terminal desde la que vas a iniciar Java. Configurarlas en otra terminal o después de arrancar Spring Boot deja al proceso en ejecución con su configuración anterior. Con el usuario lector vacío, Order API devuelve disponibilidad desconocida antes de conectar a PostgreSQL. Si el usuario está configurado y falla la conexión o la consulta, revisa en la consola de Order API el mensaje `No fue posible consultar inventario` y su `SQLState`.

Si ya tienes el JAR de esta versión, puedes omitir el empaquetado y volver a ejecutar el comando `java -jar` desde la terminal configurada. Para comprobar la clave antes de reiniciar Order API, usa `psql -X -h 127.0.0.1 -p 5433 -U storefront_inventory_reader -d ecommerce_inventory -W -c "SELECT count(*) FROM public.inventory_items;"`. PostgreSQL solicitará la contraseña de forma interactiva.

Para procesar compras locales, inicia Order API con `STOREFRONT_DISPATCH_ENABLED=true` y `KAFKA_BOOTSTRAP_SERVERS=localhost:9094`. Con la publicación desactivada, las compras se registran y sus órdenes quedan en la cola persistente, por lo que el stock no cambia. Al reiniciar con la publicación habilitada, Order API procesa las órdenes que seguían pendientes. Revisa el pedido existente en **Mis pedidos** antes de volver a comprar.

### Recorrido local

1. Selecciona **Comprar** en un destacado. La tienda abrirá el inicio de sesión con el producto seleccionado.
2. Elige **Crear cuenta** y usa una contraseña de al menos 6 caracteres y como máximo 72 bytes. La cuenta se guarda en PostgreSQL y, si llegaste desde una compra, el navegador vuelve a la ficha del producto. El mínimo de seis caracteres es para el entorno académico local. Una versión pública debe aplicar una política de contraseñas más robusta.
3. Abre **Catálogo**, agrega productos mediante **Agregar al carrito** y abre el contador del encabezado. Puedes ajustar cantidades de 1 a 99, quitar artículos, vaciar el carrito y revisar el total en COP.
4. Completa **Datos de entrega** con información de prueba. Elige tarjeta de prueba o pago contra entrega simulado y revisa el total validado por Order API. La tarjeta solo muestra una referencia enmascarada.
5. **Confirmar compra** crea la compra y una orden técnica por SKU en PostgreSQL. El publicador envía las órdenes pendientes a Kafka. Abre **Mis pedidos → Ver seguimiento** para ver los resultados persistidos de los tres workers. La vista se actualiza mediante el WebSocket privado y vuelve a consultar el historial al reconectar.
6. La línea de tiempo **Transporte simulado** avanza manualmente y vive en memoria. Cerrar sesión invalida el acceso a la compra y borra el carrito y el borrador local. La compra real permanece en PostgreSQL y se recupera al iniciar sesión con la misma cuenta.
7. Abre la campana del encabezado para ver y marcar avisos. La compra registrada y los resultados confirmados generan notificaciones persistentes. El WebSocket privado señala cambios y, al reconectar, la tienda recarga la bandeja desde PostgreSQL. Los avisos se generan para compras y resultados procesados desde la instalación de la migración V5.

El perfil `storefront` bloquea las rutas heredadas `/api/orders` y `/order`. La compra usa el catálogo de precios de Order API y un usuario de solo lectura para consultar el inventario de Inventory Worker. La reserva se ejecuta en el worker después de que RabbitMQ distribuye la tarea.

### Pruebas automatizadas

En otra consola configura las mismas variables de PostgreSQL y `STOREFRONT_FRONTEND_ROOT`, apuntando a una **base local de pruebas**. La suite crea cuentas de prueba con correos únicos.

```powershell
$env:STOREFRONT_POSTGRES_TEST = 'true'
.\mvnw.cmd -f order-api/pom.xml test
```

Con el servidor local levantado, confirma la respuesta directa para un visitante sin cookie.

```powershell
curl.exe -k -s -o NUL -w "%{http_code}" https://localhost:8443/api/catalog/products
# Esperado: 401
```
