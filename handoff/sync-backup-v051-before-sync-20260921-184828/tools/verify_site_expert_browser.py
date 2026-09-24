#!/usr/bin/env python3
"""Exercise the delivered UI and real Java HTTP API (never simulated responses)."""
from __future__ import annotations
import argparse,json,traceback,urllib.request,urllib.error
from pathlib import Path
from playwright.sync_api import sync_playwright

def main():
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--base',default='http://127.0.0.1:18767');ap.add_argument('--out',type=Path,default=Path('build/site-browser'));ap.add_argument('--chromium',default='/usr/bin/chromium');a=ap.parse_args();assert a.base.startswith('http://127.0.0.1:')
    root=Path(__file__).resolve().parents[1];a.out.mkdir(parents=True,exist_ok=True);result={'checks':[],'pageErrors':[],'scenes':{}}
    with sync_playwright() as pw:
        browser=pw.chromium.launch(executable_path=a.chromium,headless=True,args=['--no-sandbox','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader'])
        page=browser.new_page(viewport={'width':1560,'height':1080},device_scale_factor=1);page.on('pageerror',lambda e:result['pageErrors'].append(str(e)))
        def wait(condition='state.current'):
            page.wait_for_function(f'!state.requestPending && ({condition})',timeout=100000)
        def capture(name):
            page.wait_for_timeout(250);page.screenshot(path=str(a.out/(name+'.png')));result['scenes'][name]=page.evaluate('({config:state.current.config,metrics:state.current.metrics})')
        try:
            try:
                response=page.goto(a.base,wait_until='domcontentloaded',timeout=15000);assert response and response.ok and page.locator('#layoutMode').count()==1;result['transport']='native HTTP'
            except Exception as e:
                result['nativeNavigationFailure']=str(e)[:400]
                def fetch(url, options=None):
                    assert url=='/api/plan' or url=='/api/terrain-controls' or url.startswith('/api/terrain?') or url.startswith('/api/plan?')
                    o=options or {};body=o.get('body');req=urllib.request.Request(a.base+url,data=body.encode() if body else None,headers=o.get('headers') or {},method=o.get('method','GET'))
                    try:r=urllib.request.urlopen(req,timeout=95)
                    except urllib.error.HTTPError as e:r=e
                    with r:return {'status':r.status,'text':r.read().decode()}
                page.expose_function('__localFetch',fetch);html=(root/'tools/preview.html').read_text()
                for name in ['three.min.js','OrbitControls.js']:html=html.replace('<script src="/vendor/'+name+'"></script>','<script>'+(root/'tools/generators'/name).read_text()+'</script>')
                html=html.replace('<script>\n  const $','<script>window.fetch=async(url,o={})=>{const r=await window.__localFetch(url,{method:o.method,headers:o.headers,body:o.body});return {ok:r.status===200,status:r.status,json:async()=>JSON.parse(r.text)};};</script><script>\n  const $')
                page.set_content(html,wait_until='domcontentloaded');result['transport']='actual app JS via Python-to-real-local-HTTP bridge (POST included)'
            wait();assert page.evaluate('state.current.config.layoutMode')=='expert';assert page.evaluate('terrainSchema.controls.length')==10
            assert page.evaluate('state.current.metrics.status')=='COMPLETE';result['checks'].append('Default expert mode, unchanged 10 terrain controls, actual completed plan')
            page.select_option('#mapSize','512');page.fill('#planSeed','48');page.click('#generate');wait('state.current.config.width===512 && state.current.config.planSeed===48')
            assert page.evaluate('state.current.metrics.sitePlanning.bboxCoverage')>=.25
            page.evaluate("document.getElementById('panel').scrollTop=document.getElementById('layoutMode').offsetTop-60");capture('expert512-planning')
            page.click('[data-mode="constructed"]');capture('expert512-constructed');result['checks'].append('512 full map uses actual new sites/roads and construction layers')
            page.select_option('#mapSize','128');page.fill('#relief','4');page.fill('#waterLevelRatio','0');page.fill('#planSeed','42');page.click('#generate');wait('state.current.config.width===128 && state.current.config.relief===4')
            page.evaluate("document.getElementById('pinPanel').open=true")
            page.click('#pickPin');assert page.evaluate('pickPinMode')
            if page.evaluate('state.fallback2D'):
                point=page.evaluate("""(()=>{const r=document.getElementById('canvas').getBoundingClientRect(),w=128,d=128,s=Math.min((r.width-48)/w,(r.height-80)/d);return {x:r.left+(r.width-w*s)/2+20.5*s,y:r.top+(r.height-d*s)/2+20.5*s};})()""")
                page.mouse.click(point['x'],point['y']);assert page.input_value('#pinX')=='20' and page.input_value('#pinZ')=='20'
                result['checks'].append('Actual 2D canvas click maps to exact X/Z origin')
            else:
                page.click('#pickPin');page.fill('#pinX','20');page.fill('#pinZ','20');result['checks'].append('WebGL scene rendered; manual numeric coordinates used (ray picking not asserted)')
            page.fill('#pinId','home');page.select_option('#pinFacing','EAST');page.fill('#pinConnect','other');page.click('#savePin')
            page.fill('#pinId','other');page.fill('#pinX','95');page.fill('#pinZ','85');page.select_option('#pinFacing','WEST');page.fill('#pinConnect','network');page.click('#savePin')
            assert page.evaluate('manualPins.length')==2;page.click('#generate');wait('state.current.config.expertSettings.pins.length===2');assert page.evaluate('state.current.metrics.status')=='COMPLETE'
            assert page.evaluate("state.current.plan.plots.find(p=>p.requirementId==='home').origin.join(',')")=='20,20'
            assert page.evaluate("state.current.metrics.sitePlanning.requiredConnections.some(c=>c.from==='home'&&c.to==='other'&&c.status.startsWith('VERIFIED'))")
            page.click('[data-mode="planning"]');page.evaluate("document.getElementById('panel').scrollTop=document.getElementById('pinPanel').offsetTop-40");capture('manual-pins-forced-connection')
            result['checks'].append('Add/update pins through visible form; exact locked positions and named forced connection reach real API')
            terrain=page.evaluate('state.current.config.originalTerrainHash');old_seed=page.evaluate('state.current.config.planSeed');page.fill('#horizontalScale','1.5');page.click('#roll');wait(f'state.current.config.planSeed==={old_seed+1}')
            assert page.evaluate('state.current.config.originalTerrainHash')==terrain;assert page.evaluate("state.current.plan.plots.find(p=>p.requirementId==='home').origin.join(',')")=='20,20'
            assert page.evaluate('state.current.config.terrainParameters.horizontalScale')==1;assert page.evaluate('state.current.metrics.status')=='COMPLETE';result['checks'].append('Roll preserves exact pins and last generated terrain, not pending terrain edits')
            page.click('#compare');assert page.evaluate('state.mode')=='compare';result['checks'].append('Comparison still available on same terrain')
            before=page.evaluate('state.current.metrics.planHash');page.fill('#minBBoxCoverage','');page.click('#roll');assert page.evaluate('state.current.metrics.planHash')==before;assert 'minBBoxCoverage' in page.locator('#message').inner_text();result['checks'].append('Blank numeric setting reports error without silently becoming zero or throwing page error')
            page.fill('#minBBoxCoverage','85');page.click('#generate');wait("state.current.metrics.status==='REJECTED'");assert page.evaluate('state.current.metrics.constructionEdits')==0;assert page.evaluate('state.current.config.expertSettings.pins.length')==2
            page.evaluate("document.getElementById('panel').scrollTop=document.getElementById('siteDetails').offsetTop-40");capture('hard-threshold-rejection');result['checks'].append('Too-strict coverage rejects atomically while preserving human constraints and diagnostics')
            page.fill('#minBBoxCoverage','');page.click('#terrainOnly');wait("state.current.kind==='TERRAIN_ONLY'");result['checks'].append('Terrain-only remains independent of invalid unsubmitted planning controls')
            page.fill('#minBBoxCoverage','25');page.fill('#pinId','other');page.fill('#pinX','512');page.fill('#pinZ','85');page.click('#savePin');page.click('#generate');wait("state.current.kind==='TERRAIN_ONLY'");assert 'PIN_OUT_OF_BOUNDS' in page.locator('#message').inner_text();result['checks'].append('Invalid exact pin returns visible server error; previous world remains')
            page.select_option('#pinKind','dock');assert page.locator('#pinPreset').is_disabled() and page.locator('#pinFacing').is_disabled();page.select_option('#pinKind','water');assert not page.locator('#pinPreset').is_disabled();result['checks'].append('Land/water/dock selector and irrelevant fields behave correctly')
            # Successful water-house preset on actual generated terrain, using only the visible form.
            while page.locator('#pinList button').count():page.locator('#pinList button').nth(1).click()
            page.fill('#relief','12');page.fill('#waterLevelRatio','.22');page.fill('#horizontalScale','1');page.fill('#planSeed','42')
            page.fill('#pinId','water_house');page.fill('#pinX','100');page.fill('#pinZ','56');page.fill('#pinY','');page.select_option('#pinFacing','EAST');page.fill('#pinConnect','network');page.click('#savePin');page.click('#generate')
            wait("state.current.kind==='PLAN' && state.current.config.relief===12 && state.current.config.expertSettings.pins.length===1")
            assert page.evaluate('state.current.metrics.status')=='COMPLETE'
            assert page.evaluate('state.current.metrics.sitePlanning.waterBuildingCount')==1
            assert page.evaluate('state.current.metrics.sitePlanning.bridgeColumns')>0
            page.click('[data-mode="constructed"]');page.evaluate("document.getElementById('panel').scrollTop=document.getElementById('pinPanel').offsetTop-40");capture('water-house-dock-constructed')
            result['checks'].append('Water-house pin in real generated lake produces actual raised foundation, bridge columns and construction through POST')
            assert not result['pageErrors'],result['pageErrors'];result['renderMode']=page.evaluate('state.fallback2D?"2D fallback":"WebGL"');result['passed']=True
        except Exception as e:
            result['passed']=False;result['error']=str(e);traceback.print_exc();page.screenshot(path=str(a.out/'failure.png'));raise
        finally:(a.out/'results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2));browser.close()
    print('ALL EXPERT BROWSER CHECKS PASSED')
if __name__=='__main__':main()
