#!/usr/bin/env python3
"""New v0.5.1 controls tested with real app JS and actual local HTTP/Java output."""
from __future__ import annotations
import argparse,json,traceback,urllib.request,urllib.error
from pathlib import Path
from playwright.sync_api import sync_playwright

def main():
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--base',default='http://127.0.0.1:18767');ap.add_argument('--out',type=Path,default=Path('build/network-browser'));ap.add_argument('--chromium',default='/usr/bin/chromium');a=ap.parse_args()
    assert a.base.startswith('http://127.0.0.1:');a.out.mkdir(parents=True,exist_ok=True);root=Path(__file__).resolve().parents[1]
    result={'passed':False,'checks':[],'scenes':{},'pageErrors':[]}
    with sync_playwright() as pw:
        browser=pw.chromium.launch(executable_path=a.chromium,headless=True,args=['--no-sandbox','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader'])
        page=browser.new_page(viewport={'width':1680,'height':1060},device_scale_factor=1);page.on('pageerror',lambda e:result['pageErrors'].append(str(e)))
        def wait(condition='state.current'):page.wait_for_function(f'!state.requestPending && ({condition})',timeout=100000)
        def capture(name):
            page.wait_for_timeout(250);page.screenshot(path=str(a.out/(name+'.png')));result['scenes'][name]=page.evaluate('({config:state.current.config,metrics:state.current.metrics})')
        try:
            try:
                response=page.goto(a.base,wait_until='domcontentloaded',timeout=15000);assert response and response.ok and page.locator('#releaseVersion').count()==1;result['transport']='native HTTP'
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
                page.set_content(html,wait_until='domcontentloaded');result['transport']='actual app JS through Python-to-real-local-HTTP bridge'
            wait();assert '0.5.1' in page.locator('#releaseVersion').inner_text();assert page.evaluate('state.current.config.roadDirections')==16
            assert page.evaluate('state.current.metrics.status')=='COMPLETE';assert page.evaluate('terrainSchema.controls.length')==10
            assert page.input_value('#buildingRepulsion')=='1' and page.input_value('#roadMergeDistance')=='7'
            result['checks'].append('Actual page defaults to 16-heading mixed mode and exposes both new controls; original 10 terrain controls preserved')
            page.select_option('#mapSize','256');page.fill('#planSeed','54');page.fill('#targetPlots','9');page.select_option('#roadDirections','8');page.click('#generate');wait('state.current.config.width===256 && state.current.config.planSeed===54 && state.current.config.roadDirections===8')
            assert page.evaluate('state.current.metrics.status')=='COMPLETE';assert page.evaluate('state.current.metrics.sitePlanning.closePairs')==0;assert page.evaluate('state.current.metrics.sitePlanning.macroLoops')>0
            page.evaluate("document.getElementById('panel').scrollTop=document.getElementById('layoutMode').offsetTop-20");capture('screen256-8way-planning')
            result['checks'].append('Screenshot-visible 256/seed54/9-building settings generate a complete, non-crowded layout with a macro enclosure')
            page.select_option('#roadDirections','16');page.click('#generate');wait('state.current.config.roadDirections===16')
            assert page.evaluate('state.current.metrics.network.diagonal45Steps>0 && state.current.metrics.network.obliqueSteps>0')
            capture('screen256-mixed-planning');page.click('[data-mode="constructed"]');capture('screen256-mixed-constructed');result['checks'].append('45-degree and oblique moves both occur in committed geometry and construction view')
            page.select_option('#mapSize','512');page.click('#generate');wait('state.current.config.width===512');assert page.evaluate('state.current.metrics.status')=='COMPLETE';assert page.evaluate('state.current.metrics.plotCount')==9
            page.click('[data-mode="planning"]');capture('mixed512-planning');result['checks'].append('512 mixed-mode 9-building actual plan and construction data, not a placeholder scene')
            old=page.evaluate('state.current.metrics.planHash');page.fill('#buildingRepulsion','');page.click('#roll');assert 'buildingRepulsion' in page.locator('#message').inner_text();assert page.evaluate('state.current.metrics.planHash')==old
            page.fill('#buildingRepulsion','1');page.fill('#roadMergeDistance','1.5');page.click('#roll');assert 'roadMergeDistance' in page.locator('#message').inner_text();assert page.evaluate('state.current.metrics.planHash')==old
            result['checks'].append('Blank/fractional invalid controls do not replace a valid current plan')
            page.fill('#roadMergeDistance','10');page.fill('#buildingRepulsion','2');page.select_option('#mapSize','128');page.fill('#relief','4');page.fill('#waterLevelRatio','0');page.fill('#planSeed','42');page.click('#generate');wait('state.current.config.width===128 && state.current.config.relief===4')
            assert page.evaluate('state.current.config.expertSettings.buildingRepulsion')==2 and page.evaluate('state.current.config.expertSettings.roadMergeDistance')==10
            before=page.evaluate('state.current.config.originalTerrainHash');page.fill('#horizontalScale','2');page.click('#roll');wait('state.current.config.planSeed===43')
            assert page.evaluate('state.current.config.originalTerrainHash')==before and page.evaluate('state.current.config.expertSettings.buildingRepulsion')==2 and page.input_value('#roadMergeDistance')=='10'
            result['checks'].append('Both parameters propagate through POST, actual config and roll; pending terrain edits remain unsubmitted')
            page.select_option('#layoutMode','legacy');assert page.locator('#expertFields').is_hidden();page.select_option('#layoutMode','expert');assert not page.locator('#expertFields').is_hidden()
            result['checks'].append('Legacy/expert switch correctly shows relevant controls')
            assert not result['pageErrors'];result['renderMode']=page.evaluate('state.fallback2D?"2D fallback":"WebGL"');result['passed']=True
        except Exception as e:
            result['error']=str(e);traceback.print_exc();page.screenshot(path=str(a.out/'failure.png'));raise
        finally:
            (a.out/'results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2));browser.close()
    print('ALL NETWORK BROWSER CHECKS PASSED')
if __name__=='__main__':main()
