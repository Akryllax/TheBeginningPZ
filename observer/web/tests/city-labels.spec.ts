import {test,expect,type Page} from '@playwright/test';

const url=process.env.SAVED_MAP_URL||'http://127.0.0.1:8766';
const city={id:'MapLabel_Muldraugh',label:'Muldraugh',x:10768,y:9904};

async function fixture(page:Page){
  let expanded=false,revision=1;
  // Capture the real event source so the test can deliver an exploration
  // revision without writing to a game or waiting for the 30-second poll.
  await page.addInitScript(()=>{
    const Native=window.EventSource;
    (window as any).cityTestStreams=[];
    window.EventSource=class extends Native{
      constructor(input:string|URL,options?:EventSourceInit){super(input,options);(window as any).cityTestStreams.push(this);}
    };
  });
  await page.route('**/api/v1/world',route=>route.fulfill({json:{mode:'saved_map',world:'City label fixture',
    revision,coverage_revision:revision,terrain_revision:1,indexing:false,index_error:null,
    observers:['akryllax','eric'],players:[{id:1,name:'akryllax',character:'Tommy',x:city.x,y:city.y,z:0}],
    markers:[],deaths:[],death_markers_since:null,sources:{}}}));
  await page.route('**/api/v1/coverage*',async route=>{
    const observer=new URL(route.request().url()).searchParams.get('observer');
    const cells:number[][]=[];
    if(observer!=='eric'){
      if(expanded)for(let x=city.x-240;x<=city.x+240;x+=32)for(let y=city.y-80;y<=city.y+80;y+=32)cells.push([x,y,3]);
      else cells.push([10752,9888,3]);
    }
    await route.fulfill({json:{revision,unit_size:32,cells,city_labels:cells.length?[city]:[]}});
  });
  await page.route('**/api/v1/map/features?*',route=>route.fulfill({json:{vehicles:[]}}));
  await page.goto(url);
  await expect(page.locator('.city-label svg')).toHaveAttribute('aria-label','Muldraugh');
  await expect(page.locator('.leaflet-pan-anim,.leaflet-zoom-anim')).toHaveCount(0);
  return async()=>{
    expanded=true;revision++;
    await page.evaluate(()=>{
      const stream=(window as any).cityTestStreams.find((s:EventSource)=>s.url.endsWith('/api/v1/events'));
      stream.dispatchEvent(new MessageEvent('revision',{data:'[]'}));
    });
  };
}

async function paintedPixels(page:Page){
  return page.locator('.city-label svg').evaluate(async element=>{
    const svg=element as SVGSVGElement;
    const copy=svg.cloneNode(true) as SVGSVGElement;
    const original=svg.querySelector('text')!,text=copy.querySelector('text')!;
    const style=getComputedStyle(original);
    for(const property of ['font-family','font-weight','letter-spacing','fill','stroke','stroke-width','stroke-linejoin','paint-order','opacity']){
      text.style.setProperty(property,style.getPropertyValue(property));
    }
    const image=new Image();
    image.src='data:image/svg+xml;charset=utf-8,'+encodeURIComponent(new XMLSerializer().serializeToString(copy));
    await image.decode();
    const canvas=document.createElement('canvas');canvas.width=image.width;canvas.height=image.height;
    const ctx=canvas.getContext('2d')!;ctx.drawImage(image,0,0);
    const pixels=ctx.getImageData(0,0,canvas.width,canvas.height).data;
    const rects=[...copy.querySelectorAll('clipPath rect')].map(r=>['x','y','width','height'].map(k=>Number(r.getAttribute(k))));
    let painted=0,outside=0;
    for(let y=0;y<canvas.height;y++)for(let x=0;x<canvas.width;x++)if(pixels[(y*canvas.width+x)*4+3]){
      painted++;
      if(!rects.some(([rx,ry,w,h])=>x+1>rx&&x<rx+w&&y+1>ry&&y<ry+h))outside++;
    }
    return {painted,outside};
  });
}

test('city text obeys fog, updates without panning, and follows layer and observer selection',async({page})=>{
  const errors:string[]=[];page.on('pageerror',e=>errors.push(e.message));
  const expand=await fixture(page);
  const before=await paintedPixels(page);
  expect(before.painted).toBeGreaterThan(0);expect(before.outside).toBe(0);
  await page.screenshot({path:'../artifacts/city-labels/fog-boundary.png'});
  const pane=await page.locator('.leaflet-map-pane').getAttribute('style');
  await expand();
  await expect.poll(()=>page.locator('.city-label clipPath rect').count()).toBeGreaterThan(1);
  const after=await paintedPixels(page);
  expect(after.painted).toBeGreaterThan(before.painted*2);expect(after.outside).toBe(0);
  expect(await page.locator('.leaflet-map-pane').getAttribute('style')).toBe(pane);
  await page.locator('#show-cities').uncheck();await expect(page.locator('.city-label')).toHaveCount(0);
  await page.locator('#show-cities').check();await expect(page.locator('.city-label')).toHaveCount(1);
  await page.locator('#coverage-source').selectOption('eric');await expect(page.locator('.city-label')).toHaveCount(0);
  await expect(page.locator('#known-count')).toContainText('0 known blocks');
  await page.locator('#coverage-source').selectOption('akryllax');await expect(page.locator('.city-label')).toHaveCount(1);
  await page.locator('.leaflet-control-zoom-out').click();
  await expect(page.locator('.city-label-text')).toHaveAttribute('font-size','22');
  expect((await paintedPixels(page)).outside).toBe(0);
  await expect(page.locator('.leaflet-cityLabels-pane')).toHaveCSS('pointer-events','none');
  const box=(await page.locator('.city-label').boundingBox())!;
  await page.mouse.move(box.x+box.width/2,box.y+box.height/2);await page.mouse.down();
  await page.mouse.move(box.x+box.width/2+80,box.y+box.height/2+40,{steps:6});await page.mouse.up();
  await expect.poll(()=>page.locator('.leaflet-map-pane').getAttribute('style')).not.toBe(pane);
  expect(errors).toEqual([]);
});

test('city names remain readable on mobile and hide immediately while another observer loads',async({page})=>{
  await page.setViewportSize({width:390,height:844});
  const expand=await fixture(page);await expand();
  await expect.poll(()=>page.locator('.city-label clipPath rect').count()).toBeGreaterThan(1);
  await expect(page.locator('.city-label')).toBeInViewport();
  await page.screenshot({path:'../artifacts/city-labels/mobile.png'});
  let release!:()=>void;
  const gate=new Promise<void>(resolve=>{release=resolve;});
  await page.route('**/api/v1/coverage?observer=eric',async route=>{
    await gate;await route.fulfill({json:{revision:2,unit_size:32,cells:[],city_labels:[]}});
  });
  await page.getByRole('button',{name:'Toggle map controls'}).click();
  await page.locator('#coverage-source').selectOption('eric');
  await expect(page.locator('.city-label')).toHaveCount(0);
  release();await expect(page.locator('#known-count')).toContainText('0 known blocks');
});
