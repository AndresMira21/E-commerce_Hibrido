#!/usr/bin/env python3
"""Management API: consulta por defecto, --apply solo tras preflight. No consume ni borra."""
import argparse
import base64
import getpass
import ipaddress
import json
import re
import urllib.error
import urllib.parse
import urllib.request


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--url', default='http://127.0.0.1:15672', help='Management en loopback del host EC2; nunca publicar 15672 en Internet')
    parser.add_argument('--user', required=True)
    parser.add_argument('--vhost', default='/')
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    parsed = urllib.parse.urlparse(args.url)
    if parsed.scheme not in ('http', 'https') or parsed.username or parsed.password or parsed.query or parsed.fragment:
        parser.error('URL sin credenciales ni query, HTTP privado o HTTPS')
    if parsed.scheme == 'http':
        try:
            private = parsed.hostname == 'localhost' or ipaddress.ip_address(parsed.hostname).is_private
        except ValueError:
            private = False
        if not private:
            parser.error('HTTP solo hacia IP privada/loopback; usar tunel seguro para otros casos')
    if args.user.lower() == 'guest':
        parser.error('No usar guest en AWS')
    password = getpass.getpass('Password RabbitMQ (no se registra): ')
    if not password or password.lower() == 'guest':
        parser.error('Password vacia/de desarrollo no permitida')
    auth = base64.b64encode((args.user + ':' + password).encode()).decode()
    base = args.url.rstrip('/') + '/api'
    vhost = urllib.parse.quote(args.vhost, safe='')

    # Evitar enviar la credencial por redirecciones o proxies del entorno.
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, req, fp, code, msg, headers, newurl):
            return None
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())

    def request(path, method='GET', body=None):
        data = None if body is None else json.dumps(body).encode()
        req = urllib.request.Request(base + path, data=data, method=method,
                                     headers={'Authorization': 'Basic ' + auth, 'Content-Type': 'application/json'})
        try:
            with opener.open(req, timeout=10) as response:
                raw = response.read()
                return json.loads(raw) if raw else None
        except urllib.error.HTTPError as error:
            if method == 'GET' and error.code == 404:
                return None
            # No imprimir cuerpo del error, cabeceras ni credenciales.
            raise RuntimeError('Management API devolvio HTTP ' + str(error.code)) from None

    policies = request('/policies/' + vhost) or []
    plans = []
    for prefix in ('billing', 'inventory', 'notification'):
        queue, dlx, dead, key = prefix + '_queue', prefix + '_dlx', prefix + '_dead_queue', prefix + '.failed'
        policy_name = prefix + '-dlq'
        definition = {'dead-letter-exchange': dlx, 'dead-letter-routing-key': key}
        desired = {'pattern': '^' + queue + '$', 'apply-to': 'queues', 'priority': 10, 'definition': definition}
        original = request(f'/queues/{vhost}/{queue}')
        if original is None:
            raise RuntimeError('Preparar primero cola origen sin consumir: ' + queue)
        for policy in policies:
            if policy['name'] == policy_name:
                if any(policy.get(k) != v for k, v in desired.items()):
                    raise RuntimeError('Politica con configuracion diferente: ' + policy_name)
            elif policy.get('apply-to') != 'exchanges' and re.search(policy['pattern'], queue):
                raise RuntimeError('Politica ajena coincidente; combinar manualmente: ' + policy['name'])
        for k, v in definition.items():
            if original.get('arguments', {}).get('x-' + k, v) != v:
                raise RuntimeError('Argumentos incompatibles en ' + queue)
            if original.get('effective_policy_definition', {}).get(k, v) != v:
                raise RuntimeError('Politica efectiva/operador incompatible en ' + queue)
        exchange = request(f'/exchanges/{vhost}/{dlx}')
        destination = request(f'/queues/{vhost}/{dead}')
        if exchange and (exchange['type'] != 'direct' or not exchange['durable'] or exchange['auto_delete']
                         or exchange.get('internal') or exchange.get('arguments')):
            raise RuntimeError('Exchange incompatible: ' + dlx)
        if destination and (not destination['durable'] or destination['auto_delete'] or destination.get('arguments')):
            raise RuntimeError('DLQ incompatible: ' + dead)
        bindings = request(f'/bindings/{vhost}/e/{dlx}/q/{dead}') or []
        plans.append((queue, dlx, dead, key, policy_name, desired, any(b['routing_key'] == key for b in bindings)))
    for queue, dlx, dead, key, policy_name, desired, bound in plans:
        if args.apply:
            request(f'/exchanges/{vhost}/{dlx}', 'PUT', dict(type='direct', durable=True, auto_delete=False, internal=False, arguments={}))
            request(f'/queues/{vhost}/{dead}', 'PUT', dict(durable=True, auto_delete=False, arguments={}))
            if not bound:
                request(f'/bindings/{vhost}/e/{dlx}/q/{dead}', 'POST', dict(routing_key=key, arguments={}))
            request(f'/policies/{vhost}/{policy_name}', 'PUT', desired)
        effective = request(f'/queues/{vhost}/{queue}')
        bindings = request(f'/bindings/{vhost}/e/{dlx}/q/{dead}') or []
        valid = effective.get('policy') == policy_name and all(
            effective.get('effective_policy_definition', {}).get(k) == v for k, v in desired['definition'].items()
        ) and any(b['routing_key'] == key for b in bindings)
        print(f'{queue}: politica={effective.get("policy")} destino={dead} verificada={valid}')
        if args.apply and not valid:
            raise RuntimeError('Verificacion efectiva fallo: ' + queue)


if __name__ == '__main__':
    try:
        main()
    except RuntimeError as error:
        raise SystemExit('DLQ: ' + str(error))
    except (urllib.error.URLError, ValueError, KeyError) as error:
        # Solo diagnostico generico: no imprimir respuestas ni URLs con secretos.
        raise SystemExit('DLQ: operacion interrumpida (' + type(error).__name__ + '). Revisar credenciales, conectividad y conflictos; no se borraron recursos.')
