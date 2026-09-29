"""Build optionally, start or restart the parkour client, and confirm world readiness."""
import argparse
import importlib.util
import json
import msvcrt
from pathlib import Path
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
REPORT = ROOT / 'build/parkour-evidence/restart-latest.json'
BEGAN = time.monotonic()


def report(stage, **details):
    data = {'stage': stage, 'elapsedSeconds': round(time.monotonic()-BEGAN, 1),
            'timestamp': time.strftime('%Y-%m-%d %H:%M:%S'), **details}
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    temporary = REPORT.with_suffix('.tmp')
    temporary.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')
    temporary.replace(REPORT)
    print(json.dumps(data, ensure_ascii=True), flush=True)


def run_stage(stage, command, cwd, timeout):
    log = REPORT.parent / ('restart-' + stage + '.log')
    report(stage, log=str(log), timeoutSeconds=timeout)
    # Child/daemon stdout must not keep the calling terminal's pipe open after
    # the controller exits. Keep complete output in a file instead.
    with log.open('wb') as output:
        process = subprocess.Popen(command, cwd=cwd, stdin=subprocess.DEVNULL,
                                   stdout=output, stderr=subprocess.STDOUT)
        deadline = time.monotonic() + timeout
        heartbeat = time.monotonic() + 10
        changed, size = time.monotonic(), 0
        try:
            while process.poll() is None:
                current_size = log.stat().st_size
                if current_size != size:
                    changed, size = time.monotonic(), current_size
                if time.monotonic() >= deadline or (stage == 'build' and time.monotonic()-changed > 180):
                    raise TimeoutError(stage + ' timed out or stopped producing output; inspect ' + str(log))
                if time.monotonic() >= heartbeat:
                    report(stage, childPid=process.pid, log=str(log),
                           remainingSeconds=round(deadline-time.monotonic()),
                           silentSeconds=round(time.monotonic()-changed))
                    heartbeat = time.monotonic() + 10
                time.sleep(.2)
        except BaseException:
            if process.poll() is None:
                if stage == 'build':
                    # Kill only this build's tree, never every Java process.
                    subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'],
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=20)
                else:
                    process.kill()
                process.wait(timeout=10)
            raise
        if process.returncode:
            raise RuntimeError('%s failed (exit %s); inspect %s' % (stage, process.returncode, log))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--start', action='store_true', help='Start a stopped test client without disconnecting')
    parser.add_argument('--build', action='store_true', help='Run offline reobfJar before disconnecting')
    parser.add_argument('--jar', type=Path, default=ROOT / 'build/libs/MythosScript-v1.0.72-mc1.12.2.jar')
    parser.add_argument('--tests-dir', type=Path, default=ROOT.parent / 'MythosTests')
    parser.add_argument('--world', default='§0Just Another §fParkour Map§0')
    parser.add_argument('--player', help='Required if multiple players are connected')
    parser.add_argument('--timeout', type=float, default=120)
    parser.add_argument('--build-timeout', type=float, default=300)
    parser.add_argument('--render-distance', type=int, choices=range(2, 33),
                        help='Set the test client view distance while it is stopped')
    args = parser.parse_args()
    tests = args.tests_dir.resolve()
    jar = args.jar.resolve()
    if args.timeout <= 0 or args.build_timeout <= 0 or not (tests / 'run.py').is_file():
        parser.error('Positive timeout and a valid MythosTests directory are required')
    if args.build:
        run_stage('build', ['pwsh', '-NoProfile', '-Command',
                        '.\\gradlew.bat reobfJar --offline --console=plain --no-daemon --max-workers=2 --info; exit $LASTEXITCODE'],
                       cwd=ROOT, timeout=args.build_timeout)
    if not jar.is_file():
        parser.error('JAR does not exist: ' + str(jar))

    config = tests / 'instances/1.12.2/inject/game/config/我的世界脚本'
    port = json.loads((config / 'mcp_server.json').read_text(encoding='utf-8-sig'))['port']
    spec = importlib.util.spec_from_file_location('bridge', ROOT / 'skills/mythosscript-mcp/scripts/client.py')
    bridge = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(bridge)
    client = bridge.LocalClient(config / 'mcp.token', 'http://127.0.0.1:%s/mcp' % port)

    def call(name, arguments=None):
        response = client.send({'jsonrpc': '2.0', 'id': 1, 'method': 'tools/call',
                               'params': {'name': name, 'arguments': arguments or {}}})
        result = response.get('result', {})
        if response.get('error') or result.get('isError'):
            raise RuntimeError('MCP call failed: ' + name)
        return result.get('structuredContent', result)

    old_pid = None
    player = args.player or 'test_1122_inj'
    if not args.start:
        report('discover')
        listing = call('mythos_clients')
        matches = [entry for entry in listing.get('clients', []) if entry.get('inWorld')
                   and (not args.player or entry.get('player', '').casefold() == args.player.casefold())]
        if len(matches) != 1:
            raise RuntimeError('Expected exactly one matching in-world client; specify --player')
        old_pid = int(matches[0]['pid'])
        player = matches[0]['player']
        client.player = player
        client.pid = old_pid
        call('mythos_discover')
        before = call('mythos_status')
        if not before.get('inWorld'):
            raise RuntimeError('Selected client left the world before restart')
        call('mythos_actions', {'query': 'disconnect'})
        call('mythos_actions', {'type': 'disconnect'})
        sequence = {'name': 'parkour-restart-disconnect', 'steps': [
            {'pos': None, 'actions': [{'type': 'disconnect', 'params': {}}]}]}
        validation = call('mythos_validate', {'sequence': sequence})
        if validation.get('valid') is False or validation.get('errors') or any(
                str(issue.get('severity', '')).upper() == 'ERROR' for issue in validation.get('issues', [])):
            raise RuntimeError('Disconnect sequence validation failed')
        print('Disconnecting player=%s pid=%s' % (player, old_pid), flush=True)
        report('disconnect', oldPid=old_pid, player=player)
        run = call('mythos_run', {'sequence': sequence})
        if not run.get('accepted'):
            raise RuntimeError('Disconnect was not accepted')
        client.player = None
        client.pid = None
        deadline = time.monotonic() + args.timeout
        while True:
            listing = call('mythos_clients')
            # An unresponsive endpoint is NOT confirmation of a saved/closed world.
            still_in_world = any(entry.get('pid') == old_pid and entry.get('inWorld')
                                 for entry in listing.get('clients', []))
            if not still_in_world and player not in listing.get('players', []):
                break
            if time.monotonic() >= deadline:
                raise TimeoutError('World exit not confirmed; process was not stopped')
            time.sleep(.3)
        run_stage('stop', ['pwsh', '-NoProfile', '-Command',
                        '$p = Get-Process -Id %d -ErrorAction SilentlyContinue; '
                        'if ($p) { Stop-Process -InputObject $p -ErrorAction Stop; '
                        'if (-not $p.WaitForExit(20000)) { throw "Process exit timed out" } }; exit 0' % old_pid],
                       cwd=ROOT, timeout=30)
    if args.render_distance is not None:
        options = config.parents[1] / 'options.txt'
        lines = options.read_text(encoding='utf-8').splitlines()
        old = [line for line in lines if line.startswith('renderDistance:')]
        if len(old) != 1:
            raise RuntimeError('Expected one renderDistance entry in ' + str(options))
        updated = ['renderDistance:' + str(args.render_distance) if line == old[0] else line for line in lines]
        options.write_text('\n'.join(updated) + '\n', encoding='utf-8')
        report('view-distance', previous=old[0], renderDistance=args.render_distance)
    print('Launching ' + str(jar), flush=True)
    run_stage('launch', [sys.executable, str(tests / 'run.py'), 'launch', '--version', '1.12.2',
                    '--mode', 'inject', '--world', args.world, '--jar', str(jar),
                    '--timeout', str(max(1,int(args.timeout)))], cwd=tests, timeout=args.timeout)
    if args.render_distance is not None:
        actual = [line for line in options.read_text(encoding='utf-8').splitlines()
                  if line.startswith('renderDistance:')]
        if actual != ['renderDistance:' + str(args.render_distance)]:
            raise RuntimeError('Launcher changed the requested view distance: ' + str(actual))
    report('confirm-world', oldPid=old_pid, player=player)
    deadline = time.monotonic() + args.timeout
    while time.monotonic() < deadline:
        client.player = None
        client.pid = None
        try:
            listing = call('mythos_clients')
            matches = [entry for entry in listing.get('clients', [])
                       if entry.get('inWorld') and entry.get('player') == player
                       and entry.get('pid') != old_pid]
            if len(matches) == 1:
                client.player = player
                client.pid = int(matches[0]['pid'])
                status = call('mythos_status')
                if status.get('inWorld') and status.get('player'):
                    report('ready', ready=True, oldPid=old_pid, pid=client.pid,
                           player=player, position=[status['player'][k] for k in ('x', 'y', 'z')])
                    return 0
        except (RuntimeError, OSError):
            pass  # Poll only; never launch a second process after an ambiguous timeout.
        time.sleep(.5)
    raise TimeoutError('Launcher returned but the selected player has not entered the world')


if __name__ == '__main__':
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    lock = (REPORT.parent / 'restart.lock').open('a+b')
    lock.write(b'\0')
    lock.flush()
    lock.seek(0)
    try:
        msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
    except OSError:
        print('Another restart/build is already running; inspect restart-latest.json', file=sys.stderr)
        raise SystemExit(1)
    try:
        raise SystemExit(main())
    except (RuntimeError, OSError, subprocess.SubprocessError) as error:
        report('failed', error=str(error))
        print('Restart failed: ' + str(error), file=sys.stderr)
        raise SystemExit(1)
    finally:
        lock.seek(0)
        msvcrt.locking(lock.fileno(), msvcrt.LK_UNLCK, 1)
        lock.close()
