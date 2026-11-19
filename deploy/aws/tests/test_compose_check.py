"""Fixtures sinteticas: no Docker, red, credenciales reales ni fusion YAML."""
import copy
import importlib.util
import pathlib
import unittest

spec = importlib.util.spec_from_file_location('compose_check', pathlib.Path(__file__).parents[1] / 'check-effective-compose.py')
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)


def fixture():
    config = {'services': {}, 'networks': {'ecommerce': {}},
              'volumes': {v: {} for v in ('kafka_data', 'rabbitmq_data', 'postgres_data')}}
    for name in checker.EXPECTED:
        config['services'][name] = {'networks': {'ecommerce': {}}, 'environment': {}, 'ports': [],
                                    'deploy': {'resources': {'limits': {'cpus': '0.5', 'memory': '512m'}}}}
    for name, ports in checker.PORTS.items():
        config['services'][name]['ports'] = [dict(host_ip=ip, published=published, target=target, protocol=protocol)
                                            for ip, published, target, protocol in ports]
    for name in ('rabbitmq', 'order-processor', 'billing-worker', 'inventory-worker', 'notification-worker'):
        user, password = ('RABBITMQ_DEFAULT_USER', 'RABBITMQ_DEFAULT_PASS') if name == 'rabbitmq' else ('RABBITMQ_USERNAME', 'RABBITMQ_PASSWORD')
        config['services'][name]['environment'] = {user: 'fixture-user', password: 'fictional-test-only'}
    config['services']['postgres']['environment'] = {'POSTGRES_PASSWORD': 'fictional-test-only'}
    for name in ('kafka', 'rabbitmq', 'postgres'):
        config['services'][name]['volumes'] = [{'source': name + '_data', 'target': '/fixture'}]
    return config


class ComposeCheckTests(unittest.TestCase):
    def setUp(self):
        self.config = fixture()

    def test_accepts_expected_private_topology(self):
        checker.validate(self.config)

    def test_rejects_public_dashboard_port(self):
        self.config['services']['dashboard-aggregator']['ports'][0]['host_ip'] = '0.0.0.0'
        with self.assertRaises(ValueError):
            checker.validate(self.config)

    def test_rejects_any_published_broker_port(self):
        for name in ('kafka', 'rabbitmq', 'postgres', 'notification-worker'):
            config = copy.deepcopy(self.config)
            config['services'][name]['ports'] = [dict(host_ip='127.0.0.1', published='1234', target=1234)]
            with self.subTest(name=name), self.assertRaises(ValueError):
                checker.validate(config)

    def test_rejects_public_management_bindings(self):
        for host_ip in ('0.0.0.0', '::', '::1', None):
            config = copy.deepcopy(self.config)
            config['services']['rabbitmq']['ports'][0]['host_ip'] = host_ip
            with self.subTest(host_ip=host_ip), self.assertRaises(ValueError):
                checker.validate(config)

    def test_rejects_management_and_additional_amqp_port(self):
        self.config['services']['rabbitmq']['ports'].append(
            dict(host_ip='127.0.0.1', published='5672', target=5672))
        with self.assertRaises(ValueError):
            checker.validate(self.config)

    def test_rejects_guest_credentials(self):
        self.config['services']['rabbitmq']['environment']['RABBITMQ_DEFAULT_USER'] = 'guest'
        with self.assertRaises(ValueError):
            checker.validate(self.config)

    def test_rejects_missing_secret(self):
        self.config['services']['postgres']['environment']['POSTGRES_PASSWORD'] = ''
        with self.assertRaises(ValueError):
            checker.validate(self.config)

    def test_rejects_missing_memory_limit(self):
        self.config['services']['order-api']['deploy']['resources']['limits'].pop('memory')
        with self.assertRaises(ValueError):
            checker.validate(self.config)

    def test_rejects_additional_service(self):
        self.config['services']['unexpected'] = {}
        with self.assertRaises(ValueError):
            checker.validate(self.config)

    def test_rejects_lost_volume(self):
        self.config['services']['postgres']['volumes'] = []
        with self.assertRaises(ValueError):
            checker.validate(self.config)


if __name__ == '__main__':
    unittest.main()
