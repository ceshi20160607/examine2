// 发布包前端进程：只提供已构建页面，并将同源 API 请求转发到本机后端。
import { createServer, request as httpRequest } from 'node:http'
import { readFile, stat } from 'node:fs/promises'
import { resolve, sep, extname } from 'node:path'

const root = resolve(process.env.APP_FRONTEND_ROOT || '.')
const host = process.env.APP_FRONTEND_HOST || '127.0.0.1'
const port = Number(process.env.APP_FRONTEND_PORT || '15173')
const target = new URL('http://127.0.0.1:9999')
if (!Number.isInteger(port) || port < 1024 || port > 65535 || target.protocol !== 'http:') {
  throw new Error('Invalid frontend port or backend target')
}
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8', '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml', '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg',
  '.ico': 'image/x-icon', '.woff': 'font/woff', '.woff2': 'font/woff2', '.txt': 'text/plain; charset=utf-8' }

function proxy(request, response) {
  const upstream = httpRequest({ hostname: target.hostname, port: target.port, method: request.method,
    path: request.url, headers: { ...request.headers, host: target.host } }, upstreamResponse => {
    response.writeHead(upstreamResponse.statusCode || 502, upstreamResponse.headers)
    upstreamResponse.pipe(response)
  })
  upstream.on('error', () => {
    if (!response.headersSent) response.writeHead(502, { 'content-type': 'text/plain; charset=utf-8' })
    response.end('Backend unavailable')
  })
  request.pipe(upstream)
}

async function serve(request, response, url) {
  if (request.method !== 'GET' && request.method !== 'HEAD') {
    response.writeHead(405, { allow: 'GET, HEAD' })
    response.end()
    return
  }
  let pathname
  try { pathname = decodeURIComponent(url.pathname) } catch { response.writeHead(400); response.end(); return }
  const requested = resolve(root, `.${pathname}`)
  if (requested !== root && !requested.startsWith(root + sep)) {
    response.writeHead(403)
    response.end()
    return
  }
  let file = requested
  try {
    if (!(await stat(file)).isFile()) file = resolve(root, 'index.html')
  } catch {
    if (extname(pathname)) { response.writeHead(404); response.end(); return }
    file = resolve(root, 'index.html')
  }
  try {
    const bytes = await readFile(file)
    response.writeHead(200, { 'content-type': types[extname(file)] || 'application/octet-stream',
      'content-length': bytes.length, 'cache-control': extname(file) === '.html' ? 'no-cache' : 'public, max-age=3600' })
    response.end(request.method === 'HEAD' ? undefined : bytes)
  } catch {
    response.writeHead(404)
    response.end()
  }
}

createServer((request, response) => {
  const url = new URL(request.url, `http://${host}:${port}`)
  if (/^\/(api|actuator)(\/|$)/.test(url.pathname)) proxy(request, response)
  else void serve(request, response, url)
}).listen(port, host, () => console.log(`Frontend serving ${root} on http://${host}:${port}`))
