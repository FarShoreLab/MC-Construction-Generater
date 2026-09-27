"""Package the portable preset authoring standard with current integration references."""
import hashlib
import json
from pathlib import Path
import shutil
import zipfile

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'handoff/building-preset-standard-v1'
STAGE=ROOT/'build/preset-handoff/building-preset-standard-v1'

def main():
    STAGE.mkdir(parents=True,exist_ok=True)
    for p in OUT.iterdir():
        if p.is_file() and p.name not in {'manifest.json','LATEST_HANDOFF_ZH.md','compiled.json'}:
            shutil.copy2(p,STAGE/p.name)
    shutil.copy2(ROOT/'tools/compile_building_preset.py',STAGE/'compile_building_preset.py')
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
        dest=STAGE/'source-reference'/name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(ROOT/name,dest)
    shutil.copy2(ROOT/'docs/PRESET_CONTROLS_20260922_ZH.md',STAGE/'LATEST_HANDOFF_ZH.md')
    files=sorted(p for p in STAGE.rglob('*') if p.is_file() and p.name!='manifest.json' and '__pycache__' not in p.parts)
    manifest={p.relative_to(STAGE).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in files}
    (STAGE/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
    archive=OUT.with_suffix('.zip')
    with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
        for p in files+[STAGE/'manifest.json']:z.write(p,p.relative_to(STAGE.parent))
    with zipfile.ZipFile(archive) as z:assert z.testzip() is None
    print('PASS',archive,'files',len(files)+1,'bytes',archive.stat().st_size)

if __name__=='__main__':main()
