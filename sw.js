const CACHE = 'nexus-static-v18';
const FILES = ['./','index.html','manifest.json','ikona-192.png','ikona-512.png'].map(p => new URL(p,self.registration.scope).href);
const ALLOWED = new Set(FILES);
self.addEventListener('install', e => e.waitUntil(caches.open(CACHE).then(c=>c.addAll(FILES)).then(()=>self.skipWaiting())));
self.addEventListener('activate', e => e.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(k=>k.startsWith('nexus-')&&k!==CACHE).map(k=>caches.delete(k)))).then(()=>self.clients.claim())));
self.addEventListener('fetch', e => {
 const r=e.request;
 if(r.method!=='GET'||!ALLOWED.has(r.url)||r.headers.has('Authorization')) return;
 e.respondWith(fetch(r).then(async res=>{
 if(res.ok&&!res.redirected&&!/private|no-store/i.test(res.headers.get('Cache-Control')||'')) { const c=await caches.open(CACHE); await c.put(r,res.clone()); }
 return res;
 }).catch(async()=> (await caches.match(r,{cacheName:CACHE}))||Response.error()));
});
