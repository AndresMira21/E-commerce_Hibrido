# Despliegue en AWS con Ubuntu 24.04

Esta guía prepara una demostración del proyecto en una sola EC2. Todavía no hemos
desplegado en AWS. Los comandos se ejecutarán allí cuando se autorice, no en el
computador donde funciona la integración local.

Usamos los siete microservicios, Kafka, RabbitMQ y PostgreSQL. Nginx se instala en
el servidor, fuera de Docker. Los tres Compose originales se conservan y
`compose.ec2.yml` es el override oficial y se añade al final. Conserva límites y
volúmenes, incorpora healthchecks de Kafka/RabbitMQ, dependencias saludables,
reinicio `unless-stopped` y rotación de logs. Sustituye al override AWS anterior.

## 1. Preparar la instancia

Antes de crearla, confirma con el docente la cuenta, región, permisos y presupuesto.
No asumas que será gratis: EC2, discos, snapshots e IPs pueden generar gastos.
Las alertas de presupuesto no detienen automáticamente el consumo.

- Usa una AMI oficial de Canonical Ubuntu Server 24.04 LTS, EBS cifrado e IMDSv2 obligatorio.
- Los diez contenedores tienen límites que suman unos 5.2 GiB. Una microinstancia no
  basta. Como referencia para presupuestar: 4 vCPU, 16 GiB RAM y 40–60 GiB de disco;
  hay que medir el consumo, especialmente durante los builds Maven.
- Usa IAM con permisos mínimos y MFA. La aplicación no necesita claves AWS ni un
  rol de administrador. No añadas servicios de pago sin autorización.
- Guarda la clave SSH fuera de Git, con permisos restringidos. Comprueba la huella
  del servidor antes de aceptar la conexión.
- Security Group: puerto 22 solo desde la IP administrativa. Al principio deja
  80/443 cerrados y usa un túnel SSH. Si se publica la demo, limita también esos
  puertos a las IP autorizadas y revisa las reglas IPv6.
- No abras los puertos de las aplicaciones, brokers, PostgreSQL ni la API de Docker.

Una instancia única no ofrece alta disponibilidad. Antes de cambiar SSH o el
firewall, mantén dos sesiones comprobadas y una forma de recuperar el acceso.
No borres las claves de `ubuntu` ni desactives ese usuario sin probar una alternativa.

```bash
ssh -i /ruta/privada/clave.pem ubuntu@IP_PUBLICA_EC2
```

## 2. Seguridad básica del servidor

Estos cambios son manuales y deben hacerse uno a uno, con copia de la configuración
anterior y una forma de revertirlos.

| Tema | Qué revisar |
|---|---|
| SSH | Acceso por clave, sin contraseñas ni root. El ejemplo `sshd-hardening.conf.example` no se instala solo. Revisar también los snippets de cloud-init. |
| Actualizaciones | Aplicar parches en una ventana prevista y revisar `unattended-upgrades`; no programar reinicios sin supervisión. |
| Firewall | Revisar UFW/nftables y Security Groups. Los puertos publicados por Docker pueden eludir UFW. No vaciar reglas ni romper FORWARD/NAT. |
| Fail2ban | Configurar SSH con backend systemd, límites moderados e IP administrativa en `ignoreip`. Probar su configuración antes de recargar. |
| AppArmor y kernel | Mantener AppArmor activo. Revisar parámetros antes de cambiarlos; no desactivar `ip_forward`, que Docker necesita. |
| Permisos | Usar usuarios con permisos mínimos. El grupo Docker equivale a root; no abrir permisos del socket ni usar `chmod 777`. |

Comprobaciones de solo lectura:

```bash
id
sudo sshd -t
sudo sshd -T | grep -E 'passwordauthentication|pubkeyauthentication|permitrootlogin|kbdinteractiveauthentication'
sudo ufw status verbose
sudo nft list ruleset
sysctl net.ipv4.ip_forward
sudo aa-status
```

