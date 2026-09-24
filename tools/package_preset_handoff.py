"""Package the portable preset authoring standard with current integration references."""
import hashlib
import json
from pathlib import Path
import shutil
import zipfile

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'handoff/building-preset-standard-v1'

def main():
    shutil.copy2(ROOT/'tools/compile_building_preset.py',OUT/'compile_building_preset.py')
    references=[
        'core-planner/src/main/java/org/mcsettlement/planner/preset/'+name+'.java'
        for name in ['BuildingPreset','BuildingShape','PlannedBuilding','BuildingPresetRegistry','PresetPalette']]
    references += ['core-planner/src/main/java/org/mcsettlement/planner/'+name+'.java'
                   for name in ['ExpertSettings','ExpertSettlementPlanner','BoundedSettlementPlanner','SettlementPlanner']]
    references += ['core-planner/src/main/java/org/mcsettlement/planner/ir/PlanningIR.java',
        'core-planner/src/main/java/org/mcsettlement/planner/civil/PlanConstruction.java',
        'core-planner/src/main/java/org/mcsettlement/planner/simulation/SimulationApiRunner.java',
        'core-planner/src/test/java/org/mcsettlement/planner/PresetControlsMain.java',
        'tools/test_compile_building_preset.py','tools/verify_preset_controls.py','tools/preview.html','tools/preview_server.py']
    for name in references:
        dest=OUT/'source-reference'/name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(ROOT/name,dest)
    evidence=OUT/'verification';evidence.mkdir(exist_ok=True)
    for name in ['verification-status.json','preset-controls.log','environment.json']:
        shutil.copy2(ROOT/'build/preset-controls-final'/name,evidence/name)
    shutil.copy2(ROOT/'build/preset-controls-api/results.json',evidence/'http-results.json')
    shutil.copy2(ROOT/'build/preset-controls-api/surfaces/results.json',evidence/'surface-http-results.json')
    shutil.copy2(ROOT/'docs/PRESET_CONTROLS_20260922_ZH.md',OUT/'LATEST_HANDOFF_ZH.md')
    files=sorted(p for p in OUT.rglob('*') if p.is_file() and p.name!='manifest.json' and '__pycache__' not in p.parts)
    manifest={p.relative_to(OUT).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in files}
    (OUT/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    archive=OUT.with_suffix('.zip')
    with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
        for p in files+[OUT/'manifest.json']:z.write(p,p.relative_to(OUT.parent))
    with zipfile.ZipFile(archive) as z:assert z.testzip() is None
    print('PASS',archive,'files',len(files)+1,'bytes',archive.stat().st_size)

if __name__=='__main__':main()
