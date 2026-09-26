import type {TripStop} from './planning';

export type RoadSegment={from:string;to:string;kind:'road'|'access';points:[number,number][]};
export type Routing={observer:string|null;auto_reroute:boolean;request_id?:string;status?:string;message?:string;geometry?:RoadSegment[];road_distance?:number;access_distance?:number};
export type GPSContext={observer:string|null;players:{name:string;x:number;y:number;z:number;source?:string;online?:boolean|null}[];selectedPlace:TripStop|null};
type Result=Routing&{status:string;message:string;stops?:TripStop[]};
type Settings={stops:TripStop[];routing:Routing|null;follow_player:string|null};
const clone=<T>(v:T):T=>JSON.parse(JSON.stringify(v));
const point=(p:{label:string;x:number;y:number;z?:number}):TripStop=>({label:p.label,x:p.x,y:p.y,z:Math.floor(p.z||0),kind:'manual'});

export function enableGPS(host:HTMLElement,context:()=>GPSContext,reference:()=>TripStop,get:()=>Settings,callbacks:{
  arm:(which:'from'|'to')=>void;apply:(stops:TripStop[],routing:Routing)=>void;auto:(on:boolean)=>void;recalculate:()=>void;
  id:()=>string;
}){
  host.innerHTML=`<div class="gps-helper plan-edit"><div class="small-label">ROAD ROUTE HELPER</div>
    <label class="small-label" for="gps-from">From</label><div class="gps-endpoint"><input id="gps-from" autocomplete="off" placeholder="Player, city, place or X, Y"/><button type="button" id="gps-from-map" aria-label="Pick From on map">Map</button></div>
    <div id="gps-from-results" class="gps-results"></div>
    <label class="small-label" for="gps-to">To</label><div class="gps-endpoint"><input id="gps-to" autocomplete="off" placeholder="Player, city, place or X, Y"/><button type="button" id="gps-to-map" aria-label="Pick To on map">Map</button></div>
    <div id="gps-to-results" class="gps-results"></div>
    <div class="trip-actions"><button type="button" id="gps-reference">From reference</button><button type="button" id="gps-selection">To selected place</button><button type="button" id="gps-fill">Fill route</button><button type="button" id="gps-cancel" hidden>Cancel</button></div>
    <p id="gps-knowledge" class="muted"></p></div>
    <label class="trip-visibility"><input type="checkbox" id="gps-auto" checked/> Auto reroute</label>
    <button type="button" class="plan-edit" id="gps-recalculate" hidden>Recalculate remaining route</button>
    <p id="gps-status" class="muted" role="status">Fill a route through known roads; road bends become editable checkpoints.</p>`;
  const $=<T extends HTMLElement>(id:string)=>host.querySelector<T>('#'+id)!;
  const fields={from:$<HTMLInputElement>('gps-from'),to:$<HTMLInputElement>('gps-to')};
  const ends:{from:TripStop|null;to:TripStop|null}={from:null,to:null};
  let cities:{label:string;x:number;y:number}[]=[],editing=false,busy=false,generation=0,dirty=0;
  let searchTimer:ReturnType<typeof setTimeout>|null=null,controller:AbortController|null=null,calculation:AbortController|null=null;
  let defaultAuto=true,lastTrip='',lastNotice='';
  const note=$<HTMLParagraphElement>('gps-status');
  function set(which:'from'|'to',p:TripStop){ends[which]={...p,id:callbacks.id(),kind:'manual'};fields[which].value=p.label;$(`gps-${which}-results`).replaceChildren();dirty++;}
  function parse(which:'from'|'to'){
    if(ends[which])return ends[which];
    const text=fields[which].value.trim(),match=text.match(/^(-?\d+(?:\.\d+)?)\s*[, ]\s*(-?\d+(?:\.\d+)?)$/);
    if(match){const x=Number(match[1]),y=Number(match[2]);if(Math.abs(x)<=200000&&Math.abs(y)<=200000){set(which,point({label:`${x}, ${y}`,x,y}));return ends[which];}}
    const options=[...cities.map(p=>point(p)),...context().players.map(p=>point({...p,label:p.name}))];
    const found=options.find(p=>p.label.toLowerCase()===text.toLowerCase());if(found){set(which,found);return ends[which];}
    return null;
  }
  function options(which:'from'|'to',matches:TripStop[]){
    const results=$(`gps-${which}-results`);results.replaceChildren();
    for(const p of matches.slice(0,10)){const button=document.createElement('button');button.type='button';button.textContent=`${p.label} · ${Math.round(p.x)}, ${Math.round(p.y)}`;button.onclick=()=>set(which,p);results.append(button);}
  }
  async function search(which:'from'|'to'){
    const q=fields[which].value.trim();controller?.abort();const active=controller=new AbortController();
    const ctx=context(),needle=q.toLowerCase();
    const matches=[...ctx.players.filter(p=>p.name.toLowerCase().includes(needle)).map(p=>point({...p,label:p.name})),...cities.filter(p=>p.label.toLowerCase().includes(needle)).map(p=>point(p))];
    options(which,matches);if(q.length<2||/^[\d\s,.-]+$/.test(q))return;
    const origin=ends.from||reference();
    try{
      const params=new URLSearchParams({q,x:String(origin.x),y:String(origin.y)});if(ctx.observer)params.set('observer',ctx.observer);
      const response=await fetch('/api/v1/places/search?'+params,{signal:active.signal});if(!response.ok)return;
      const data=await response.json();if(!active.signal.aborted&&fields[which].value.trim()===q)options(which,[...matches,...data.results.map((p:any)=>point(p))]);
    }catch{/* Existing city/player suggestions remain usable during search outages. */}
  }
  for(const which of ['from','to'] as const){
    fields[which].oninput=()=>{ends[which]=null;dirty++;if(searchTimer)clearTimeout(searchTimer);searchTimer=setTimeout(()=>void search(which),200);};
    fields[which].onkeydown=e=>{if(e.key==='Enter'){e.preventDefault();if(!parse(which))void search(which);}};
    $<HTMLButtonElement>(`gps-${which}-map`).onclick=()=>callbacks.arm(which);
  }
  $<HTMLButtonElement>('gps-reference').onclick=()=>set('from',reference());
  $<HTMLButtonElement>('gps-selection').onclick=()=>{const p=context().selectedPlace;if(p)set('to',p);};
  $<HTMLInputElement>('gps-auto').onchange=()=>{defaultAuto=$<HTMLInputElement>('gps-auto').checked;if(get().routing)callbacks.auto(defaultAuto);};
  $<HTMLButtonElement>('gps-recalculate').onclick=callbacks.recalculate;
  function cancel(){generation++;calculation?.abort();busy=false;lastNotice='Calculation cancelled; current trip kept.';note.textContent=lastNotice;render(lastTrip,editing);}
  $<HTMLButtonElement>('gps-cancel').onclick=cancel;
  async function fill(){
    const from=parse('from'),to=parse('to');if(!from||!to){lastNotice='Choose both endpoints from suggestions, coordinates or the map.';note.textContent=lastNotice;return;}
    const mine=++generation,serial=dirty,ctx=context(),trip=get(),signature=JSON.stringify(trip.stops);
    calculation?.abort();const active=calculation=new AbortController();busy=true;lastNotice='Calculating known roads…';note.textContent=lastNotice;render(lastTrip,editing);
    const payload={stops:[clone(from),clone(to)],observer:ctx.observer};
    try{
      let result:Result;
      for(let retry=0;;retry++){
        const response=await fetch('/api/v1/routes',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload),signal:active.signal});
        const data=await response.json();if(!response.ok)throw new Error(data.detail||'Route calculation failed.');result=data;
        if(mine!==generation)return;
        lastNotice=result.message;note.textContent=lastNotice;
        if(!['preparing','busy'].includes(result.status))break;
        if(retry>=90)throw new Error('Road preparation is taking longer than expected. Try again shortly; the current trip is kept.');
        await new Promise<void>((resolve,reject)=>{const abort=()=>{clearTimeout(timer);reject(new DOMException('Cancelled','AbortError'));};const timer=setTimeout(()=>{active.signal.removeEventListener('abort',abort);resolve();},1000);active.signal.addEventListener('abort',abort,{once:true});});
      }
      if(result.status!=='ready'||!result.stops)return;
      if(!editing||serial!==dirty||signature!==JSON.stringify(get().stops)||ctx.observer!==context().observer){lastNotice='The endpoints, trip or map selection changed. Fill the route again.';note.textContent=lastNotice;return;}
      callbacks.apply(result.stops,{...result,observer:ctx.observer,auto_reroute:$<HTMLInputElement>('gps-auto').checked});
      lastNotice=`Filled ${result.stops.length} checkpoints · bends can be moved or removed.`;note.textContent=lastNotice;
    }catch(e){if((e as Error).name!=='AbortError'){lastNotice=(e as Error).message;note.textContent=lastNotice;}}
    finally{if(mine===generation){busy=false;render(lastTrip,editing);}}
  }
  $<HTMLButtonElement>('gps-fill').onclick=()=>void fill();
  function render(id:string,isEditing:boolean){
    editing=isEditing;const draft=get(),ctx=context();
    if(lastTrip!==id){lastTrip=id;lastNotice='';defaultAuto=true;ends.from=ends.to=null;fields.from.value=fields.to.value='';}
    if(editing&&!fields.from.value){
      const player=ctx.players.find(p=>p.name===draft.follow_player&&p.source==='live'&&p.online);
      set('from',player?point({...player,label:player.name}):draft.stops[0]||reference());
    }
    if(editing&&!fields.to.value){const end=draft.stops.length>1?draft.stops.at(-1):ctx.selectedPlace;if(end)set('to',end);}
    for(const field of Object.values(fields))field.disabled=!editing||busy;
    for(const id of ['gps-fill','gps-from-map','gps-to-map','gps-reference'])$<HTMLButtonElement>(id).disabled=!editing||busy;
    $<HTMLButtonElement>('gps-selection').disabled=!editing||busy||!ctx.selectedPlace;
    $<HTMLButtonElement>('gps-fill').textContent=draft.stops.length?'Replace route':'Fill route';
    $<HTMLButtonElement>('gps-cancel').hidden=!busy;
    $<HTMLInputElement>('gps-auto').checked=draft.routing?.auto_reroute??defaultAuto;
    $<HTMLInputElement>('gps-auto').disabled=!editing||busy;
    $<HTMLButtonElement>('gps-recalculate').hidden=!draft.routing;
    $<HTMLButtonElement>('gps-recalculate').disabled=!editing||busy;
    $('gps-knowledge').textContent=`Generate using ${ctx.observer||"everyone’s"} known map · prefer paved roads`;
    if(!busy){note.textContent=lastNotice|| (draft.routing?`${draft.routing.message||'Road route'} · using ${draft.routing.observer||"everyone’s"} knowledge. Dashed amber stretches are unverified access.`:'Fill a route through known roads; road bends become editable checkpoints.');}
  }
  return {render,set,updateCities(value:typeof cities){cities=value;},notice(){lastNotice='';},dispose(){generation++;controller?.abort();calculation?.abort();if(searchTimer)clearTimeout(searchTimer);}};
}

