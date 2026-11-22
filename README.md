# E-commerce Híbrido

E-commerce Híbrido es una plataforma de comercio electrónico desarrollada con una arquitectura de siete microservicios en Java y Spring Boot. Integra una tienda web, procesamiento distribuido de pedidos, control de inventario, descuentos, notificaciones y un panel administrativo.

El sistema utiliza **Apache Kafka**, **RabbitMQ**, **PostgreSQL**, **Docker** y **WebSocket** para gestionar las compras y comunicar los resultados de su procesamiento.

## Funcionalidades principales

- Registro e inicio de sesión de clientes.
- Catálogo de productos con búsqueda, filtros y disponibilidad.
- Carrito de compras y gestión de cantidades.
- Descuento automático del 10 % cuando el importe de una línea de producto alcanza $100.000 COP.
- Registro y procesamiento distribuido de pedidos.
- Reserva y actualización de inventario en PostgreSQL.
- Historial y seguimiento de compras.
- Notificaciones persistentes y actualizaciones mediante WebSocket.
- Panel administrativo protegido para consultar resultados operativos.

El procesamiento de pagos y el seguimiento logístico utilizan implementaciones locales de demostración; no realizan cobros bancarios ni entregas físicas.

## Arquitectura de microservicios

| Microservicio | Responsabilidad |
|---|---|
| **Order API** | Autenticación de clientes, catálogo, cotizaciones, registro de compras y publicación de órdenes. |
| **Order Processor** | Consumo de órdenes desde Kafka y distribución de tareas mediante RabbitMQ. |
| **Billing Worker** | Procesamiento de pagos locales y aplicación de descuentos. |
| **Inventory Worker** | Verificación, reserva y actualización de existencias en PostgreSQL. |
| **Notification Worker** | Procesamiento de eventos de notificación asociados a las compras. |
| **Analytics Service** | Procesamiento y agregación de información de las órdenes. |
| **Dashboard Aggregator** | Visualización protegida de métricas, resultados y alertas operativas. |

### Flujo de procesamiento

1. El cliente realiza una compra desde la tienda web.
2. Order API valida la solicitud, registra la compra y publica una orden técnica por artículo en Kafka.
3. Order Processor consume las órdenes y distribuye las tareas mediante RabbitMQ.
4. Billing Worker, Inventory Worker y Notification Worker procesan sus responsabilidades.
5. Los resultados se publican a través de Kafka y se registran para su consulta.
6. La tienda actualiza el historial de pedidos, el inventario y las notificaciones correspondientes.

**Mensajería utilizada:**

- Kafka: `new_orders`, `billing_events`, `inventory_events`, `notification_events`.
- RabbitMQ: exchange `order_tasks`, con las colas `billing_queue`, `inventory_queue` y `notification_queue`.

## Tecnologías

| Componente | Tecnología |
|---|---|
| Backend | Java, Spring Boot |
| Frontend | HTML, CSS, JavaScript |
| Mensajería | Apache Kafka, RabbitMQ |
| Base de datos | PostgreSQL |
| Persistencia y migraciones | Spring Data JPA, Flyway |
| Comunicación en tiempo real | WebSocket |
| Contenedores | Docker, Docker Compose |
| Compilación y pruebas | Maven, JUnit |

## Requisitos

- JDK 17 o superior, compatible con la configuración de cada módulo.
- Docker Desktop con Docker Compose v2.
- Git.
- PowerShell para ejecutar los comandos de Windows.
- Puertos locales disponibles para los servicios.

El repositorio incluye Maven Wrapper, por lo que no es necesario instalar Maven por separado.

## Ejecución local

### 1. Infraestructura y microservicios

El proyecto incluye los siguientes archivos de Docker Compose:

- `compose.infra.yml`
- `compose.services.yml`
- `compose.integration.yml`
- `compose.storefront.yml`

Para validar su configuración desde PowerShell:

