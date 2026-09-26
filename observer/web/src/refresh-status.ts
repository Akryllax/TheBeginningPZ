type Source={modified_at:number|null;checked_at:number;error:string|null};
export type PositionFeed={enabled:boolean;status:'disabled'|'waiting'|'live'|'stale';received_at:number|null;captured_at:number|null;stale_after_ms:number;interval_ms:number;online_count:number|null};
export type ExplorationFeed=Omit<PositionFeed,'online_count'>;
type Snapshot={sources:Record<string,Source>;indexing:boolean;index_error:string|null;position_feed?:PositionFeed;exploration_feed?:ExplorationFeed};

/** Countdown is for this page's refresh, not a promise that the game will save. */
export function enableRefreshStatus(host:HTMLElement,refresh:()=>void){
  host.innerHTML=`<div class="map-update-info"><span id="map-position-status">Saved positions</span><span id="map-exploration-status">Waiting for exploration…</span><span id="map-save-age">Waiting for saved data…</span><span id="map-check-age">Not checked yet</span><span id="map-update-warning" role="status"></span></div><span id="map-refresh-countdown">Connecting…</span><progress id="map-refresh-progress" max="30" value="0" aria-label="Time until the next map refresh"></progress>`;
  const positionStatus=host.querySelector<HTMLElement>('#map-position-status')!;
  const explorationStatus=host.querySelector<HTMLElement>('#map-exploration-status')!;
  const saved=host.querySelector<HTMLElement>('#map-save-age')!;
  const checked=host.querySelector<HTMLElement>('#map-check-age')!;
  const warning=host.querySelector<HTMLElement>('#map-update-warning')!;
  const countdown=host.querySelector<HTMLElement>('#map-refresh-countdown')!;
  const progress=host.querySelector<HTMLProgressElement>('#map-refresh-progress')!;
  const interval=30000;
  let next=performance.now()+interval,snapshot:Snapshot|null=null,busy=false,failed=false,feed:PositionFeed|undefined,explorationFeed:ExplorationFeed|undefined;
  function age(at:number){
    const seconds=Math.max(0,Math.floor((Date.now()-at)/1000));
    if(seconds<60)return `${seconds}s ago`;
    if(seconds<3600)return `${Math.floor(seconds/60)}m ago`;
    if(seconds<86400)return `${Math.floor(seconds/3600)}h ago`;
    return `${Math.floor(seconds/86400)}d ago`;
  }
  function paint(){
    const live=feed?.status==='live'&&feed.received_at!==null&&Date.now()-feed.received_at<feed.stale_after_ms;
    positionStatus.textContent=!feed?.enabled?'Saved positions':live?`Live positions · ${feed.online_count} online`:feed.received_at?`Positions delayed · last ${age(feed.captured_at||feed.received_at)}`:'Waiting for live positions';
    positionStatus.classList.toggle('positions-live',Boolean(live));
    const explorationLive=explorationFeed?.status==='live'&&explorationFeed.received_at!==null&&Date.now()-explorationFeed.received_at<explorationFeed.stale_after_ms;
    const explorationSaved=Math.max(0,...Object.entries(snapshot?.sources||{}).filter(([name])=>name.startsWith('coverage:')).map(([,s])=>s.modified_at||0));
    explorationStatus.textContent=explorationLive?'Live exploration':explorationFeed?.received_at?`Exploration delayed · last ${age(explorationFeed.captured_at||explorationFeed.received_at)}`:explorationSaved?`Exploration saved ${age(explorationSaved)}`:'Waiting for exploration…';
    explorationStatus.title=explorationLive?'Exact explored and learned areas from the game. One player updated every two seconds.':explorationFeed?.received_at?'Live exploration is delayed. Retaining known areas and continuing to check saved map files.':explorationSaved?`Latest exploration-file write: ${new Date(explorationSaved).toLocaleString()}. Exploration files update on world saves or logout.`:'No exploration snapshot is available.';
    explorationStatus.classList.toggle('positions-live',Boolean(explorationLive));
    const remaining=Math.max(0,Math.ceil((next-performance.now())/1000));
    if(snapshot){
      const sources=Object.values(snapshot.sources);
      const modified=Math.max(0,...Object.entries(snapshot.sources).filter(([name])=>name!=='deaths').map(([,s])=>s.modified_at||0));
      const lastCheck=Math.max(0,...sources.map(s=>s.checked_at||0));
      saved.textContent=modified?`Saved ${age(modified)}`:'No saved data yet';
      saved.title=modified?`Latest source file update: ${new Date(modified).toLocaleString()}. Other sources may be older; see Source Status.`:'No saved source timestamp is available.';
      checked.textContent=lastCheck?`Checked ${age(lastCheck)}`:'Not checked yet';
      checked.title=lastCheck?`Most recent source-file check: ${new Date(lastCheck).toLocaleString()}. Checking does not force a game save.`:'The collector has not checked a source yet.';
      const errors=sources.filter(s=>s.error).length+(snapshot.index_error?1:0);
      warning.textContent=failed?'Connection interrupted':errors?`${errors} source ${errors===1?'error':'errors'}`:snapshot.indexing?'Indexing map…':'';
    }else warning.textContent=failed?'Connection interrupted':'';
    host.classList.toggle('has-update-error',failed||Boolean(snapshot&&Object.values(snapshot.sources).some(s=>s.error))||Boolean(snapshot?.index_error));
    countdown.textContent=busy?'Refreshing…':`${failed?'Retry':'Refresh'} in ${remaining}s`;
    if(busy)progress.removeAttribute('value');
    else progress.value=30-remaining;
    progress.setAttribute('aria-valuetext',busy?'Refreshing saved map data':`${remaining} seconds until the next refresh`);
  }
  const timer=setInterval(()=>{
    if(performance.now()>=next){next=performance.now()+interval;refresh();}
    paint();
  },250);
  paint();
  return {
    begin(){busy=true;paint();},
    received(value:Snapshot){snapshot=value;feed=value.position_feed;if(!explorationFeed||(value.exploration_feed?.received_at||0)>=(explorationFeed.received_at||0))explorationFeed=value.exploration_feed;failed=false;paint();},
    positions(value:PositionFeed|undefined){feed=value;paint();},
    exploration(value:ExplorationFeed){explorationFeed=value;paint();},
    failed(){failed=true;paint();},
    end(){busy=false;paint();},
    dispose(){clearInterval(timer);},
  };
}
