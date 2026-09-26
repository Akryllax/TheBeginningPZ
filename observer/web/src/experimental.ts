import './style.css';
import { WorldScene } from './scene';
import type { Element, World } from './types';

document.querySelector<HTMLDivElement>('#app')!.innerHTML=`
<div class="app">
 <header class="topbar">
  <div class="brand"><button class="mobile-toggle" id="menu" aria-label="Toggle map controls">☰</button><div class="brand-icon"><span>◈</span></div><div><h1>Zomboid Observer</h1><p>Our world, as we remember it</p></div></div>
  <div class="top-right"><div class="live" id="live-status"><span class="dot"></span><span id="live-text">Connecting</span></div><a class="top-link" href="/downloads/ZomboidObserver.zip">Download observer mod ↗</a></div>
 </header>
 <div class="workspace">
  <aside class="sidebar" id="sidebar">
   <section class="section"><p class="eyebrow">Observation map</p><select class="field" id="observer" aria-label="Observation source"><option value="">Everyone’s observations</option></select><p class="subtitle">A shared memory of the places we’ve seen.</p><form class="search-form" id="search"><input class="field" id="coordinates" placeholder="Go to X, Y" aria-label="Coordinates X, Y"/><button aria-label="Go to coordinates">↗</button></form><p id="search-error" class="warning"></p></section>
   <section class="section"><div class="section-title"><p class="eyebrow">Survivors</p><span id="player-count" class="count">0</span></div><div id="players"><p class="subtitle">Waiting for player observations.</p></div></section>
   <section class="section"><p class="eyebrow">Layers</p><div id="layers"></div></section>
   <section class="section"><div class="section-title"><p class="eyebrow">Public markers</p><span class="count" id="marker-count">0</span></div><div id="markers"></div></section>
   <section class="section"><p class="eyebrow">Field notes</p><p class="status-foot" id="coverage-note">Loading explored areas…</p><p class="status-foot">Unseen changes stay unknown. Contents update only when someone inspects them.</p><p class="warning" id="source-warning"></p></section>
  </aside>
  <main class="map-wrap">
   <div class="canvas-host" id="scene"></div>
   <div class="map-toolbar"><div class="segmented" role="group" aria-label="Camera mode"><button class="active" id="mode-3d" aria-pressed="true">3D view</button><button id="mode-iso" aria-pressed="false">Isometric</button></div><div class="floor-tools"><button id="floor-down" aria-label="Lower floor">−</button><span id="floor-label" class="floor-label">Floor 0</span><button id="floor-up" aria-label="Higher floor">+</button><button class="cutaway-button" id="cutaway" aria-pressed="false">Cutaway</button></div></div>
   <div class="demo-banner hidden" id="demo-banner">DEMONSTRATION · Recorded example scene</div>
   <div class="empty-note" id="empty-note"><h2>Waiting for observations</h2><p id="empty-text">The map will take shape as players explore. Previously known areas are shown as a coverage grid.</p></div>
   <div class="map-footer"><span class="footer-coords" id="map-coordinates">10818, 9844 · Z 0</span><span class="hint">Drag to pan · Right-drag to orbit · Scroll to zoom</span><span id="scene-count">0 elements</span></div>
  </main>
  <aside class="inspector" id="inspector"><div class="inspector-head"><button id="close-inspector" class="mobile-toggle" aria-label="Close inspector">×</button><p class="eyebrow">Element inspector</p><h2 id="selection-title">Look a little closer</h2><p class="subtitle" id="selection-subtitle">Select anything in the observed world.</p></div><div class="inspector-body" id="inspector-body"><div class="inspector-empty"><div class="selection-icon">⌖</div>Every object has a story.<br>Select one to see its last observed state,<br>location and inspected contents.</div></div></aside>
 </div>
</div>`;