Si modificas SSH, revisa la configuración efectiva para tu usuario/IP con
`sshd -T -C`, ejecuta `sshd -t` y solo entonces recarga el servicio, manteniendo la
sesión anterior abierta. Prueba otra conexión. Si falla, restaura el archivo cambiado,
valida y recarga desde la sesión que sigue abierta.

No apliques recetas automáticas de PAM/MFA, sudoers, GRUB, montajes o sysctl. No uses
`nft flush ruleset`, `iptables -F`, `down -v` ni `prune`. Esta guía no certifica
cumplimiento CIS.

## 3. Instalar Docker y preparar el repositorio

En EC2, revisa e instala las actualizaciones de Ubuntu:

```bash
sudo apt update
apt list --upgradable
sudo apt upgrade
sudo apt install ca-certificates curl git python3
```

Instala Docker Engine y Compose Plugin siguiendo la
[guía oficial para Ubuntu](https://docs.docker.com/engine/install/ubuntu/).
Si Docker ya existe, revísalo antes de reemplazar paquetes. No uses `curl | sh`.
Necesitamos **Compose 2.24.4 o posterior** por las etiquetas `!override`.

```bash
sudo docker version
sudo docker compose version
git clone --branch develop --single-branch https://github.com/AndresMira21/E-commerce_Hibrido.git
cd E-commerce_Hibrido
git log -1 --oneline
test -f compose.ec2.yml
```

Clona `develop` cuando contenga estos archivos revisados; ahora siguen siendo cambios
locales. Si el repositorio es privado, usa acceso de solo lectura y no pongas tokens
en la URL. Mantén el mismo nombre de proyecto Compose para reutilizar los volúmenes:
el ejemplo `ecommerce-aws` es para una EC2 nueva, no para renombrar una instalación existente.

## 4. Variables y validación de Compose

Crea el archivo real fuera del repositorio, sin sobrescribir uno que ya exista:

```bash
sudo install -d -m 700 /etc/ecommerce
sudo test -e /etc/ecommerce/.env.aws || sudo install -m 600 deploy/aws/.env.aws.example /etc/ecommerce/.env.aws
sudoedit /etc/ecommerce/.env.aws
sudo chmod 600 /etc/ecommerce/.env.aws
```

Configura contraseñas fuertes y distintas para PostgreSQL y RabbitMQ. No uses
`guest/guest`. Las variables inicializan volúmenes nuevos; cambiarlas no rota las
credenciales de una base o broker ya inicializado. No borres datos para cambiarlas.

No muestres el archivo, pases contraseñas por argumentos ni actives `set -x`.
Los usuarios root/Docker pueden ver el entorno de los contenedores. Los roles de
bootstrap tienen permisos amplios: una versión de producción necesitaría separar
usuarios de migración, aplicación y administración.

Desde la raíz del repositorio:

```bash
dc() { sudo docker compose --env-file /etc/ecommerce/.env.aws -f compose.infra.yml -f compose.services.yml -f compose.integration.yml -f compose.ec2.yml "$@"; }
python3 -B -m unittest discover -s deploy/aws/tests -v
sudo bash deploy/aws/validate-compose.sh /etc/ecommerce/.env.aws
```

El script ejecuta `config --quiet`, lista los servicios y pasa el JSON efectivo al
comprobador sin imprimir secretos. Deben aparecer diez servicios y estos únicos
puertos publicados por Docker:

| Servicio | Dirección en EC2 |
|---|---|
| Analytics | 127.0.0.1:8082 |
| Dashboard | 127.0.0.1:8083 |
| Order API | 127.0.0.1:8084 |
| RabbitMQ Management | 127.0.0.1:15672 |
| Resto | Sin puertos publicados |

No reemplaces `!override` por una lista vacía normal: Compose podría conservar
publicaciones anteriores. Las redes, volúmenes y límites se heredan sin cambios.

## 5. Construir, preparar las DLQ y arrancar

Construye secuencialmente para reducir el uso de memoria. **Si falla un build, no
continúes con el arranque.**

```bash
for service in order-api order-processor billing-worker inventory-worker notification-worker analytics-service dashboard-aggregator; do
  dc build "$service" || break
done
# Continuar solo si todos terminaron correctamente.
dc up -d kafka rabbitmq postgres
dc ps
dc logs --tail=60 kafka rabbitmq postgres
```

Espera a que PostgreSQL y los brokers estén saludables. En una EC2 nueva, prepara
las colas antes de iniciar los consumidores. Management queda solo en loopback del
host EC2; no abras 15672 en el Security Group ni añadas una ruta pública Nginx.
Desde tu computador, abre el túnel y visita `http://127.0.0.1:15672`:

```bash
ssh -i /ruta/privada/clave.pem -N -L 15672:127.0.0.1:15672 ubuntu@IP_PUBLICA_EC2
```

Si faltan, declara en Management el exchange `order_tasks` tipo Fanout durable,
las colas durables `billing_queue`, `inventory_queue` y `notification_queue`,
y sus bindings con routing key vacía. Sin auto-delete ni argumentos DLX.
Si ya existen, inspecciónalos: no los recrees ni modifiques sus argumentos.

Aplica las políticas desde EC2 con el usuario real configurado:

```bash
python3 deploy/aws/configure-dlq.py --url http://127.0.0.1:15672 --user ecommerce_app
# Revisar primero; --apply escribe destinos y políticas.
python3 deploy/aws/configure-dlq.py --url http://127.0.0.1:15672 --user ecommerce_app --apply
python3 deploy/aws/configure-dlq.py --url http://127.0.0.1:15672 --user ecommerce_app
dc exec -T rabbitmq rabbitmqctl list_policies -p /
dc exec -T rabbitmq rabbitmqctl list_queues -p / name durable policy messages_ready messages_unacknowledged
dc exec -T rabbitmq rabbitmqctl list_bindings -p / source_name destination_name routing_key
```

El script pide la contraseña sin mostrarla, detecta conflictos y no consume ni borra
mensajes. Esperamos tres resultados `verificada=True`. Si hay un conflicto o falla
la aplicación, revisa antes de continuar. No pulses **Get messages** ni **Purge**.

Iniciar workers consume el backlog. Hazlo solo después de revisar las colas y las DLQ:

```bash
dc up -d order-processor billing-worker inventory-worker notification-worker analytics-service dashboard-aggregator order-api
dc ps
dc logs --tail=80 order-api order-processor billing-worker inventory-worker notification-worker
```

Revisa Flyway en los esquemas `orders` y `public`. Los healthchecks permiten ordenar
el arranque, pero no garantizan que todas las colas y políticas estén preparadas.

## 6. Configurar Nginx y el acceso

En EC2 instala Nginx y copia los ejemplos. Antes de reemplazar archivos existentes,
respáldalos y comprueba que no pertenecen a otra aplicación.

```bash
sudo apt install nginx
sudo install -m 644 deploy/aws/nginx/00-ecommerce-http.conf /etc/nginx/conf.d/00-ecommerce-http.conf
sudo install -m 644 deploy/aws/nginx/access-control.conf.example /etc/nginx/snippets/ecommerce-access.conf
sudo install -m 644 deploy/aws/nginx/security-headers.conf /etc/nginx/snippets/ecommerce-security.conf
sudo install -m 644 deploy/aws/nginx/proxy-common.conf /etc/nginx/snippets/ecommerce-proxy.conf
sudo install -m 644 deploy/aws/nginx/routes.conf /etc/nginx/snippets/ecommerce-routes.conf
sudo install -m 644 deploy/aws/nginx/ecommerce-http.conf /etc/nginx/sites-available/ecommerce
```

Revisa `sites-enabled`: no debe haber dos sitios `default_server`. Deshabilita el
enlace de la página predeterminada solo si no se usa, conservando su archivo original.

```bash
sudo ln -s /etc/nginx/sites-available/ecommerce /etc/nginx/sites-enabled/ecommerce
sudo nginx -t
# Solo si la validación anterior pasó:
sudo systemctl reload nginx
```

El ejemplo permite solo loopback. Para empezar sin dominio, deja 80/443 cerrados en
el Security Group y accede por un túnel:

```bash
ssh -i /ruta/privada/clave.pem -N -L 8080:127.0.0.1:80 ubuntu@IP_PUBLICA_EC2
# Navegador: http://127.0.0.1:8080
```

Si se publica HTTP, añade la IP administrativa antes de `deny all` en Nginx y
restringe también el Security Group. Usa solo datos ficticios: HTTP público no cifra
el tráfico. No uses Basic Auth sin TLS. El proxy limita ambos POST a 5 solicitudes
por minuto/IP, con burst 3.

Nginx envía `/`, `/orders/status` y `/ws` al Dashboard; `/order` y `/api/orders`
a la API. Analytics no se publica por Nginx.

La plantilla HTTPS permanece inactiva hasta tener dominio, DNS y certificado válidos.
Reemplaza las rutas y el dominio de ejemplo, valida con `nginx -t` y prueba TLS
1.2/1.3 y renovación. No asumas que una IP basta para obtener un certificado válido.
Si ACME necesita una excepción, limítala a su ruta; no abras todo el sitio.
HSTS se habilita solo después de comprobar HTTPS.

El Dashboard usa jsDelivr y scripts inline. Una futura CSP debe probarse primero en
modo Report-Only para no romper SockJS/STOMP.

## 7. Una sola prueba E2E manual

Abre el Dashboard y la suscripción WebSocket antes del envío. Comprueba stock de
`Libro`; si falta, prepara los datos por separado, sin reinicializar inventario.

```bash
dc exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT product_name,available_quantity,version FROM inventory_items WHERE product_name = '\''Libro'\'';"'
curl -fsS http://127.0.0.1:8082/analytics
RESPONSE=$(curl -sS --max-time 25 -w '\n%{http_code}' -H 'Content-Type: application/json' \
  -d '{"cliente":"Alejandra","correo":"alejandra@example.com","item":"Libro","cantidad":1,"monto":15000}' \
  http://127.0.0.1/order)
HTTP_CODE=${RESPONSE##*$'\n'}
BODY=${RESPONSE%$'\n'*}
printf 'HTTP %s\n%s\n' "$HTTP_CODE" "$BODY"
# Si no es 202, detenerse y revisar la auditoría. No repetir automáticamente.
ORDER_ID=$(printf '%s' "$BODY" | python3 -c 'import json,sys,uuid; d=json.load(sys.stdin); assert d["status"]=="ACCEPTED"; print(uuid.UUID(d["order_id"]))')
dc exec -T postgres sh -c "psql -U \"\$POSTGRES_USER\" -d \"\$POSTGRES_DB\" -c \"SELECT order_id,estado FROM orders.ordenes WHERE order_id='$ORDER_ID'::uuid; SELECT estado,fecha FROM orders.orden_cambios WHERE order_id='$ORDER_ID'::uuid ORDER BY secuencia; SELECT order_id,status,quantity FROM stock_reservations WHERE order_id='$ORDER_ID'::uuid;\""
dc logs --since=10m order-api order-processor billing-worker inventory-worker notification-worker analytics-service dashboard-aggregator | grep -F "$ORDER_ID"
curl -fsS http://127.0.0.1/orders/status | python3 -c 'import json,sys; print(json.dumps([r for r in json.load(sys.stdin) if r["order_id"]==sys.argv[1]],indent=2))' "$ORDER_ID"
curl -fsS http://127.0.0.1:8082/analytics
```

Esperamos el mismo UUID en SQL, logs y Dashboard, con `PAYMENT_SUCCESS`,
`STOCK_RESERVED` y `EMAIL_SENT`. Este último es una simulación, no entrega real
de correo. Analytics mide pedidos recibidos, no ingresos cobrados. En el navegador,
revisa Network → WS/SockJS: `CONNECTED`, suscripción a `/topic/orders` y `MESSAGE`
con ese UUID. No uses consumidores de históricos ni leas mensajes previos para esta prueba.

## 8. Monitoreo, respaldo y evidencias

```bash
sudo bash deploy/aws/monitor-readonly.sh /etc/ecommerce/.env.aws
sudo ss -tulpn
sudo journalctl -u nginx --since '1 hour ago' --no-pager
sudo fail2ban-client -t
sudo fail2ban-client status sshd
sudo systemctl status auditd --no-pager
sudo ausearch -m USER_LOGIN -ts today
sudo aureport --auth --summary
```

Fail2ban, auditd y Lynis deben instalarse y configurarse manualmente si están
autorizados. `sudo lynis audit system --quick` escribe un informe y consume recursos;
no lo ejecutes como si fuera una consulta sin efectos. No apliques sus recomendaciones
a ciegas. CloudWatch es opcional y requiere revisar permisos y costos.

Guarda un respaldo consistente y prueba su restauración en otro entorno.
Los snapshots EBS no sustituyen `pg_dump`; exportar definiciones RabbitMQ no guarda
sus mensajes. No copies volúmenes activos como si fueran un respaldo garantizado.

```bash
set -o pipefail
sudo install -d -m 700 /var/backups/ecommerce
BACKUP=/var/backups/ecommerce/orders-inventory-$(date -u +%Y%m%dT%H%M%SZ).dump
sudo install -m 600 /dev/null "$BACKUP"
dc exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' | sudo tee "$BACKUP" >/dev/null
sudo stat "$BACKUP"
```

Para la exposición, reúne evidencias reales de:

- EC2, costos revisados, EBS cifrado e IMDSv2.
- Security Group, SSH por clave y permisos.
- Firewall compatible con Docker, AppArmor, actualizaciones y auditoría.
- Nginx restringido, HTTPS cuando aplique y WebSocket.
- Diez servicios, puertos loopback, DLQ y consumo de CPU/RAM/disco.
- Una orden con su UUID en la auditoría, inventario y Dashboard.
- Respaldo y recuperación comprobados.

No captures contraseñas, claves, tokens ni datos personales. Guarda evidencias y
backups fuera de Git. Al terminar, detén la instancia desde EC2 en una ventana sin
pedidos en curso, sin terminarla ni borrar volúmenes. Discos, snapshots e IPs pueden
seguir generando costos.

## Qué se ha verificado y qué falta

Se revisaron los archivos, las exclusiones de secretos, la sintaxis Bash y
`git diff --check`. No se ejecutaron comandos remotos ni se creó infraestructura.

El equipo confirmó `config --quiet` y los cuatro puertos loopback de `compose.ec2.yml`.
Eso no sustituye la comprobación del validador actualizado. En esta terminal no
estaban disponibles Docker, Nginx ni Python: quedan pendientes la ejecución del
validador, las diez pruebas Python, `nginx -t` y las verificaciones reales en EC2
de arranque, políticas DLQ, TLS y E2E.
Las pruebas con fixtures no sustituyen esas verificaciones. No se promete entrega
exactamente una vez entre PostgreSQL, Kafka y RabbitMQ.

Referencias: [Docker en Ubuntu](https://docs.docker.com/engine/install/ubuntu/),
[fusión de Compose](https://docs.docker.com/reference/compose-file/merge/),
[firewall y Docker](https://docs.docker.com/engine/network/packet-filtering-firewalls/),
[WebSocket en Nginx](https://nginx.org/en/docs/http/websocket.html) y
[seguridad de Ubuntu](https://ubuntu.com/server/docs/explanation/security/security_suggestions/).
