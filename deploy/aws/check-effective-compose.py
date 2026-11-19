#!/usr/bin/env python3
"""Valida JSON de docker compose config por stdin. No imprime secretos ni conecta servicios."""
import json
import sys

EXPECTED = {"order-api", "order-processor", "billing-worker", "inventory-worker",
            "notification-worker", "analytics-service", "dashboard-aggregator", "kafka", "rabbitmq", "postgres"}
PORTS = {"order-api": {("127.0.0.1", "8084", 8084, "tcp")},
         "dashboard-aggregator": {("127.0.0.1", "8083", 8083, "tcp")},
         "analytics-service": {("127.0.0.1", "8082", 8082, "tcp")},
         "rabbitmq": {("127.0.0.1", "15672", 15672, "tcp")}}


def validate(config):
    services = config.get("services", {})
    if set(services) != EXPECTED:
        raise ValueError("Se requieren exactamente siete microservicios y tres servicios centrales")
    if "ecommerce" not in config.get("networks", {}):
        raise ValueError("Falta la red ecommerce")
    volumes = {"kafka_data", "rabbitmq_data", "postgres_data"}
    if not volumes.issubset(config.get("volumes", {})):
        raise ValueError("Faltan volumenes persistentes")
    for name, service in services.items():
        if service.get("network_mode") == "host" or "ecommerce" not in service.get("networks", {}):
            raise ValueError("Red inesperada en " + name)
        actual = set()
        for port in service.get("ports", []):
            if not isinstance(port, dict):
                raise ValueError("Se requiere JSON normalizado de Compose")
            actual.add((port.get("host_ip"), str(port.get("published")), int(port["target"]), port.get("protocol", "tcp")))
        if actual != PORTS.get(name, set()):
            raise ValueError("Puertos publicados inesperados en " + name)
        limits = service.get("deploy", {}).get("resources", {}).get("limits", {})
        if not (service.get("cpus") or limits.get("cpus")) or not (service.get("mem_limit") or limits.get("memory")):
            raise ValueError("Faltan limites de CPU/RAM en " + name)
    for name in ("rabbitmq", "order-processor", "billing-worker", "inventory-worker", "notification-worker"):
        environment = services[name].get("environment", {})
        user_key, pass_key = (("RABBITMQ_DEFAULT_USER", "RABBITMQ_DEFAULT_PASS") if name == "rabbitmq"
                              else ("RABBITMQ_USERNAME", "RABBITMQ_PASSWORD"))
        if not environment.get(user_key) or str(environment[user_key]).strip().lower() == "guest":
            raise ValueError("Usuario RabbitMQ no permitido en " + name)
        if not environment.get(pass_key) or str(environment[pass_key]).strip().lower() == "guest":
            raise ValueError("Credencial RabbitMQ de desarrollo/vacia en " + name)
    if not services["postgres"].get("environment", {}).get("POSTGRES_PASSWORD"):
        raise ValueError("Falta credencial PostgreSQL")
    for name, source in (("kafka", "kafka_data"), ("rabbitmq", "rabbitmq_data"), ("postgres", "postgres_data")):
        if not any(v.get("source") == source for v in services[name].get("volumes", []) if isinstance(v, dict)):
            raise ValueError("Volumen persistente no conectado en " + name)


def main():
    try:
        validate(json.load(sys.stdin))
    except (ValueError, KeyError, TypeError) as error:
        print("Configuracion AWS rechazada: " + str(error), file=sys.stderr)
        return 1
    print("Configuracion efectiva verificada: 10 servicios, red/volumenes/limites presentes.")
    print("Unicos puertos publicados: 127.0.0.1:8082, 127.0.0.1:8083, 127.0.0.1:8084, 127.0.0.1:15672 (TCP).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
