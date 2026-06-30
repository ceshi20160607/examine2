const fs = require('fs');
const http = require('http');
const path = require('path');

const frontendDir = process.env.UNEXAMINE_FRONTEND_DIR;
const backendPort = Number(process.env.UNEXAMINE_BACKEND_PORT || '9999');
const frontendPort = Number(process.env.UNEXAMINE_FRONTEND_PORT || '18131');
const mimeTypes = new Map([
  ['.html', 'text/html; charset=utf-8'],
  ['.js', 'text/javascript; charset=utf-8'],
  ['.css', 'text/css; charset=utf-8'],
  ['.svg', 'image/svg+xml'],
  ['.json', 'application/json; charset=utf-8'],
  ['.ico', 'image/x-icon'],
  ['.png', 'image/png'],
  ['.jpg', 'image/jpeg'],
  ['.jpeg', 'image/jpeg'],
  ['.webp', 'image/webp'],
]);

function sendStatic(req, res) {
  const url = new URL(req.url, `http://${req.headers.host || '127.0.0.1'}`);
  let pathname = decodeURIComponent(url.pathname);
  if (pathname === '/') {
    pathname = '/index.html';
  }
  let target = path.resolve(frontendDir, `.${pathname}`);
  if (!target.startsWith(path.resolve(frontendDir))) {
    res.writeHead(403);
    res.end('Forbidden');
    return;
  }
  if (!fs.existsSync(target) || fs.statSync(target).isDirectory()) {
    target = path.join(frontendDir, 'index.html');
  }
  res.writeHead(200, {
    'Content-Type': mimeTypes.get(path.extname(target).toLowerCase()) || 'application/octet-stream',
    'Cache-Control': path.basename(target) === 'config.js' ? 'no-store' : 'no-cache',
  });
  fs.createReadStream(target).pipe(res);
}

function proxyApi(req, res) {
  const upstream = http.request({
    hostname: '127.0.0.1',
    port: backendPort,
    path: req.url,
    method: req.method,
    headers: { ...req.headers, host: `127.0.0.1:${backendPort}` },
  }, (upstreamRes) => {
    res.writeHead(upstreamRes.statusCode || 502, upstreamRes.headers);
    upstreamRes.pipe(res);
  });
  upstream.on('error', (error) => {
    res.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({ code: 'LOCAL_PROXY_ERROR', message: error.message }));
  });
  req.pipe(upstream);
}

http.createServer((req, res) => {
  const requestUrl = req.url || '';
  if (requestUrl.startsWith('/api/') || requestUrl.startsWith('/openapi/')) {
    proxyApi(req, res);
    return;
  }
  sendStatic(req, res);
}).listen(frontendPort, '127.0.0.1', () => {
  console.log(`Frontend listening on http://127.0.0.1:${frontendPort}`);
});
