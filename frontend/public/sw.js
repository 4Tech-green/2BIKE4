// Service worker do PWA: guarda as telas (HTML, JS, CSS, marca) para o app
// abrir rápido e funcionar com conexão ruim. A API e o mapa vão sempre à rede.
const CACHE = '2bike4-v4';
const BASICO = ['./', 'index.html', 'css/main.css', 'js/main.js', 'manifest.json',
                'brand/logo-sem-slogan.svg', 'brand/icone-pin.svg', 'brand/app-icone-192.png'];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(BASICO.map((u) => new Request(u, { cache: 'reload' })))).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys()
    .then((nomes) => Promise.all(nomes.filter((n) => n !== CACHE).map((n) => caches.delete(n))))
    .then(() => self.clients.claim()));
});

self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET' || url.origin !== location.origin || url.pathname.startsWith('/api/')) return;
  // rede primeiro (versão nova a cada deploy); sem rede, o que estiver guardado
  e.respondWith(
    fetch(e.request, { cache: 'no-cache' })
      .then((resp) => {
        const copia = resp.clone();
        caches.open(CACHE).then((c) => c.put(e.request, copia));
        return resp;
      })
      .catch(() => caches.match(e.request).then((r) => r || caches.match('index.html'))));
});
