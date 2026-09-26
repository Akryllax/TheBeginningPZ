import { test,expect } from '@playwright/test';

test('reconstructed scene, camera switching, selection and public inspection',async({page})=>{
  const errors:string[]=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto('/');
  await expect(page.getByRole('heading',{name:'Zomboid Observer'})).toBeVisible();
  await expect(page.locator('#scene-count')).not.toHaveText('0 elements');
  await expect(page.locator('#demo-banner')).toBeVisible();
  await page.getByRole('button',{name:'Isometric',exact:true}).click();
  await expect(page.locator('#mode-iso')).toHaveAttribute('aria-pressed','true');
  await page.getByRole('button',{name:'3D view',exact:true}).click();
  await expect(page.locator('#mode-3d')).toHaveAttribute('aria-pressed','true');
  const bounds=await page.locator('canvas').boundingBox();
  expect(bounds).toBeTruthy();
  await page.mouse.click(bounds!.x+bounds!.width/2,bounds!.y+bounds!.height/2);
  await expect(page.locator('#selection-title')).not.toHaveText('Look a little closer');
  const detail=await page.request.get('/api/v1/elements/vehicle:2');
  expect(detail.ok()).toBeTruthy();
  expect((await detail.json()).inspections.some((i:any)=>i.items?.some((x:any)=>x.name==='Nails'))).toBeTruthy();
  const stats=await page.evaluate(()=>(window as any).observerDiagnostics());
  expect(stats.drawCalls).toBeLessThan(60);expect(stats.objects).toBeGreaterThan(1000);
  await page.screenshot({path:'../artifacts/viewer-3d.png',fullPage:true});
  await page.getByRole('button',{name:'Isometric',exact:true}).click();
  await page.screenshot({path:'../artifacts/viewer-isometric.png',fullPage:true});
  expect(errors).toEqual([]);
});

test('coordinate errors, individual view, floor controls and mobile navigation',async({page})=>{
  await page.goto('/');
  await page.getByLabel('Coordinates X, Y').fill('not coordinates');
  await page.getByRole('button',{name:'Go to coordinates',exact:true}).click();
  await expect(page.locator('#search-error')).toBeVisible();
  await page.getByLabel('Coordinates X, Y').fill('10818, 9844');
  await page.getByRole('button',{name:'Go to coordinates',exact:true}).click();
  await expect(page.locator('#search-error')).not.toBeVisible();
  await page.getByRole('button',{name:'Lower floor'}).click();
  await expect(page.locator('#floor-label')).toHaveText('Floor -1');
  await page.getByRole('button',{name:'Higher floor'}).click();
  await page.getByLabel('Observation source').selectOption('eric');
  await page.setViewportSize({width:390,height:844});
  await page.getByRole('button',{name:'Toggle map controls'}).click();
  await expect(page.locator('#sidebar')).toBeVisible();
  await page.screenshot({path:'../artifacts/viewer-mobile.png',fullPage:true});
});
