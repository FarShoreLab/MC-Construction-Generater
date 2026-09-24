#!/usr/bin/env python3
"""Build with local Java 21 + real Gson. Never downloads jars or changes dependencies."""
from __future__ import annotations
import argparse, hashlib, json, os, shutil, subprocess, sys, time, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'core-planner/build/classes/java/main'
TEST = ROOT / 'core-planner/build/classes/java/offlineTest'

def gson_jar() -> Path:
    override = os.environ.get('GSON_JAR')
    if override:
        p = Path(override).expanduser().resolve()
        if p.is_file(): return p
        raise RuntimeError(f'GSON_JAR does not exist: {p}')
    candidates = []
    for home in [ROOT / '.gradle', Path.home() / '.gradle']:
        candidates += list((home / 'caches/modules-2/files-2.1/com.google.code.gson/gson/2.10.1').glob('*/gson-2.10.1.jar'))
    if candidates: return sorted(candidates)[0]
    raise RuntimeError('Local Gson 2.10.1 not found. Set GSON_JAR to a real local Gson jar. Nothing was downloaded.')

def compile_java(sources: list[Path], target: Path, cp: str, evidence: Path, name: str) -> None:
    target.mkdir(parents=True, exist_ok=True)
    argfile = target.parent / (name + '-sources.txt')
    argfile.write_text('\n'.join('"' + p.as_posix().replace('"', '\\"') + '"' for p in sorted(sources)), encoding='utf-8')
    command = ['javac', '-encoding', 'UTF-8', '--release', '21', '-cp', cp, '-d', str(target), '@'+str(argfile)]
    result = subprocess.run(command, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=90)
    (evidence / (name + '.log')).write_bytes(result.stdout)
    if result.returncode: raise RuntimeError(f'{name} failed; see {evidence / (name + ".log")}')

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--test', action='store_true')
    parser.add_argument('--loopback-tests', action='store_true', help='Also run the Java HTTP loopback bridge fixture (no external endpoint).')
    parser.add_argument('--evidence', type=Path, default=ROOT / 'build/verification')
    args = parser.parse_args(); evidence = args.evidence.resolve(); evidence.mkdir(parents=True, exist_ok=True)
    for tool in ['java', 'javac']:
        if not shutil.which(tool): raise RuntimeError(f'{tool} is not on PATH; Java 21 is required.')
    jar = gson_jar(); cp = os.pathsep.join([str(MAIN), str(jar)])
    with zipfile.ZipFile(jar) as archive:
        props=archive.read('META-INF/maven/com.google.code.gson/gson/pom.properties').decode()
    actual_gson=next((line.split('=',1)[1].strip() for line in props.splitlines() if line.startswith('version=')), 'unknown')
    environment = {'actual_gson':actual_gson,'exact_declared_gson_verified':actual_gson=='2.10.1','python':sys.version, 'java':subprocess.run(['java','-version'],capture_output=True,text=True).stderr,
                   'gson_jar':str(jar), 'gson_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),
                   'declared_gson':'2.10.1', 'fabric_type_check':'NOT_RUN: requires the Fabric/Gradle dependency cache',
                   'minecraft_runtime':'NOT_RUN'}
    (evidence / 'environment.json').write_text(json.dumps(environment, indent=2), encoding='utf-8')
    sources = list((ROOT/'core-planner/src/main/java').rglob('*.java')) + list((ROOT/'llm-bridge/src/main/java').rglob('*.java'))
    compile_java(sources, MAIN, str(jar), evidence, 'compile-main')
    shutil.copytree(ROOT/'core-planner/src/main/resources', MAIN, dirs_exist_ok=True)
    print(f'PASS core + llm-bridge compilation ({len(sources)} files); local Gson: {jar}', flush=True)
    if not args.test: return 0
    tests = [p for module in ['core-planner','llm-bridge'] for p in (ROOT/module/'src/test/java').rglob('*.java') if not p.name.endswith('Test.java')]
    tests += [ROOT/'tools/FuzzAudit.java', ROOT/'tools/SyntaxCheck.java', ROOT/'tools/EvidenceExport.java']
    compile_java(tests, TEST, cp, evidence, 'compile-executable-tests'); cp = os.pathsep.join([cp, str(TEST)])
    commands = [
        ('core-21',['org.mcsettlement.planner.regression.RegressionMain',str(evidence/'regression')]),
        ('adaptive-29',['org.mcsettlement.planner.AdaptiveTerrainMain',str(evidence/'adaptive')]),
        ('fuzz-100',['FuzzAudit',str(evidence/'fuzz-cases.csv')]),
        ('organic-presets-25',['org.mcsettlement.planner.OrganicPresetMain',str(evidence/'organic')]),
        ('route-richness',['org.mcsettlement.planner.RouteRichnessMain',str(evidence/'route-richness')]),
        ('terrain-controls',['org.mcsettlement.planner.TerrainControlsMain',str(evidence/'terrain-controls')]),
        ('bridge-diagonal',['org.mcsettlement.llm.BridgeTerrainRegressionMain']),
        ('fabric-syntax-only',['SyntaxCheck',str(ROOT/'fabric-mod/src/main/java')]),
    ]
    if args.loopback_tests: commands.append(('bridge-loopback',['org.mcsettlement.llm.BridgeRegressionMain']))
    results = []
    for name, tail in commands:
        started = time.perf_counter()
        with (evidence/(name+'.log')).open('wb') as log:
            try:
                code = subprocess.run(['java','-Xmx1g','-cp',cp,*tail],cwd=ROOT,stdout=log,stderr=subprocess.STDOUT,timeout=300).returncode
            except subprocess.TimeoutExpired: code = 124
        results.append({'test':name,'exit_code':code,'elapsed_seconds':round(time.perf_counter()-started,3),'log':name+'.log'})
        print(('PASS' if code==0 else 'FAIL')+' '+name,flush=True)
    (evidence/'verification-status.json').write_text(json.dumps(results,indent=2),encoding='utf-8')
    return int(any(r['exit_code']!=0 for r in results))

if __name__ == '__main__':
    try: sys.exit(main())
    except (OSError, RuntimeError, subprocess.SubprocessError) as e:
        print(f'ERROR: {e}', file=sys.stderr); sys.exit(1)
