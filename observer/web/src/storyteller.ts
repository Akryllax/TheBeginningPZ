import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import './storyteller.css';

type Cell={x:number;y:number;z:number;dwell:number;wealth:number;confidence:number;age_hours:number};
type Decision={id:string;event:string;outcome:string;reason:string;at_hour:number};
type Scenario={status:string;phase:string;elapsed_hours:number;residents:number;materialized:number;pedestrians:number;vehicles:number;worker_health:string;worker_compute_ms:number;plan_rejections:number;lease_changes:number;worker_queue:number|null;last_step_ms:number;p95_step_ms:number;p99_step_ms:number};
type Snapshot={world:string;tick:number;world_age_hours:number;phase:string;mode:string;pressure:number;budget:number;online_players:number;npc_count:number|null;timings_ms:{last:number;p95:number;p99:number;max:number};cells:Cell[];decisions:Decision[];cell_count:number;scan_queue:number;dropped_cells:number;health:string;truncated:boolean;capture_ms:number;captured_at:number;scenario?:Scenario|null};
type Feed={status:'waiting'|'stale'|'live';snapshot:Snapshot|null;stale_after_ms:number};
type Metric='dwell'|'wealth'|'confidence'|'age_hours';
const app=document.querySelector('#app')!;
app.innerHTML=`<div class="storyteller-app"><header><div><h1>Living world · diagnostics</h1><p>Local read-only view · <a href="/">Observer map</a></p></div><span id="feed-status" role="status">Connecting…</span></header><main><aside><p id="feed-detail">Waiting for a published storyteller sample.</p><dl id="story-metrics"></dl><section><h2>Spatial field</h2><label for="field-metric">Color by</label><select id="field-metric"><option value="dwell">Dwell</option><option value="wealth">Observed wealth</option><option value="confidence">Confidence</option><option value="age_hours">Observation age</option></select><label for="field-floor">Floor</label><select id="field-floor"><option value="0">0</option></select><button id="fit-field">Fit observed cells</button><p id="field-note"></p><p>32 × 32 tile cells. Wealth is an estimate of observed stock; missing or stale observations are not proof of an empty base. Terrain keeps the shared map's fog.</p></section><section><h2>Recent decisions</h2><ol id="story-decisions"></ol></section></aside><div id="story-map" aria-label="Storyteller diagnostic map"></div></main></div>`;
const $=<T extends HTMLElement=HTMLElement>(id:string)=>document.getElementById(id)! as T;
const map=L.map('story-map',{crs:L.CRS.Simple,minZoom:-4,maxZoom:3,preferCanvas:true}).setView([-9913,10632],0);
map.attributionControl.setPrefix(false);
map.attributionControl.addAttribution('Project Zomboid · shared fog · diagnostic cells');
const terrain=L.tileLayer('/api/v1/map/tiles/{z}/{x}/{y}.png',{tileSize:256,minZoom:-4,maxZoom:3,noWrap:true}).addTo(map);
const field=L.layerGroup().addTo(map);
let current:Snapshot|null=null,first=true,lastTerrain=0;
function text(tag:string,value:string){const el=document.createElement(tag);el.textContent=value;return el;}
function n(value:number){return value.toLocaleString(undefined,{maximumFractionDigits:2});}
function fieldCells(){const floor=Number($<HTMLSelectElement>('field-floor').value);return (current?.cells||[]).filter(c=>c.z===floor);}
function fit(){const cells=fieldCells();if(cells.length)map.fitBounds(cells.flatMap(c=>[[-c.y,c.x],[-c.y-32,c.x+32]] as L.LatLngTuple[]),{padding:[40,40],maxZoom:1});}
function renderField(){
 field.clearLayers();const metric=$<HTMLSelectElement>('field-metric').value as Metric,cells=fieldCells();
 const max=Math.max(1,...cells.map(c=>c[metric]));
 for(const c of cells){const ratio=metric==='confidence'?c.confidence:Math.log1p(c[metric])/Math.log1p(max);const color=`hsl(${Math.round(170-150*ratio)} 76% 54%)`;const details=text('div',`Cell ${c.x}, ${c.y} · floor ${c.z}`);details.append(text('p',`Dwell ${n(c.dwell)} · wealth ${n(c.wealth)} · confidence ${n(c.confidence*100)}% · age ${n(c.age_hours)} h`));L.rectangle([[-c.y,c.x],[-c.y-32,c.x+32]],{color,weight:1,fillColor:color,fillOpacity:.22+.43*c.confidence,dashArray:c.confidence<.5?'4 4':undefined}).bindPopup(details).addTo(field);}
 $('field-note').textContent=current?`${cells.length} cells shown on this floor · ${current.cells.length} in preview / ${current.cell_count} tracked · ${current.truncated?'exporter time budget reached; partial preview':'bounded preview'}.`:'No observed cells yet.';
}
function render(feed:Feed){
 const s=feed.snapshot;current=s;
 $('feed-status').textContent=feed.status==='live'?'Receiving diagnostics':feed.status==='stale'?'Stale · last sample retained':'Waiting for storyteller';
 $('feed-status').dataset.status=feed.status;
 $('feed-detail').textContent=s?`${s.world} · updated ${new Date(s.captured_at).toLocaleString()} · ${s.health||'No health note'}. ${feed.status==='stale'?'The game may be paused, disconnected, or no longer publishing.':''}`:'Enable the storyteller mod and optional exporter to see observations. No gameplay controls are exposed here.';
 if(!s)return;
 const metrics:[string,string][]=[['Mode',s.mode==='observe'?'Observation only':'Active'],['Phase',s.phase],['World age',`${n(s.world_age_hours)} h`],['Pressure',n(s.pressure)],['Budget',n(s.budget)],['Online players',String(s.online_players)],['NPCs',s.npc_count===null?'Unknown':String(s.npc_count)],['Scan queue',String(s.scan_queue)],['Dropped cells',String(s.dropped_cells)],['Update last / p95 / p99',`${n(s.timings_ms.last)} / ${n(s.timings_ms.p95)} / ${n(s.timings_ms.p99)} ms`],['Update maximum',`${n(s.timings_ms.max)} ms`],['Exporter capture',`${n(s.capture_ms)} ms`]];
 if(s.scenario){const v=s.scenario;metrics.unshift(['First Week',`${v.status} · ${v.phase}`],['Outbreak clock',`${n(v.elapsed_hours)} / 168 h`],['Residents / physical',`${v.residents} / ${v.materialized}`],['Pedestrians / moving cars',`${v.pedestrians} / ${v.vehicles}`],['AI worker',v.worker_health],['Worker computation',`${n(v.worker_compute_ms)} ms`],['Worker queue',v.worker_queue===null?'Not measured':String(v.worker_queue)],['Rejected plans / handoffs',`${v.plan_rejections} / ${v.lease_changes}`],['Scenario last / p95 / p99',`${n(v.last_step_ms)} / ${n(v.p95_step_ms)} / ${n(v.p99_step_ms)} ms`]);}
 $('story-metrics').replaceChildren(...metrics.flatMap(([label,value])=>[text('dt',label),text('dd',value)]));
 const floors=$<HTMLSelectElement>('field-floor'),previous=floors.value,values=[...new Set([0,...s.cells.map(c=>c.z)])].sort((a,b)=>a-b);
 if(values.join(',')!==[...floors.options].map(o=>o.value).join(',')){floors.replaceChildren(...values.map(v=>new Option(String(v),String(v))));floors.value=values.includes(Number(previous))?previous:'0';}
 $('story-decisions').replaceChildren(...[...s.decisions].reverse().map(d=>{const row=text('li','');row.append(text('strong',`${d.event} · ${d.outcome}`),text('p',d.reason),text('small',`Hour ${n(d.at_hour)} · ${d.id}`));return row;}));
 renderField();if(first&&s.cells.length){fit();first=false;}
 if(Date.now()-lastTerrain>30000){terrain.redraw();lastTerrain=Date.now();}
}
async function poll(){try{const r=await fetch('/debug/storyteller/snapshot',{signal:AbortSignal.timeout(5000)});if(!r.ok)throw new Error(String(r.status));render(await r.json());}catch{$('feed-status').textContent='Diagnostics unavailable';$('feed-status').dataset.status='stale';$('feed-detail').textContent='The last received preview remains visible. This view requires the local debug endpoint.';}finally{setTimeout(()=>void poll(),5000);}}
$('field-metric').onchange=renderField;$('field-floor').onchange=renderField;$('fit-field').onclick=fit;
void poll();