const $=<T extends HTMLElement=HTMLElement>(id:string)=>document.getElementById(id)! as T;
function node<K extends keyof HTMLElementTagNameMap>(tag:K,text?:string,className?:string) {
  const result=document.createElement(tag);if(text!==undefined)result.textContent=text;if(className)result.className=className;return result;
}
function age(at:number) {
  const seconds=Math.max(0,Math.floor((Date.now()-at)/1000));
  if(seconds<10)return 'Just observed';if(seconds<60)return `${seconds}s ago`;
  if(seconds<3600)return `${Math.floor(seconds/60)}m ago`;
  if(seconds<86400)return `${Math.floor(seconds/3600)}h ago`;
  return `${Math.floor(seconds/86400)}d ago`;
}
async function get<T>(url:string,signal?:AbortSignal):Promise<T> {
  const response=await fetch(url,{signal});if(!response.ok)throw new Error(`Request failed (${response.status})`);return response.json();
}
let scene:WorldScene;
try {scene=new WorldScene($('scene'),select,()=>{following=null;scheduleScene();});}
catch {
  $('empty-note').querySelector('h2')!.textContent='3D rendering unavailable';
  $('empty-text').textContent='This browser could not start WebGL. Try a browser with hardware acceleration enabled.';
  throw new Error('WebGL renderer unavailable');
}
let selected:string|null=null,following:string|null=null,observer='',lastWorld:World|null=null;
let sceneRequest:AbortController|null=null,sceneTimer:ReturnType<typeof setTimeout>|null=null;
let refreshBusy=false,refreshAgain=false,lastCoverageRevision=-1,lastSceneRevision=-1;

const layers:[string,string,string][]=[['floor','Ground & roads','#708570'],['wall','Walls','#e0d7bf'],['door','Doors & windows','#997857'],['roof','Roofs','#627c73'],['furniture','Furniture & storage','#c6a783'],['vehicle','Vehicles','#d5a257'],['tree','Trees & vegetation','#78955b'],['zombie','Creatures','#ab7970']];
const linked:Record<string,string[]>={door:['window'],furniture:['container','item'],tree:['vegetation'],zombie:['animal']};
for(const [kind,label,color] of layers) {
  const wrap=node('label',undefined,'layer');const checkbox=node('input');checkbox.type='checkbox';checkbox.checked=true;
  const swatch=node('span',undefined,'swatch');swatch.style.background=color;
  wrap.append(checkbox,swatch,node('span',label));$('layers').append(wrap);
  checkbox.onchange=()=>{for(const k of [kind,...(linked[kind]??[])])scene.setLayer(k,checkbox.checked);};
}
$('menu').onclick=()=>$('sidebar').classList.toggle('open');
$('close-inspector').onclick=()=>$('inspector').classList.remove('open');
for(const mode of ['3d','iso'] as const) $('mode-'+mode).onclick=()=>{
  scene.setMode(mode);
  for(const m of ['3d','iso']) {$('mode-'+m).classList.toggle('active',m===mode);$('mode-'+m).setAttribute('aria-pressed',String(m===mode));}
  scheduleScene();
};
function floor(delta:number) {
  scene.level=Math.max(-32,Math.min(64,scene.level+delta));$('floor-label').textContent=`Floor ${scene.level}`;
  const center=scene.center();scene.focus(center.x,center.y,scene.level);scheduleScene();
  if(lastWorld){scene.setPlayers(lastWorld.players);scene.setMarkers(lastWorld.markers);}
}
$('floor-down').onclick=()=>floor(-1);$('floor-up').onclick=()=>floor(1);
$('cutaway').onclick=()=>{scene.setCutaway(!scene.cutaway);$('cutaway').classList.toggle('active',scene.cutaway);$('cutaway').setAttribute('aria-pressed',String(scene.cutaway));};
$<HTMLSelectElement>('observer').onchange=()=>{
  observer=$<HTMLSelectElement>('observer').value;lastCoverageRevision=-1;
  refresh();scheduleScene();if(selected)select(selected);
};
$('search').onsubmit=e=>{
  e.preventDefault();const parts=$<HTMLInputElement>('coordinates').value.trim().split(/[,\s]+/).map(Number);
  const error=$('search-error');
  if(parts.length!==2||parts.some(v=>!Number.isFinite(v)||Math.abs(v)>200000)) {
    error.textContent='Enter two coordinates, for example 10818, 9844.';error.classList.add('visible');return;
  }
  error.classList.remove('visible');following=null;scene.focus(parts[0],parts[1]);scheduleScene();
  $('sidebar').classList.remove('open');
};

