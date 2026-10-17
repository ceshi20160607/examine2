// 隔离 OIDC 身份源签发 RS256 身份令牌，仅用于同一发布候选的协议回归。
import { createServer } from 'node:http'
import { createHash, createSign, generateKeyPairSync, randomUUID } from 'node:crypto'

const port = 18214
const issuer = `http://127.0.0.1:${port}`
const callback = 'http://127.0.0.1:15173/sso/callback'
const secret = process.env.APP_SSO_OAUTH2_CLIENT_SECRET
if (!secret) throw new Error('OIDC fixture client secret missing')
const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 })
const jwk = publicKey.export({ format: 'jwk' })
const pending = new Map()
const codes = new Map()
const tokens = new Set()

function send(response, status, body, type = 'application/json; charset=utf-8') {
  response.writeHead(status, { 'content-type': type, 'cache-control': 'no-store' })
  response.end(body)
}
function jwt(nonce) {
  const now = Math.floor(Date.now() / 1000)
  const header = Buffer.from(JSON.stringify({ alg: 'RS256', kid: 's19-oidc-key', typ: 'JWT' })).toString('base64url')
  const payload = Buffer.from(JSON.stringify({ iss: issuer, aud: 's19-oidc-client',
    sub: 's19-oidc-subject', nonce, iat: now, exp: now + 300 })).toString('base64url')
  const signed = `${header}.${payload}`
  const signature = createSign('RSA-SHA256').update(signed).sign(privateKey).toString('base64url')
  return `${signed}.${signature}`
}

createServer(async (request, response) => {
  const url = new URL(request.url, issuer)
  if (url.pathname === '/health') return send(response, 200, JSON.stringify({ status: 'UP' }))
  if (url.pathname === '/jwks') return send(response, 200, JSON.stringify({ keys: [
    { kty: 'RSA', kid: 's19-oidc-key', alg: 'RS256', use: 'sig', n: jwk.n, e: jwk.e },
  ] }))
  if (url.pathname === '/authorize' && request.method === 'GET') {
    const q = url.searchParams
    if (q.get('response_type') !== 'code' || q.get('client_id') !== 's19-oidc-client'
        || q.get('redirect_uri') !== callback || q.get('code_challenge_method') !== 'S256'
        || !q.get('nonce') || !q.get('state')) return send(response, 400, '{}')
    const requestId = randomUUID()
    pending.set(requestId, { state: q.get('state'), nonce: q.get('nonce'), challenge: q.get('code_challenge') })
    return send(response, 200, `<!doctype html><html lang="zh"><meta charset="utf-8"><body><h1>隔离 OIDC 身份源</h1><form method="post" action="/authorize"><input type="hidden" name="requestId" value="${requestId}"><button type="submit">确认企业身份登录</button></form></body></html>`, 'text/html; charset=utf-8')
  }
  if (url.pathname === '/authorize' && request.method === 'POST') {
    const chunks = []
    for await (const chunk of request) chunks.push(chunk)
    const form = new URLSearchParams(Buffer.concat(chunks).toString())
    const auth = pending.get(form.get('requestId'))
    pending.delete(form.get('requestId'))
    if (!auth) return send(response, 400, '{}')
    const code = randomUUID()
    codes.set(code, auth)
    const redirect = new URL(callback)
    redirect.searchParams.set('code', code)
    redirect.searchParams.set('state', auth.state)
    response.writeHead(302, { location: redirect.toString(), 'cache-control': 'no-store' })
    return response.end()
  }
  if (url.pathname === '/token' && request.method === 'POST') {
    const chunks = []
    for await (const chunk of request) chunks.push(chunk)
    const form = new URLSearchParams(Buffer.concat(chunks).toString())
    const auth = codes.get(form.get('code'))
    codes.delete(form.get('code'))
    const challenge = createHash('sha256').update(form.get('code_verifier') || '').digest('base64url')
    if (!auth || form.get('client_id') !== 's19-oidc-client' || form.get('client_secret') !== secret
        || form.get('redirect_uri') !== callback || challenge !== auth.challenge) return send(response, 400, '{}')
    const token = randomUUID()
    tokens.add(token)
    return send(response, 200, JSON.stringify({ access_token: token, token_type: 'Bearer', id_token: jwt(auth.nonce) }))
  }
  if (url.pathname === '/userinfo') {
    const token = request.headers.authorization?.replace(/^Bearer /, '')
    if (!tokens.has(token)) return send(response, 401, '{}')
    return send(response, 200, JSON.stringify({ sub: 's19-oidc-subject', email: 's19-oidc@example.test',
      email_verified: true, amr: ['pwd', 'mfa'] }))
  }
  send(response, 404, '{}')
}).listen(port, '127.0.0.1', () => console.log(`OIDC fixture listening on ${port}`))