```powershell
$archivos = @(
    '-f', 'compose.infra.yml',
    '-f', 'compose.services.yml',
    '-f', 'compose.integration.yml',
    '-f', 'compose.storefront.yml'
)

docker compose @archivos config --quiet
```

La configuración local de la tienda utiliza Kafka, RabbitMQ, PostgreSQL y los microservicios auxiliares en Docker. Order API se ejecuta por separado desde Windows para servir el frontend mediante HTTPS.

La preparación detallada de la infraestructura, las credenciales locales y el inicio de los servicios se encuentran en la [guía del frontend](frontend/README.md).

### 2. Bases de datos

El entorno local utiliza dos bases independientes:

| Base de datos | Puerto | Función |
|---|---|---|
| `ecommerce_dev` | `55432` | Cuentas, compras, resultados y notificaciones. |
| `ecommerce_inventory` | `5433` | Existencias y reservas de inventario. |

La consulta de inventario desde Order API utiliza un usuario con permisos de solo lectura. Las reservas y actualizaciones corresponden a Inventory Worker.

Los scripts de preparación local se encuentran en `scripts/`.

### 3. Order API y tienda web

Antes de iniciar Order API deben configurarse las variables de entorno de PostgreSQL, inventario, Kafka y HTTPS, siguiendo `frontend/README.md`.

La publicación de órdenes debe estar habilitada:

```powershell
$env:STOREFRONT_DISPATCH_ENABLED = 'true'
$env:KAFKA_BOOTSTRAP_SERVERS = 'localhost:9094'
```

Para compilar Order API:

```powershell
.\mvnw.cmd -f order-api/pom.xml package
```

Una vez configuradas las conexiones y el certificado HTTPS local, iniciar:

```powershell
java -jar order-api/target/order-api-0.0.1-SNAPSHOT.jar --spring.profiles.active=storefront
```

**Direcciones locales:**

- Tienda web: `https://localhost:8443/`
- Dashboard administrativo: `http://127.0.0.1:8083/`

El Dashboard requiere autenticación administrativa.

## Pruebas automatizadas

Para ejecutar las pruebas de los siete microservicios desde PowerShell:

```powershell
$modules = @(
    'order-api',
    'order-processor',
    'billing-worker',
    'inventory-worker',
    'notification-worker',
    'analytics-service',
    'dashboard-aggregator'
)

foreach ($module in $modules) {
    .\mvnw.cmd -f "$module/pom.xml" test

    if ($LASTEXITCODE -ne 0) {
        throw "Fallaron las pruebas de $module"
    }
}
```

Las pruebas de integración que requieren bases de datos, brokers o servicios adicionales deben ejecutarse en un entorno preparado para ese propósito.

Para las pruebas de integración del perfil storefront, consultar la sección correspondiente de [frontend/README.md](frontend/README.md).

## Seguridad y configuración

Las contraseñas, certificados privados y credenciales de servicios deben mantenerse fuera del repositorio y configurarse mediante variables de entorno.

El sistema contempla autenticación de clientes, autorización por usuario, protección del Dashboard y acceso de solo lectura al inventario desde la tienda.

No deben publicarse archivos con credenciales reales ni utilizarse datos de producción durante las pruebas locales.

## Estructura del proyecto

```text
E-commerce_Hibrido/
├── order-api/
├── order-processor/
├── billing-worker/
├── inventory-worker/
├── notification-worker/
├── analytics-service/
├── dashboard-aggregator/
├── frontend/
│   └── assets/
├── scripts/
├── compose.infra.yml
├── compose.services.yml
├── compose.integration.yml
├── compose.storefront.yml
└── README.md
```

## Estado del proyecto

El proyecto integra una tienda web con procesamiento distribuido de pedidos, persistencia de compras, reserva de inventario y notificaciones. Incluye pruebas automatizadas y configuraciones para su ejecución en un entorno local con Docker.

La configuración, las pruebas y las instrucciones específicas de la tienda se documentan en [frontend/README.md](frontend/README.md).