async function select(id:string,refreshOnly=false) {
  const source=observer;
  const scroll=$('inspector-body').scrollTop;
  selected=id;if(!refreshOnly)$('inspector').classList.add('open');
  try {
    const object=await get<Element>('/api/v1/elements/'+encodeURIComponent(id)+(observer?'?observer='+encodeURIComponent(observer):''));
    if(selected!==id||source!==observer)return;
    $('selection-title').textContent=object.label;
    $('selection-subtitle').textContent=object.kind.charAt(0).toUpperCase()+object.kind.slice(1);
    const body=$('inspector-body');body.replaceChildren();
    const badge=node('span',age(object.observed_at),Date.now()-object.observed_at>10000?'badge stale':'badge');body.append(badge);
    const dl=node('dl',undefined,'detail-list');
    function detail(label:string,value:string) {const row=node('div',undefined,'detail-row');row.append(node('dt',label),node('dd',value));dl.append(row);}
    detail('Position',`${object.x.toFixed(1)}, ${object.y.toFixed(1)} · Z ${object.z}`);
    detail('Observed by',object.observer);detail('Recorded',new Date(object.observed_at).toLocaleString());
    detail('Model size',`${object.width.toFixed(1)} × ${object.depth.toFixed(1)} × ${object.height.toFixed(1)}`);
    if(object.state.material)detail('Material',object.state.material);
    if(object.state.open!==undefined)detail('State',object.state.open?'Open':'Closed');
    if(object.state.broken!==undefined)detail('Damage',object.state.broken?'Broken':'Intact');
    if(object.sprite)detail('Type reference',object.sprite);
    body.append(dl);
    const focus=node('button','Center on element');focus.onclick=()=>{scene.focus(object.x,object.y,object.z);scheduleScene();};body.append(focus);
    if(object.inspections?.length) {
      for(const inspection of object.inspections) {
        body.append(node('h3',inspection.category==='container'?'Last inspected contents':'Last mechanical inspection'));
        body.append(node('p',`${inspection.slot} · ${age(inspection.observed_at)} · ${inspection.observer}`,'inspector-note'));
        for(const item of inspection.items) {
          const row=node('div',undefined,'inventory-item'),name=node('div',item.name);
          if(item.condition!==undefined&&item.condition!==null)name.append(node('small',`Condition ${item.condition}`));
          row.append(name,node('span',`×${item.count}`));body.append(row);
        }
        for(const part of inspection.parts) {const row=node('div',undefined,'inventory-item');row.append(node('span',part.name),node('span',`${Math.round(part.condition)}%`));body.append(row);}
        if(!inspection.items.length&&!inspection.parts.length)body.append(node('p','Empty when inspected.','inspector-note'));
      }
    } else if(['container','vehicle','furniture'].includes(object.kind)) {
      body.append(node('h3','Inspection'),node('p','No inspection recorded. Looking at an object does not reveal its contents or mechanical condition.','inspector-note'));
    }
    body.append(node('p','This is the last recorded observation, not a prediction of the current state.','inspector-note'));
    if(refreshOnly)body.scrollTop=scroll;
  } catch {
    if(selected!==id||source!==observer)return;
    $('selection-title').textContent='No current observation';$('selection-subtitle').textContent='This element may have been removed or is unknown to the selected observer.';$('inspector-body').replaceChildren();
  }
}

function scheduleScene() {if(sceneTimer)clearTimeout(sceneTimer);sceneTimer=setTimeout(loadScene,160);}
async function loadScene() {
  const center=scene.center();$('map-coordinates').textContent=`${Math.round(center.x)}, ${Math.round(center.y)} · Z ${scene.level}`;
  sceneRequest?.abort();const controller=new AbortController();sceneRequest=controller;
  const params=new URLSearchParams({x:String(center.x),y:String(center.y),z:String(scene.level),radius:'128'});
  if(observer)params.set('observer',observer);
  try {
    const data=await get<{objects:Element[];truncated:boolean}>('/api/v1/chunks?'+params,controller.signal);
    scene.setObjects(data.objects);$('scene-count').textContent=`${data.objects.length.toLocaleString()} elements${data.truncated?' · zoom in for detail':''}`;
    $('empty-note').classList.toggle('hidden',data.objects.length>0);
    if(lastWorld?.observed_tiles) {$('empty-note').querySelector('h2')!.textContent='No detailed observations here';$('empty-text').textContent='Move to a survivor or another recorded area. The shaded grid shows historical exploration, not captured objects.';}
  } catch(error) {if((error as Error).name!=='AbortError') {$('live-text').textContent='Connection interrupted';$('live-status').classList.remove('online');}}
}

