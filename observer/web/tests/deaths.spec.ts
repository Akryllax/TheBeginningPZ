import {test,expect} from '@playwright/test';
const url=process.env.SAVED_MAP_URL||'http://127.0.0.1:8766';

for(const mobile of [false,true])test(`death markers show a red X above a permanent label (${mobile?'mobile':'desktop'})`,async({page})=>{
  if(mobile)await page.setViewportSize({width:390,height:844});
  const world=await (await page.request.get(`${url}/api/v1/world`)).json();
  const p=world.players.find((p:any)=>p.name==='akryllax')||world.players[0];
  const death={id:'browser-fixture',name:'akryllax',x:Math.floor(p.x)+35,y:Math.floor(p.y)+35,z:0,occurred_at:Date.now()-60000};
  // Visual fixtures stay in this browser; never write fake deaths to the server.
  await page.route('**/api/v1/world',route=>route.fulfill({json:{...world,deaths:[death],death_markers_since:Date.now()-3600000}}));
  await page.goto(url);
  const marker=page.locator('.death-marker'),label=page.locator('.death-marker-label');
  await expect(marker).toBeVisible();
  await expect(marker.locator('path').last()).toHaveAttribute('stroke','#ff5454');
  await expect(label).toHaveCount(1);
  await expect(label).toHaveText('akryllax died here');
  await expect(label).toHaveCSS('color','rgb(255, 255, 255)');
  // Read both rectangles in one frame, including during the initial map pan.
  await expect.poll(()=>page.evaluate(()=>{
    const pin=document.querySelector('.death-marker')!.getBoundingClientRect();
    const text=document.querySelector('.death-marker-label')!.getBoundingClientRect();
    return text.y>=pin.bottom&&Math.abs(text.x+text.width/2-pin.x-pin.width/2)<2;
  })).toBe(true);
  await marker.click();
  await expect(page.locator('.leaflet-popup-content')).toContainText(`${death.x}, ${death.y} · Floor 0`);
  await expect(page.locator('.leaflet-popup-content')).toContainText('Died:');
  await page.locator('.leaflet-popup-close-button').click();
  // No hover is needed, and zooming retains the exact same marker label.
  await page.mouse.move(0,0);
  await page.locator('.leaflet-control-zoom-in').click();
  await expect(label).toBeVisible();
  if(mobile)await page.locator('#toggle-controls').click();
  await page.locator('#show-deaths').uncheck();
  await expect(marker).toHaveCount(0);await expect(label).toHaveCount(0);
  await page.locator('#death-history summary').click();
  await expect(page.locator('#death-count')).toHaveText('1');
  await page.locator('#death-markers button').click();
  await expect(marker).toBeVisible();await expect(label).toBeVisible();
  if(mobile)await expect(page.locator('#controls')).not.toHaveClass(/open/);
  await page.screenshot({path:`../artifacts/death-markers-${mobile?'mobile':'desktop'}.png`});
  await page.reload();await expect(label).toBeVisible();
});

test('death names are rendered as plain text',async({page})=>{
  const world=await (await page.request.get(`${url}/api/v1/world`)).json();
  const p=world.players.find((p:any)=>p.name==='akryllax')||world.players[0];
  const name='<img src=x onerror=alert(1)>';
  await page.route('**/api/v1/world',route=>route.fulfill({json:{...world,deaths:[{id:'safe-text',name,x:p.x+10,y:p.y+10,z:0,occurred_at:Date.now()}]}}));
  await page.goto(url);
  await expect(page.locator('.death-marker-label')).toHaveText(`${name} died here`);
  await expect(page.locator('.death-marker-label img')).toHaveCount(0);
  await page.locator('.death-marker').click();
  await expect(page.locator('.map-popup strong')).toHaveText(`${name} died here`);
  await expect(page.locator('.map-popup img')).toHaveCount(0);
});
