"""Reproducible incremental Java 8 bridge build when Forge's repository is unavailable.

Uses an existing dev classpath and runtime JAR, preserving every unrelated entry.
Does not deploy the output. A manifest records source/base/output hashes.
"""
import argparse
import hashlib
import json
import pathlib
import subprocess
import tempfile
import zipfile

root = pathlib.Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--base', type=pathlib.Path, required=True)
parser.add_argument('--classpath', type=pathlib.Path, required=True)
parser.add_argument('--java', type=pathlib.Path, required=True, help='JDK 17 bin directory')
parser.add_argument('--dragoncore', type=pathlib.Path, required=True)
parser.add_argument('--worldview-only', action='store_true', help='Build movement/terrain stream against an existing bridge development classpath')
args = parser.parse_args()
out = pathlib.Path(tempfile.mkdtemp(prefix='dragoncore-build-', dir=root / 'build'))
classes = out / 'classes'
classes.mkdir()
tests = out / 'tests'
tests.mkdir()
classpath = args.classpath.read_text(encoding='utf-8-sig').strip()
sources = [root / 'src/main/java/com/mythos/mythosScriptMod' / name for name in [
    'mcp/DragonCanvas.java', 'mcp/McpDragonCore.java', 'mcp/McpGameService.java', 'mcp/McpWorldView.java', 'mcp/WorldInputLease.java',
    'system/HeadlessInputBridge.java',
    'system/DragonResourceEncoding.java',
    'system/DragonDisconnectTasks.java', 'mcp/ClientRequestQueue.java',
    'shadowbaritone/launch/mixins/MixinDragonDisconnect.java',
    'shadowbaritone/launch/mixins/MixinDragonResourceEncoding.java',
    'shadowbaritone/launch/mixins/MixinHeadlessWorldRender.java',
    'shadowbaritone/launch/mixins/MixinDragonScreen.java',
    'shadowbaritone/launch/mixins/MixinDragonComponent.java']]
if args.worldview_only:
    sources = [p for p in sources if p.name in ('McpGameService.java', 'McpWorldView.java', 'WorldInputLease.java')]


def run(exe, params, label):
    # javac argument files keep the large classpath below Windows' command limit.
    if exe == 'javac':
        argfile = out / (label + '.args')
        argfile.write_text('\n'.join('"' + str(p).replace('\\', '/') + '"' for p in params), encoding='utf-8')
        command = [str(args.java / 'javac.exe'), '-J-Dfile.encoding=UTF-8', '@' + str(argfile)]
    else:
        command = [str(args.java / 'java.exe'), '-Dfile.encoding=UTF-8', *map(str, params)]
    result = subprocess.run(command, cwd=root, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    (out / (label + '.log')).write_bytes(result.stdout)
    print(result.stdout.decode('utf-8', errors='replace'), flush=True)
    if result.returncode:
        raise SystemExit('Failed: ' + label + ' (see ' + str(out / (label + '.log')) + ')')


run('javac', ['--release', '8', '-encoding', 'UTF-8', '-proc:none', '-cp', classpath, '-d', classes, *sources], 'compile')
testcp = ';'.join([str(classes), classpath, str(root / 'build/inventory-update/junit.jar'), str(root / 'build/inventory-update/hamcrest.jar')])
test_names = ['WorldInputLeaseTest'] if args.worldview_only else ['DragonCanvasTest', 'DragonCoreContractTest', 'ClientRequestQueueTest', 'WorldInputLeaseTest']
test_sources = [root / 'src/test/java/com/mythos/mythosScriptMod/mcp' / (name + '.java') for name in test_names]
run('javac', ['--release', '8', '-encoding', 'UTF-8', '-proc:none', '-cp', testcp, '-d', tests, *test_sources], 'compile-tests')
run('java', ['-Ddragoncore.jar=' + str(args.dragoncore), '-Ddragoncanvas.evidence=' + str(out), '-cp', str(tests) + ';' + testcp,
             'org.junit.runner.JUnitCore', *['com.mythos.mythosScriptMod.mcp.' + name for name in test_names]], 'tests')
patch = out / 'patch.jar'
with zipfile.ZipFile(patch, 'w', zipfile.ZIP_DEFLATED) as z:
    for p in classes.rglob('*.class'):
        z.write(p, p.relative_to(classes).as_posix())
mapped = out / 'patch-srg.jar'
run('java', ['-cp', str(classes) + ';' + classpath, 'net.md_5.specialsource.SpecialSource',
             '--in-jar', patch, '--out-jar', mapped, '--srg-in', root / 'build/createMcpToSrg/output.tsrg', '--live'], 'remap')
with zipfile.ZipFile(mapped) as z:
    changes = {n: z.read(n) for n in z.namelist() if n.endswith('.class')}
with zipfile.ZipFile(args.base) as base:
    config = json.loads(base.read('mixins.shadowbaritone.json'))
    for name in ['MixinDragonScreen', 'MixinDragonComponent', 'MixinHeadlessWorldRender', 'MixinDragonResourceEncoding', 'MixinDragonDisconnect']:
        if name not in config['client']:
            config['client'].append(name)
    changes['mixins.shadowbaritone.json'] = json.dumps(config, indent=2).encode('utf-8')
    output = out / args.base.name
    with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as z:
        for info in base.infolist():
            if info.filename not in changes:
                z.writestr(info, base.read(info.filename))
        for name, data in changes.items():
            z.writestr(name, data)
with zipfile.ZipFile(args.base) as base, zipfile.ZipFile(output) as built:
    assert built.testzip() is None
    assert len(built.namelist()) == len(set(built.namelist()))
    for name in base.namelist():
        if name not in changes:
            assert base.read(name) == built.read(name), name
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
manifest = {'base': str(args.base.resolve()), 'base_sha256': sha(args.base), 'output': str(output),
            'sha256': sha(output), 'dragoncore_sha256': sha(args.dragoncore), 'changed_entries': sorted(changes),
            'sources': {str(p.relative_to(root)): sha(p) for p in sources + test_sources}}
(out / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps({'output': str(output), 'manifest': str(out / 'manifest.json')}, ensure_ascii=False), flush=True)
