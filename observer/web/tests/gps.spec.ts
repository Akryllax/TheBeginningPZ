import {test,expect,Page,APIRequestContext} from '@playwright/test';
const url=process.env.SAVED_MAP_URL||'http://127.0.0.1:8766';
// These scenarios mutate the isolated preview, never production.
if(!/^http:\/\/(127\.0\.0\.1|localhost):/.test(url))throw new Error('GPS tests require a local preview');
async function ready(p:Page){await p.goto(url);await expect(p.locator('#ping-status')).toContainText('Ctrl + click');await expect(p.locator('#survivors')).toContainText('akryllax');}
async function draft(p:Page){return p.evaluate(()=>JSON.parse(localStorage.getItem('observer-trip-draft')!));}
async function saved(p:Page){await expect(p.locator('#trip-sync')).toHaveText('Saved');return draft(p);}
async function cleanup(request:APIRequestContext,id?:string){if(!id)return;const data=await(await request.get(`${url}/api/v1/trips`)).json();const t=data.trips.find((p:any)=>p.id===id);if(t)await request.delete(`${url}/api/v1/trips/${id}?version=${t.version}`);}
async function generate(p:Page){
  await p.locator('#trip-mode').click();await p.locator('#gps-from').fill('10818.5, 9844.5');await p.locator('#gps-to').fill('10714.5, 10000.5');
  await expect(p.locator('#gps-auto')).toBeChecked();await p.locator('#gps-fill').click();
  await expect.poll(async()=>((await draft(p))?.stops.length||0),{timeout:60000}).toBeGreaterThan(2);
  return saved(p);
}

test('From / To fills shared bend checkpoints; edits, undo, clear, reroute setting and reload reuse the planner',async({browser})=>{
  test.setTimeout(90000);const context=await browser.newContext(),a=await context.newPage(),b=await context.newPage();let id:string|undefined;const errors:string[]=[];a.on('pageerror',e=>errors.push(e.message));
  try{
    await Promise.all([ready(a),ready(b)]);const first=await generate(a);id=first.id;
    expect(first.routing.auto_reroute).toBe(true);expect(first.stops.some((p:any)=>p.kind==='generated')).toBe(true);
    await expect(a.locator('#trip-distance')).toContainText('road tiles');await expect(a.locator('.trip-access')).not.toHaveCount(0);
    await b.locator('#trip-details summary').click();await b.locator('#trip-select').selectOption(id!);await expect.poll(async()=>(await draft(b)).stops.length).toBe(first.stops.length);
    await a.locator('#gps-auto').uncheck();await saved(a);await expect(b.locator('#gps-auto')).not.toBeChecked();
    await a.locator('#trip-fit').click();await a.waitForTimeout(400);
    const turn=first.stops.find((p:any)=>p.kind==='generated'),pin=a.locator(`[data-waypoint-id="${turn.id}"]`);const box=(await pin.boundingBox())!;
    await a.mouse.move(box.x+12,box.y+12);await a.mouse.down();await a.mouse.move(box.x+30,box.y+12,{steps:5});await a.mouse.up();
    await expect.poll(async()=>(await draft(a)).stops.find((p:any)=>p.id===turn.id)?.kind).toBe('manual');await saved(a);
    await expect.poll(async()=>(await draft(a)).routing.status,{timeout:30000}).toBe('ready');
    await a.locator('#trip-edit-undo').click();await saved(a);await expect.poll(async()=>(await draft(a)).stops.find((p:any)=>p.id===turn.id)?.x).toBe(turn.x);
    await a.locator('#trip-clear').click();await saved(a);expect((await draft(a)).routing).toBeNull();await expect(a.locator('.trip-route')).toHaveCount(0);
    await a.locator('#trip-edit-undo').click();await saved(a);expect((await draft(a)).stops.length).toBe(first.stops.length);
    await expect.poll(async()=>(await draft(a)).routing.status,{timeout:30000}).toBe('ready');
    await a.locator('#trip-fit').click();await a.screenshot({path:'../artifacts/gps/desktop.png'});
    await a.reload();await expect(a.locator('#trip-mode')).toHaveText('Plan trip');await expect(a.locator('#gps-auto')).not.toBeChecked();await expect(a.locator('#gps-auto')).toBeDisabled();
    expect(errors).toEqual([]);
  }finally{await cleanup(context.request,id);await context.close();}
});

test('unknown endpoints and outdated requests preserve the shared trip',async({page})=>{
  test.setTimeout(90000);let id:string|undefined;
  try{
    await ready(page);const first=await generate(page);id=first.id;
    await page.locator('#gps-to').fill('190000, 190000');await page.locator('#gps-fill').click();
    await expect(page.locator('#gps-status')).toContainText('outside the selected map knowledge',{timeout:15000});expect((await draft(page)).stops).toEqual(first.stops);
    await page.locator('#gps-to').fill('10714.5, 10000.5');
    let resume:()=>void=()=>{},started:()=>void=()=>{};const held=new Promise<void>(r=>resume=r),arrived=new Promise<void>(r=>started=r);
    await page.route('**/api/v1/routes',async route=>{const response=await route.fetch();started();await held;await route.fulfill({response});});
    await page.locator('#gps-fill').click();await arrived;await page.locator('#trip-clear').click();await saved(page);resume();
    await expect(page.locator('#gps-status')).toContainText('changed');expect((await draft(page)).stops).toEqual([]);
  }finally{await cleanup(page.request,id);}
});

test('mobile From / To and map-pick controls fit the existing panel',async({browser})=>{
  test.setTimeout(90000);const context=await browser.newContext({viewport:{width:390,height:844},isMobile:true,hasTouch:true}),page=await context.newPage();let id:string|undefined;
  try{
    await ready(page);await page.locator('#toggle-controls').click();const t=await generate(page);id=t.id;
    await expect(page.locator('#gps-from')).toBeVisible();expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
    await page.screenshot({path:'../artifacts/gps/mobile-panel.png'});await page.locator('#gps-to-map').click();
    const map=(await page.locator('#saved-map').boundingBox())!;await page.touchscreen.tap(map.x+map.width*.55,map.y+map.height*.5);
    await expect(page.locator('#gps-to')).toHaveValue(/Map point/);expect((await draft(page)).stops).toEqual(t.stops);
  }finally{await cleanup(context.request,id);await context.close();}
});

test('changing knowledge immediately hides road geometry and generated turns without changing the shared route',async({page})=>{
  let id:string|undefined;
  try{
    await ready(page);const trip=await generate(page);id=trip.id;
    await expect(page.locator('.trip-generated')).not.toHaveCount(0);
    await page.route('**/api/v1/coverage?observer=eric',route=>route.fulfill({json:{revision:999,unit_size:32,cells:[],city_labels:[]}}));
    await page.locator('#coverage-source').selectOption('eric');
    await expect(page.locator('.trip-route')).toHaveCount(0);await expect(page.locator('.trip-generated')).toHaveCount(0);
    expect((await draft(page)).routing.observer).toBeNull();expect((await draft(page)).stops).toEqual(trip.stops);
    await expect(page.locator('#trip-stops')).toContainText('Turn outside this map knowledge');
  }finally{await cleanup(page.request,id);}
});