/** Clip road lines at knowledge-block boundaries before creating Leaflet layers. */
export function knownParts(points:[number,number][],known:Set<string>):[number,number][][]{
  const output:[number,number][][]=[];
  for(let i=1;i<points.length;i++){
    const a=points[i-1],b=points[i],dx=b[0]-a[0],dy=b[1]-a[1],cuts=[0,1];
    for(let axis=0;axis<2;axis++){
      const d=axis?dy:dx;if(!d)continue;
      const low=Math.min(a[axis],b[axis]),high=Math.max(a[axis],b[axis]);
      for(let v=Math.floor(low/32)*32+32;v<high;v+=32)cuts.push((v-a[axis])/d);
    }
    cuts.sort((x,y)=>x-y);
    for(let j=1;j<cuts.length;j++){
      const t0=cuts[j-1],t1=cuts[j];if(t1-t0<1e-9)continue;
      const m=(t0+t1)/2,x=a[0]+dx*m,y=a[1]+dy*m;
      if(!known.has(`${Math.floor(x/32)*32},${Math.floor(y/32)*32}`))continue;
      const start:[number,number]=[a[0]+dx*t0,a[1]+dy*t0],end:[number,number]=[a[0]+dx*t1,a[1]+dy*t1];
      const last=output.at(-1),tail=last?.at(-1);
      if(tail&&Math.hypot(tail[0]-start[0],tail[1]-start[1])<1e-7)last!.push(end);else output.push([start,end]);
    }
  }
  return output;
}
