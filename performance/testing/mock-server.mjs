import { createServer } from 'node:http'

const port = Number(process.argv[2] ?? 19091)
let recordId = 1000
const server = createServer((request, response) => {
  request.resume()
  request.on('end', () => {
    let data = { rows: [], page: 1, size: 50, total: 0 }
    if (request.method === 'POST' && /\/records$/u.test(request.url ?? '')) data = { recordId: ++recordId, version: 1 }
    else if (/:activate$/u.test(request.url ?? '')) data = { recordId, version: 2 }
    else if (request.method === 'PUT') data = { recordId, version: 3 }
    else if (/\/comments$/u.test(request.url ?? '')) data = { commentId: ++recordId }
    else if (request.method === 'GET') data = { recordId, version: 1, values: {} }
    response.writeHead(200, { 'content-type': 'application/json' })
    response.end(JSON.stringify({ code: 'OK', message: 'OK', data, requestId: 'perf-smoke', traceId: 'perf-smoke', errors: [] }))
  })
})
server.listen(port, '127.0.0.1', () => console.log(`PERF_SMOKE_READY=${port}`))
