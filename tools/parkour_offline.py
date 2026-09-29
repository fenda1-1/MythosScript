"""Fast isolated planning/physics regression runner. --prepare refreshes external build dependencies."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / 'build' / 'parkour-offline'
PACKAGE = 'com.mythos.mythosScriptMod.shadowbaritone.pathing.movement.parkour'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--prepare', action='store_true')
    parser.add_argument('--suite', type=Path, help='Run every case in a captured suite.json')
    parser.add_argument('--case', help='Run one named case from --suite')
    parser.add_argument('tests', nargs='*', default=['Level70ParkourTest'])
    args = parser.parse_args()
    if args.case and not args.suite:
        parser.error('--case requires --suite')
    began = time.monotonic()
    if args.prepare or not (BUILD / 'environment.json').exists():
        subprocess.run([str(ROOT / 'gradlew.bat'), 'testClasses', 'parkourTestEnvironment',
                        '--offline', '--console=plain', '--max-workers=2',
                        '-I', 'tools/parkour_offline.gradle'], cwd=ROOT, check=True)
    env = json.loads((BUILD / 'environment.json').read_text(encoding='utf-8'))
    java_home = Path(env['javaHome'])
    if java_home.name == 'jre':
        java_home = java_home.parent
    output = BUILD / 'classes'
    output.mkdir(exist_ok=True)
    # Test resources come directly from source, so recaptures never reuse an old fixture.
    cp = os.pathsep.join([str(output), str(ROOT/'src/test/resources'), env['classpath']])
    main = ROOT/'src/main/java/com/mythos/mythosScriptMod/shadowbaritone'
    # Compile real planning and movement code as well as physics: never silently
    # test yesterday's candidate filters after editing today's Java sources.
    sources = sorted((main/'pathing/movement').rglob('*.java'))
    sources += sorted((main/'pathing/calc').rglob('*.java'))
    sources += [main/'pathing/path/PathExecutor.java']
    sources += [main/'utils/BlockStateInterface.java']
    sources += [main/'api/utils/IPlayerContext.java']
    sources += sorted((ROOT/'src/test/java'/Path(PACKAGE.replace('.', '/'))).glob('*.java'))
    compiler_args = ['-proc:none', '-encoding', 'UTF-8', '-source', '8', '-target', '8',
                     '-classpath', cp, '-d', str(output)] + [str(p) for p in sources]
    # Java 8 argument files avoid Windows' command-line length limit.
    argfile = BUILD/'javac.args'
    argfile.write_text('\n'.join('"'+a.replace('\\', '/')+'"' for a in compiler_args), encoding='utf-8')
    stamp=BUILD/'compile-stamp.json'
    fingerprint={str(p):p.stat().st_mtime_ns for p in sources+[BUILD/'environment.json']}
    if not stamp.exists() or json.loads(stamp.read_text(encoding='utf-8')) != fingerprint:
        subprocess.run([str(java_home/'bin/javac.exe'), '-J-Dfile.encoding=UTF-8', '@'+str(argfile)], cwd=ROOT, check=True)
        stamp.write_text(json.dumps(fingerprint),encoding='utf-8')
    properties = []
    if args.suite:
        if not args.suite.is_file():
            parser.error('Suite does not exist: ' + str(args.suite))
        properties = ['-Dparkour.suite=' + str(args.suite.resolve())]
        if args.case:
            properties.append('-Dparkour.case='+args.case)
        args.tests = ['CapturedParkourSuiteTest']
    target = [PACKAGE+'.OfflineParkourTestRunner'] + [t if '.' in t else PACKAGE+'.'+t for t in args.tests]
    result = subprocess.run([str(java_home/'bin/java.exe'), '-Xmx1G', '-Dfile.encoding=UTF-8'] + properties + ['-cp', cp] + target, cwd=ROOT)
    print(f'Offline compile + tests: {time.monotonic()-began:.2f}s', flush=True)
    raise SystemExit(result.returncode)


if __name__ == '__main__':
    main()
