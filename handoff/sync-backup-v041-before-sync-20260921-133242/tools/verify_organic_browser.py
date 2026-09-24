#!/usr/bin/env python3
"""Exercise the real app and real local API, capture original/planned/constructed scenes.
Uses a Python-to-HTTP bridge only if managed Chromium cannot navigate to loopback.
Never substitutes mock planner data or downloads a browser. Software WebGL is labelled.
"""
import argparse,json,time,traceback,urllib.request,urllib.error
from pathlib import Path
from playwright.sync_api import sync_playwright
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--base',default='http://127.0.0.1:8766');p.add_argument('--out',type=Path,default=Path('build/organic-browser'));p.add_argument('--chromium',default='/usr/bin/chromium');a=p.parse_args()
if not a.base.startswith('http://127.0.0.1:'):raise ValueError('Loopback fixtures only')
a.out.mkdir(parents=True,exist_ok=True);root=Path(__file__).resolve().parents[1];results={'scenes':{},'pageErrors':[],'checks':[]}
with sync_playwright() as pw:
 browser=pw.chromium.launch(executable_path=a.chromium,headless=True,args=['--no-sandbox','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader'])
 page=browser.new_page(viewport={'width':1920,'height':1400},device_scale_factor=1)
 page.on('pageerror',lambda e:results['pageErrors'].append(str(e)))
 try:
  try:
   response=page.goto(a.base,wait_until='domcontentloaded',timeout=15000)
   assert response and response.ok and page.locator('#generate').count()==1,'Loopback navigation blocked or app missing'
   results['transport']='native Chromium HTTP to real local Python/Java API'
  except Exception as exc:
   results['nativeNavigationFailure']=str(exc)[:500]
   def local_fetch(url):
    if not url.startswith('/api/plan?'):raise ValueError('Unexpected endpoint')
    try:r=urllib.request.urlopen(a.base+url,timeout=95)
    except urllib.error.HTTPError as error:r=error
    with r:return {'status':r.status,'text':r.read().decode()}
   page.expose_function('__localFetch',local_fetch)
   html=(root/'tools/preview.html').read_text()
   for name in ['three.min.js','OrbitControls.js']:
    html=html.replace('<script src="/vendor/'+name+'"></script>','<script>'+(root/'tools/generators'/name).read_text()+'</script>')
   html=html.replace('<script>\n  const $','<script>window.fetch=async(url)=>{const r=await window.__localFetch(url);return {ok:r.status===200,status:r.status,json:async()=>JSON.parse(r.text)};};</script><script>\n  const $')
   page.set_content(html,wait_until='domcontentloaded');results['transport']='real application JS with Python-to-real-HTTP fetch bridge; native loopback navigation blocked'
  page.wait_for_function('state.current && !state.requestPending',timeout=95000)
  results['default']=page.evaluate('({config:state.current.config,metrics:state.current.metrics})')
  page.click('#openCatalog');assert page.locator('.preset-card').count()==22;page.screenshot(path=str(a.out/'22-preset-catalog.png'));page.click('#closeCatalog');results['checks'].append('22 preset cards rendered from actual API masks')
  def generate(name,size,terrain,relief,palette):
   page.select_option('#mapSize',str(size));page.select_option('#terrainType',terrain);page.fill('#relief',str(relief));page.fill('#baseElevation','58');page.fill('#terrainSeed','42');page.fill('#planSeed','42');page.fill('#targetPlots','7');page.select_option('#roadDirections','8');page.uncheck('#diagonalBuildings');page.select_option('#presetPalette',palette)
   before=time.perf_counter();page.click('#generate');page.wait_for_function(f'state.current.config.width==={size} && state.current.config.terrainType==="{terrain}" && state.current.config.relief==={relief} && state.current.config.presetPalette==="{palette}" && !state.requestPending',timeout=95000)
   record=page.evaluate('({config:state.current.config,metrics:state.current.metrics})');record['wall_seconds']=time.perf_counter()-before;results['scenes'][name]=record
   assert record['metrics']['plotCount']==7,(name,record)
   page.evaluate("if(!state.fallback2D){state.topView=false;state.camera.up.set(0,1,0);fitCameraToTerrain(state.current.original)}")
   page.click('#topView')
   for mode in ['original','planning','constructed']:
    page.locator('[data-mode="'+mode+'"]').click();page.wait_for_timeout(600);page.screenshot(path=str(a.out/(name+'-'+mode+'.png')))
   if not page.evaluate('!!state.fallback2D'):
    page.click('#topView');page.wait_for_timeout(600);page.screenshot(path=str(a.out/(name+'-constructed-oblique.png')))
   print('BROWSER',name,record['metrics']['network'],flush=True)
  generate('reference512',512,'mountain',24,'classic')
  assert results['scenes']['reference512']['metrics']['network']['verifiedLoops']>=1
  results['geometry512']=page.evaluate('''(()=>{const group=renderWorld(state.current.constructed);let meshes=0,vertices=0,instances=0;group.traverse(o=>{if(o.isMesh){meshes++;const p=o.geometry.getAttribute("position");vertices+=p.count;for(const value of p.array)if(!Number.isFinite(value))throw Error("Nonfinite geometry");if(o.isInstancedMesh)instances+=o.count;}});disposeGroup(group);return {meshes,vertices,instances};})()''')
  generate('loop128',128,'rolling_hills',4,'classic')
  generate('mixed128',128,'rolling_hills',12,'mixed')
  assert page.evaluate('new Set(state.current.plan.plots.map(p=>p.footprintShape)).size')>=5
  page.click('#roll');page.wait_for_function('state.current.config.planSeed===43 && !state.requestPending',timeout=95000)
  rolled=page.evaluate('({config:state.current.config,metrics:state.current.metrics,sameTerrain:!!state.previous&&state.previous.config.originalTerrainHash===state.current.config.originalTerrainHash,priorHasWorld:!!state.previous&&!!state.previous.original})')
  assert rolled['sameTerrain'] and not rolled['priorHasWorld'];results['rolled']=rolled;page.click('#compare');page.screenshot(path=str(a.out/'mixed128-roll-compare.png'));results['checks'].append('Roll preserves terrain, changes plan, prior full world not retained')
  page.click('#openCatalog');page.locator('.preset-card',has_text='中庭住宅').click();assert page.input_value('#presetPalette')=='single' and page.input_value('#singlePresetId')=='courtyard_house';results['checks'].append('Clicking actual courtyard card selects correct single preset')
  page.select_option('#mapSize','custom');page.fill('#mapWidth','192');page.fill('#mapDepth','96');page.fill('#targetPlots','1');page.select_option('#roadDirections','12');page.check('#diagonalBuildings');page.click('#generate');page.wait_for_function('state.current.config.width===192 && state.current.config.depth===96 && !state.requestPending',timeout=95000)
  custom=page.evaluate('({config:state.current.config,metrics:state.current.metrics})');assert custom['config']['singlePresetId']=='courtyard_house' and custom['config']['roadDirections']==12;results['custom']=custom
  page.locator('[data-mode="planning"]').click();page.screenshot(path=str(a.out/'custom192-single-courtyard.png'))
  results['renderMode']=page.evaluate('state.fallback2D ? "2D fallback" : "WebGL (Chromium launched with software ANGLE/SwiftShader flags)"')
  results['geometryMemory']=page.evaluate('state.renderer ? state.renderer.info.memory : null')
  assert not results['pageErrors'],results['pageErrors'];results['passed']=True
 except Exception as exc:
  results['passed']=False;results['error']=str(exc);traceback.print_exc();page.screenshot(path=str(a.out/'failure.png'));raise
 finally:
  (a.out/'browser-results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2));browser.close()
