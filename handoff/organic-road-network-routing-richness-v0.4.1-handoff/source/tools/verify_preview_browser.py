#!/usr/bin/env python3
"""DOM + application JS + real local API; injected transport for restricted browsers.
Requires installed Playwright/Chromium. Does not download browsers, disable site policy,
or claim native browser HTTP navigation/GPU rendering when unavailable.
"""
from playwright.sync_api import sync_playwright
import json,time,pathlib,traceback,urllib.request,urllib.error,argparse
parser=argparse.ArgumentParser();parser.add_argument("--base",default="http://127.0.0.1:8766");parser.add_argument("--out",type=pathlib.Path,default=pathlib.Path("build/browser-evidence"));parser.add_argument("--chromium",default="/usr/bin/chromium");args=parser.parse_args()
if not args.base.startswith("http://127.0.0.1:"):raise ValueError("Only local fixture access allowed")
out=args.out;out.mkdir(parents=True,exist_ok=True)
with sync_playwright() as p:
 browser=p.chromium.launch(executable_path=args.chromium,headless=True,args=['--no-sandbox','--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader'])
 page=browser.new_page(viewport={'width':1440,'height':960});errors=[];page.on('pageerror',lambda e:errors.append(str(e)));page.on('console',lambda m: print('console',m.type,m.text,flush=True) if m.type=='error' else None)
 try:
  root=pathlib.Path(__file__).resolve().parents[1]
  def local_fetch(url):
   if not url.startswith('/api/plan?'):raise ValueError('Unexpected URL')
   try:
    response=urllib.request.urlopen(args.base+url,timeout=95);return {'status':response.status,'text':response.read().decode()}
   except urllib.error.HTTPError as e:return {'status':e.code,'text':e.read().decode()}
  page.expose_function('__localFetch',local_fetch)
  html=(root/'tools/preview.html').read_text()
  html=html.replace('<script src="/vendor/three.min.js"></script>','<script>'+(root/'tools/generators/three.min.js').read_text()+'</script>')
  html=html.replace('<script src="/vendor/OrbitControls.js"></script>','<script>'+(root/'tools/generators/OrbitControls.js').read_text()+'</script>')
  html=html.replace('<script>\n  const $', '<script>window.fetch=async (url)=>{const r=await window.__localFetch(url);return {ok:r.status===200,status:r.status,json:async()=>JSON.parse(r.text)};};</script><script>\n  const $')
  t=time.perf_counter();page.set_content(html,wait_until='domcontentloaded');page.wait_for_function('state.current && !state.requestPending',timeout=95000)
  info=page.evaluate('({config:state.current.config,metrics:state.current.metrics,draw:state.renderer?state.renderer.info.render:null})');info['load_seconds']=time.perf_counter()-t;print('default',info,flush=True)
  page.screenshot(path=str(out/'128-planning.png'));print('canvas mode',page.evaluate("state.fallback2D ? '2D fallback' : 'WebGL'"),flush=True)
  page.locator('[data-mode="constructed"]').click();page.wait_for_timeout(1000);page.screenshot(path=str(out/'128-constructed.png'))
  page.select_option('#roadDirections','12');page.check('#diagonalBuildings');page.click('#roll');page.wait_for_function('state.current.config.planSeed===43 && !state.requestPending',timeout=95000)
  rolled=page.evaluate('({config:state.current.config,metrics:state.current.metrics,hasPrior:!!state.previous,previousHasWorld:!!state.previous.original})');print('rolled',rolled,flush=True)
  page.click('#compare');page.screenshot(path=str(out/'12-directions-compare.png'))
  page.select_option('#mapSize','512');page.click('#generate');page.wait_for_function('state.current.config.width===512 && !state.requestPending',timeout=95000);page.screenshot(path=str(out/'512-planning.png'))
  geometry=page.evaluate('''(()=>{const group=renderWorld(state.current.constructed);let meshes=0,vertices=0,instances=0;group.traverse(o=>{if(o.isMesh){meshes++;const p=o.geometry.getAttribute("position");vertices+=p.count;for(let i=0;i<p.array.length;i++)if(!Number.isFinite(p.array[i]))throw Error("Nonfinite geometry");if(o.isInstancedMesh)instances+=o.count;}});disposeGroup(group);return {meshes,vertices,instances};})()''');print('512 CPU geometry',geometry,flush=True)
  big=page.evaluate('({config:state.current.config,metrics:state.current.metrics,draw:state.renderer?state.renderer.info.render:null,geometries:state.renderer?state.renderer.info.memory.geometries:null})');print('big',big,flush=True)
  page.select_option('#mapSize','custom');page.fill('#mapWidth','192');page.fill('#mapDepth','96');page.click('#generate');page.wait_for_function('state.current.config.width===192 && !state.requestPending',timeout=95000);page.screenshot(path=str(out/'custom-192x96.png'))
  custom=page.evaluate('({config:state.current.config,metrics:state.current.metrics})');print('custom',custom,flush=True)
  (out/'browser-results.json').write_text(json.dumps({'default':info,'rolled':rolled,'big':big,'custom':custom,'geometry512':geometry,'pageErrors':errors,'renderMode':page.evaluate('state.fallback2D ? "2D fallback" : "WebGL"'),'transport':'Unmodified app JS; injected Python-to-real-HTTP fetch bridge because managed Chromium blocks loopback navigation'},indent=2))
 except Exception as e:
  print('BROWSER FAILURE',str(e),flush=True);traceback.print_exc();page.screenshot(path=str(out/'failure.png'));(out/'failure.json').write_text(json.dumps({'error':str(e),'pageErrors':errors,'message':page.locator('#message').inner_text()}));raise
 finally:browser.close()
