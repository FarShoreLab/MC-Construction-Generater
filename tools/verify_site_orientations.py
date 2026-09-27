"""Four-facing presentation variants must match actual pinned placement, including 45-degree rasters."""
import json
from pathlib import Path
from verify_two_stage import sites

def main():
    results=[]
    for diagonal in (False,True):
        params=dict(targetPlots=1,presetPalette='single',singlePresetId='timber_cottage',relief=4,minBBoxCoverage=0,minMinorAxisRatio=0,diagonalBuildings=diagonal)
        pin=dict(id='oriented',x=48,z=48,presetId='timber_cottage',entranceIndex=1,diagonal45=diagonal,facing='NORTH')
        initial=sites(pins=[pin],**params)['plan']['plots'][0]
        variants=initial['orientationVariants']
        assert set(variants)=={'NORTH','EAST','SOUTH','WEST'}
        for facing,variant in variants.items():
            placed=sites(pins=[dict(pin,facing=facing)],**params)['plan']['plots'][0]
            assert placed['sourceFacing']==facing and placed['entranceIndex']==1 and placed['diagonal45']==diagonal
            assert variant['footprint']==placed['footprint'], (diagonal,facing,'footprint')
            assert variant['entrance']==[placed['entrance'][0]-placed['origin'][0],placed['entrance'][2]-placed['origin'][1]], (diagonal,facing,'entrance')
            assert variant['entranceFacing']==placed['entranceFacing'], (diagonal,facing,'entrance facing')
            assert variant['width']==max(x for x,z in placed['footprint'])+1
            assert variant['depth']==max(z for x,z in placed['footprint'])+1
            results.append(f'PASS {facing}, diagonal45={diagonal}, entranceIndex=1: preview footprint/entrance match actual placement')
    folder=Path('build/verification/parcel-facing');folder.mkdir(parents=True,exist_ok=True)
    (folder/'orientation-results.json').write_text(json.dumps(results,indent=2),encoding='utf-8')
    for result in results: print(result)

if __name__=='__main__': main()
