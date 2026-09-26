import L from 'leaflet';
import type {PositionFeed} from './refresh-status';

type FollowPlayer={name:string;x:number;y:number;source?:'live'|'last_seen'|'saved';online?:boolean|null};

/** Camera preference belongs to this viewer, independent of shared trip tracking. */
export function enableCameraFollow(map:L.Map,host:HTMLElement,changed:()=>void){
  host.innerHTML='<span id="camera-follow-name"></span><span id="camera-follow-source"></span><button id="camera-follow-stop" type="button" aria-label="Stop following player">Stop</button>';
  const name=host.querySelector<HTMLElement>('#camera-follow-name')!;
  const source=host.querySelector<HTMLElement>('#camera-follow-source')!;
  const reducedMotion=window.matchMedia('(prefers-reduced-motion: reduce)');
  let selected:string|null=null,players:FollowPlayer[]=[],feed:PositionFeed|undefined;
  let moving=false,zooming=false,lastPosition='';

  function paint(){
    host.hidden=selected===null;
    if(selected===null)return;
    const player=players.find(p=>p.name===selected);
    name.textContent=`Following ${selected}`;name.title=selected;
    source.textContent=player?.online===false?'Offline · last position':
      feed?.enabled&&feed.status!=='live'?'Waiting for live positions':
      player?.source==='live'?'Live':player?.source==='last_seen'?'Last seen':'Saved position';
  }
  function center(force=false){
    const player=players.find(p=>p.name===selected);
    if(!player||zooming)return;
    const key=`${player.x},${player.y}`;
    if(!force&&key===lastPosition)return;
    lastPosition=key;
    const target=L.latLng(-player.y,player.x);
    if(map.latLngToContainerPoint(target).distanceTo(map.getSize().divideBy(2))<.5)return;
    moving=true;
    try{map.panTo(target,{animate:!reducedMotion.matches,duration:.3});}
    finally{moving=false;}
  }
  function stop(){
    if(selected===null)return;
    selected=null;lastPosition='';paint();changed();
    // Stop an in-flight follow pan as well, so a manual navigation wins.
    moving=true;try{map.stop();}finally{moving=false;}
  }
  const manualMove=()=>{if(!moving&&!zooming)stop();};
  const zoomStart=()=>{zooming=true;};
  const zoomEnd=()=>{zooming=false;center(true);};
  const resize=()=>center(true);
  map.on('dragstart boxzoomstart',stop).on('movestart',manualMove);
  map.on('zoomstart',zoomStart).on('zoomend',zoomEnd).on('resize',resize);
  host.querySelector<HTMLButtonElement>('button')!.onclick=stop;

  return {
    get selected(){return selected;},
    toggle(playerName:string){
      if(playerName===selected){stop();return;}
      if(!players.some(p=>p.name===playerName))return;
      selected=playerName;lastPosition='';paint();changed();center(true);
    },
    update(next:FollowPlayer[],positionFeed:PositionFeed|undefined){
      players=next;feed=positionFeed;
      if(selected&&!players.some(p=>p.name===selected)){stop();return;}
      paint();center();
    },
    stop,
    dispose(){
      map.off('dragstart boxzoomstart',stop).off('movestart',manualMove);
      map.off('zoomstart',zoomStart).off('zoomend',zoomEnd).off('resize',resize);
    },
  };
}
