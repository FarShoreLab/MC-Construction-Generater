#!/usr/bin/env python3
"""Exercise actual DOM, form controls and local Java API. No generated screenshots or API mocks."""
from __future__ import annotations
import argparse,json,traceback,urllib.request,urllib.error
from pathlib import Path
from playwright.sync_api import sync_playwright

def main():
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--base',default='http://127.0.0.1:18767');ap.add_argument('--out',type=Path,default=Path('build/terrain-browser'));ap.add_argument('--chromium',default='/usr/bin/chromium');a=ap.parse_args()
    if not a.base.startswith('http://127.0.0.1:'):raise ValueError('Local test fixture only')
    root=Path(__file__).resolve().parents[1];a.out.mkdir(parents=True,exist_ok=True)
    results={'checks':[],'pageErrors':[],'scenes':{}}
    with sync_playwright() as pw:
        browser=pw.chromium.launch(executable_path=a.chromium,headless=True,args=['--no-sandbox','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader'])
        page=browser.new_page(viewport={'width':1540,'height':1050},device_scale_factor=1)
        page.on('pageerror',lambda e:results['pageErrors'].append(str(e)))
        try:
            try:
                response=page.goto(a.base,wait_until='domcontentloaded',timeout=15000)
                assert response and response.ok and page.locator('#terrainOnly').count()==1
                results['transport']='native Chromium HTTP to real local API'
            except Exception as exc:
                results['nativeNavigationFailure']=str(exc)[:500]
                def fetch(url):
                    if not (url.startswith('/api/plan?') or url.startswith('/api/terrain?') or url=='/api/terrain-controls'):raise ValueError('Unexpected endpoint')
                    try:r=urllib.request.urlopen(a.base+url,timeout=95)
                    except urllib.error.HTTPError as error:r=error
                    with r:return {'status':r.status,'text':r.read().decode()}
                page.expose_function('__localFetch',fetch)
                html=(root/'tools/preview.html').read_text(encoding='utf-8')
                for name in ['three.min.js','OrbitControls.js']:
                    html=html.replace('<script src="/vendor/'+name+'"></script>','<script>'+(root/'tools/generators'/name).read_text()+'</script>')
                html=html.replace('<script>\n  const $','<script>window.fetch=async(url)=>{const r=await window.__localFetch(url);return {ok:r.status===200,status:r.status,json:async()=>JSON.parse(r.text)};};</script><script>\n  const $')
                page.set_content(html,wait_until='domcontentloaded')
                results['transport']='actual application JS with Python-to-real-local-HTTP fetch bridge'
            page.wait_for_function('state.current && !state.requestPending',timeout=100000)
            assert page.evaluate('terrainSchema.controls.length')==10
            assert page.locator('#horizontalScaleHelp').inner_text().find('调低')>=0
            assert page.locator('#horizontalScaleHelp').inner_text().find('调高')>=0
            results['checks'].append('Ten schema-backed controls with actual low/high explanations')
            page.select_option('#terrainType','valley');assert page.input_value('#waterLevelRatio')=='0.28'
            page.evaluate("document.getElementById('terrainGroup_shape').open=true")
            assert page.locator('#valleyWidthField').is_visible() and not page.locator('#ridgeSharpnessField').is_visible()
            page.fill('#waterLevelRatio','.41');page.select_option('#terrainType','mountain');assert page.input_value('#waterLevelRatio')=='.41'
            assert page.locator('#ridgeSharpnessField').is_visible() and not page.locator('#valleyWidthField').is_visible()
            results['checks'].append('Type-specific controls filter correctly; untouched water defaults follow type; customized water is retained')
            page.click('#resetTerrain');assert page.input_value('#waterLevelRatio')=='0.22'
            page.select_option('#terrainType','rolling_hills');page.select_option('#mapSize','256')
            page.evaluate("document.getElementById('terrainGroup_trees').open=true")
            page.fill('#treeDensity','.4');page.fill('#horizontalScale','.5');page.fill('#detailStrength','.5')
            page.click('#terrainOnly');page.wait_for_function("state.current.kind==='TERRAIN_ONLY' && !state.requestPending",timeout=100000)
            assert page.evaluate("state.current.config.terrainParameters.horizontalScale") == .5
            assert page.evaluate("!state.current.plan && !state.current.constructed && state.mode==='original'")
            assert page.locator('[data-mode="constructed"]').is_disabled()
            def capture(name):
                page.evaluate("document.getElementById('panel').scrollTop=250")
                page.wait_for_timeout(400);page.screenshot(path=str(a.out/(name+'.png')))
                results['scenes'][name]=page.evaluate('({config:state.current.config,metrics:state.current.metrics})')
            capture('scale-low-256')
            page.fill('#horizontalScale','1.23');assert page.input_value('#horizontalScaleRange')=='1.23'
            page.fill('#horizontalScale','2');assert page.input_value('#horizontalScaleRange')=='2'
            page.click('#terrainOnly');page.wait_for_function("state.current.config.terrainParameters.horizontalScale===2 && !state.requestPending",timeout=100000)
            capture('scale-high-256')
            results['checks'].append('Numeric/slider synchronization and terrain-only generation alter actual API terrain')
            # Do not silently apply pending terrain changes during a planning-only roll.
            original_hash=page.evaluate('state.current.config.originalTerrainHash')
            page.fill('#horizontalScale','.8');page.fill('#targetPlots','3');page.click('#roll')
            page.wait_for_function("state.current.kind==='PLAN' && !state.requestPending",timeout=100000)
            assert page.evaluate('state.current.config.originalTerrainHash')==original_hash
            assert page.evaluate('state.current.config.terrainParameters.horizontalScale')==2
            assert page.input_value('#horizontalScale')=='2'
            assert not page.locator('[data-mode="constructed"]').is_disabled()
            results['checks'].append('Planning-only roll reuses last actual terrain parameters, not pending form edits')
            page.click('#roll');page.wait_for_function('state.previous && !state.requestPending',timeout=100000)
            page.click('#compare');assert page.evaluate('state.mode')=='compare'
            results['checks'].append('Original same-terrain plan comparison still works')
            # Invalid manual values report an error without replacing the current result.
            old_hash=page.evaluate('state.current.config.originalTerrainHash')
            page.fill('#horizontalScale','0');page.click('#terrainOnly')
            assert page.locator('#message').inner_text().find('0.25')>=0
            assert page.evaluate('state.current.config.originalTerrainHash')==old_hash
            page.click('#resetTerrain');page.select_option('#terrainType','valley')
            page.fill('#valleyWidth','1.4');page.fill('#waterLevelRatio','.38');page.click('#terrainOnly')
            page.wait_for_function("state.current.kind==='TERRAIN_ONLY' && state.current.config.terrainType==='valley' && !state.requestPending",timeout=100000)
            capture('valley-controls')
            results['checks'].append('Invalid UI values preserve last result; reset and subsequent generation recover')
            assert page.evaluate("Array.from(document.querySelectorAll('#terrainControls input[type=number]')).every(e=>e.getBoundingClientRect().right<=document.getElementById('panel').getBoundingClientRect().right)")
            assert not results['pageErrors'],results['pageErrors']
            results['renderMode']=page.evaluate('state.fallback2D ? "2D fallback" : "WebGL (software ANGLE/SwiftShader flags)"')
            results['passed']=True
        except Exception as exc:
            results['passed']=False;results['error']=str(exc);traceback.print_exc();page.screenshot(path=str(a.out/'failure.png'));raise
        finally:
            (a.out/'results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8');browser.close()
    print('RESULT terrain browser checks passed')

if __name__=='__main__':main()
