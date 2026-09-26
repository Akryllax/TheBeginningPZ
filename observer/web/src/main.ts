async function start() {
  try {
    if (location.pathname.replace(/\/$/, '') === '/debug/storyteller') {
      await import('./storyteller');
      return;
    }
    const response=await fetch('/api/v1/world');
    if(!response.ok)throw new Error('Server unavailable');
    const world=await response.json();
    if(world.mode==='saved_map')await import('./saved');
    else await import('./experimental');
  } catch {
    const app=document.querySelector('#app')!;
    app.textContent='The map service is unavailable. Reload this page to reconnect.';
  }
}
void start();
