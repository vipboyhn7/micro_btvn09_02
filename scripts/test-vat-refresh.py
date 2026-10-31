#!/usr/bin/env python3
"""End-to-end check against real Git, Config Server and two running clients (JDK 17)."""
import json
import os
from pathlib import Path
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parent.parent
LOG_DIR = ROOT / '.local/logs/vat-refresh'


def git(repo, *args):
    return subprocess.check_output([
        'git', '-C', str(repo), '-c', 'user.name=Config Demo',
        '-c', 'user.email=config-demo@example.invalid', '-c', 'commit.gpgsign=false',
        *args,
    ], text=True).strip()


def request(port, path, body=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(f'http://127.0.0.1:{port}{path}', data=data,
                                 headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(req, timeout=15) as response:
        return json.load(response)


def wait_ready(process, port):
    deadline = time.monotonic() + 45
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError(f'Process exited: {process.returncode}; see {LOG_DIR}')
        try:
            if request(port, '/actuator/health')['status'] == 'UP':
                return
        except (OSError, urllib.error.URLError):
            time.sleep(0.3)
    raise RuntimeError(f'Port {port} did not become ready; see {LOG_DIR}')


def bill(port, rate, total):
    result = request(port, '/api/v1/bill', {'medicineTotal': 100000})
    assert result == {'medicineTotal': 100000, 'vatRate': rate,
                      'vatAmount': total - 100000, 'totalAmount': total}, result


def main():
    LOG_DIR.mkdir(parents=True, exist_ok=True)
    processes, logs, sockets = [], [], []
    with tempfile.TemporaryDirectory(prefix='pharmacy-vat-test-') as temp:
        repo = Path(temp)
        config = repo / 'pharmacy-service.properties'
        template = (ROOT / 'config-repo/pharmacy-service.properties').read_text()
        template = '\n'.join(line for line in template.splitlines()
                             if not line.startswith(('spring.datasource.url=', 'pharmacy.vat-rate=')))
        template += '\nspring.datasource.url=jdbc:h2:mem:vat-test\n'
        config.write_text(template + 'pharmacy.vat-rate=10\n')
        git(repo, 'init', '-b', 'main')
        git(repo, 'add', 'pharmacy-service.properties')
        git(repo, 'commit', '-m', 'Initial VAT 10 percent')
        try:
            # Reserve three different unused ports before starting processes.
            for _ in range(3):
                sock = socket.socket()
                sock.bind(('127.0.0.1', 0))
                sockets.append(sock)
            ports = [sock.getsockname()[1] for sock in sockets]
            for i, (name, port) in enumerate(zip(('config-server', 'client-1', 'client-2'), ports)):
                sockets[i].close()
                env = os.environ.copy()
                env.update(CONFIG_GIT_URI=repo.as_uri(), CONFIG_GIT_LABEL='main',
                           CONFIG_SERVER_PORT=str(ports[0]), PHARMACY_PORT=str(port),
                           CONFIG_SERVER_URL=f'http://127.0.0.1:{ports[0]}')
                script = 'run-config-server.sh' if i == 0 else 'run-pharmacy-service.sh'
                log = (LOG_DIR / f'{name}.log').open('w')
                logs.append(log)
                process = subprocess.Popen(['bash', str(ROOT / 'scripts' / script)],
                                           cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
                processes.append(process)
                wait_ready(process, port)
            clients = ports[1:]
            for port in clients:
                bill(port, 10, 110000)
            config.write_text(template + 'pharmacy.vat-rate=8\n')
            git(repo, 'add', 'pharmacy-service.properties')
            git(repo, 'commit', '-m', 'Change VAT to 8 percent')
            remote = request(ports[0], '/pharmacy-service/default/main')
            assert remote['version'] == git(repo, 'rev-parse', 'HEAD'), remote
            for port in clients:
                bill(port, 10, 110000)
            for i, port in enumerate(clients):
                changed = request(port, '/actuator/refresh', {})
                assert 'pharmacy.vat-rate' in changed, changed
                bill(port, 8, 108000)
                if i == 0:
                    bill(clients[1], 10, 110000)
            assert all(process.poll() is None for process in processes)
            for name in ('client-1', 'client-2'):
                text = (LOG_DIR / f'{name}.log').read_text()
                assert text.count('Started PharmacyApplication') == 1, text
                assert 'using Java 17' in text
                assert 'Database connected: H2' in text
            print('PASS: JDK 17; Git commit updated; VAT 10% -> 8% on both clients after refresh.')
            print('PASS: unrefreshed client keeps old VAT; no application restart.')
        finally:
            for process in reversed(processes):
                if process.poll() is None:
                    process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()
            for log in logs:
                log.close()
            for sock in sockets:
                sock.close()


if __name__ == '__main__':
    main()
