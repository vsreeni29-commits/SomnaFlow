"""Builds the web edition: dist/artifact.html (page body) and dist/index.html (installable web app for GitHub Pages)."""
import json, pathlib
here = pathlib.Path(__file__).parent
dist = here / "dist"; dist.mkdir(exist_ok=True)
body = (here / "app.src.html").read_text().replace("/*CONTENT*/", json.dumps(json.loads((here / "content.json").read_text()), separators=(",", ":"), ensure_ascii=False))
(dist / "artifact.html").write_text(body)
head = ('<!doctype html><html lang="en"><head><meta charset="utf-8">'
        '<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">'
        '<meta name="apple-mobile-web-app-capable" content="yes"><meta name="mobile-web-app-capable" content="yes">'
        '<meta name="apple-mobile-web-app-status-bar-style" content="black-translucent"><meta name="theme-color" content="#0B0817">'
        '<link rel="manifest" href="manifest.json"><link rel="apple-touch-icon" href="icon-180.png">'
        '<style>:root{padding:env(safe-area-inset-top,0px) 0 0}body{margin:0}[hidden]{display:none!important}</style></head><body>')
(dist / "index.html").write_text(head + body + "</body></html>")
(dist / "manifest.json").write_text(json.dumps({"name": "SomnaFlow", "short_name": "SomnaFlow", "start_url": ".", "display": "standalone",
    "background_color": "#0B0817", "theme_color": "#0B0817", "icons": [{"src": "icon-180.png", "sizes": "180x180", "type": "image/png"}, {"src": "icon-512.png", "sizes": "512x512", "type": "image/png"}]}))
(dist / "sw.js").write_text("const C='somnaflow-v1';self.addEventListener('install',e=>{self.skipWaiting();e.waitUntil(caches.open(C).then(c=>c.addAll(['./','manifest.json','icon-180.png'])))});"
    "self.addEventListener('activate',e=>e.waitUntil(self.clients.claim()));"
    "self.addEventListener('fetch',e=>{if(e.request.method!=='GET')return;e.respondWith(fetch(e.request).then(r=>{const k=r.clone();caches.open(C).then(c=>c.put(e.request,k));return r}).catch(()=>caches.match(e.request,{ignoreSearch:true})))});")
for f in here.glob("icon-*.png"): (dist / f.name).write_bytes(f.read_bytes())
print("built", (dist / "index.html").stat().st_size, "bytes")