async function refresh() {
  if(refreshBusy){refreshAgain=true;return;}refreshBusy=true;
  try {
    const world=await get<World>('/api/v1/world');lastWorld=world;
    $('live-status').classList.toggle('online',world.online);$('live-text').textContent=world.online?'Live observations':'Recording offline · last snapshot';
    $('demo-banner').classList.toggle('hidden',!world.world.toLowerCase().includes('demo'));
    const sourceSelect=$<HTMLSelectElement>('observer');
    const known=[...sourceSelect.options].slice(1).map(o=>o.value);
    if(JSON.stringify(known)!==JSON.stringify(world.observers)) {
      sourceSelect.replaceChildren(new Option('Everyone’s observations',''),...world.observers.map(o=>new Option(o,o)));sourceSelect.value=observer;
    }
    $('player-count').textContent=String(world.players.filter(p=>p.online).length);
    const players=$('players');players.replaceChildren();
    if(!world.players.length)players.append(node('p','No player positions recorded yet.','subtitle'));
    for(const p of world.players) {
      const button=node('button',undefined,'player'),info=node('span',undefined,'player-info');
      const avatar=node('span',p.name.slice(0,2).toUpperCase(),'avatar');
      info.append(node('span',p.name,'player-name'),node('span',`${Math.round(p.x)}, ${Math.round(p.y)} · ${p.online?'online':'last seen'}`,'player-position'));
      button.append(avatar,info,node('span',following===p.name?'●':'↗'));
      button.onclick=()=>{following=following===p.name?null:p.name;scene.level=p.z;$('floor-label').textContent=`Floor ${p.z}`;scene.focus(p.x,p.y,p.z);scheduleScene();$('sidebar').classList.remove('open');};players.append(button);
    }
    if(following) {const p=world.players.find(p=>p.name===following&&p.online);if(p){scene.focus(p.x,p.y,p.z);if(scene.level!==p.z){scene.level=p.z;$('floor-label').textContent=`Floor ${p.z}`;}scheduleScene();}}
    scene.setPlayers(world.players);scene.setMarkers(world.markers);
    const markers=$('markers');markers.replaceChildren();$('marker-count').textContent=String(world.markers.length);
    for(const m of world.markers) {
      const button=node('button','⌖ '+m.label,'marker');button.append(node('small',m.author));
      button.onclick=()=>{following=null;scene.level=m.z;$('floor-label').textContent=`Floor ${m.z}`;scene.focus(m.x,m.y,m.z);scene.setMarkers(world.markers);scene.setPlayers(world.players);scheduleScene();};markers.append(button);
    }
    if(!world.markers.length)markers.append(node('p','No publicly shared markers.','subtitle'));
    const warning=$('source-warning');const message=world.collector_error?.message??(world.gap?'A recording gap was detected. Some observations may be missing.':'');
    warning.textContent=message;warning.classList.toggle('visible',!!message);
    if(lastCoverageRevision!==world.coverage_revision) {
      const coverage=await get<{cells:number[][]}>('/api/v1/coverage'+(observer?'?observer='+encodeURIComponent(observer):''));
      scene.setCoverage(coverage.cells);lastCoverageRevision=world.coverage_revision;
      $('coverage-note').textContent=`${coverage.cells.length.toLocaleString()} known map blocks · ${world.observed_tiles.toLocaleString()} detailed tiles recorded.`;
    }
    if(lastSceneRevision!==world.scene_revision){lastSceneRevision=world.scene_revision;scheduleScene();}
  } catch {$('live-text').textContent='Connection interrupted';$('live-status').classList.remove('online');}
  finally {refreshBusy=false;if(refreshAgain){refreshAgain=false;void refresh();}}
}
const events=new EventSource('/api/v1/events');events.addEventListener('revision',()=>void refresh());
events.onerror=()=>{$('live-text').textContent='Reconnecting · last snapshot';$('live-status').classList.remove('online');};
setInterval(()=>{void refresh();scheduleScene();if(selected)void select(selected,true);},5000);void refresh();scheduleScene();
window.addEventListener('beforeunload',()=>{events.close();scene.dispose();});
// Read-only diagnostics for performance verification; no control or ingestion interface.
Object.defineProperty(window,'observerDiagnostics',{value:()=>scene.stats(),writable:false});
