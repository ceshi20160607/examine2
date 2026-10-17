// 隔离验收身份源：按授权码、PKCE 和 Bearer 用户信息协议运行，不代表已接入企业真实 IdP。
import { createServer } from 'node:http'
import { createHash, randomUUID } from 'node:crypto'

const port = Number(process.env.S19_OAUTH2_PROVIDER_PORT || 18212)
const secret = process.env.APP_SSO_OAUTH2_CLIENT_SECRET
if (!secret) throw new Error('APP_SSO_OAUTH2_CLIENT_SECRET is required')
const callback = process.env.S19_OAUTH2_CALLBACK || 'http://127.0.0.1:15184/sso/callback'
const clientId = 's19-oauth-client'
const pending = new Map()
const codes = new Map()
const tokens = new Set()

function reply(response, status, body, type = 'application/json; charset=utf-8') {
  response.writeHead(status, { 'content-type': type, 'cache-control': 'no-store' })
  response.end(body)
}

const server = createServer(async (request, response) => {
  const url = new URL(request.url, `http://127.0.0.1:${port}`)
  if (url.pathname === '/health' && request.method === 'GET') {
    reply(response, 200, JSON.stringify({ status: 'UP' }))
    return
  }
  if (url.pathname === '/authorize' && request.method === 'GET') {
    const q = url.searchParams
    if (q.get('response_type') !== 'code' || q.get('client_id') !== clientId ||
        q.get('redirect_uri') !== callback || q.get('code_challenge_method') !== 'S256' ||
        !q.get('state') || !q.get('code_challenge')) {
      reply(response, 400, JSON.stringify({ error: 'invalid_request' }))
      return
    }
    const requestId = randomUUID()
    pending.set(requestId, { state: q.get('state'), challenge: q.get('code_challenge'), redirect: callback })
    reply(response, 200, `<!doctype html><html lang="zh"><meta charset="utf-8"><title>隔离 OAuth2 身份源</title><body><h1>隔离 OAuth2 身份源</h1><p>验收账号：S19 OAuth 成员</p><form method="post" action="/authorize"><input type="hidden" name="requestId" value="${requestId}"><button type="submit">确认企业身份登录</button></form></body></html>`, 'text/html; charset=utf-8')
    return
  }
  if (url.pathname === '/authorize' && request.method === 'POST') {
    const chunks = []
    for await (const chunk of request) chunks.push(chunk)
    const form = new URLSearchParams(Buffer.concat(chunks).toString('utf8'))
    const authorization = pending.get(form.get('requestId'))
    pending.delete(form.get('requestId'))
    if (!authorization) { reply(response, 400, JSON.stringify({ error: 'invalid_request' })); return }
    const code = randomUUID()
    codes.set(code, authorization)
    const redirect = new URL(authorization.redirect)
    redirect.searchParams.set('code', code)
    redirect.searchParams.set('state', authorization.state)
    response.writeHead(302, { location: redirect.toString(), 'cache-control': 'no-store' })
    response.end()
    return
  }
  if (url.pathname === '/token' && request.method === 'POST') {
    const chunks = []
    for await (const chunk of request) chunks.push(chunk)
    const form = new URLSearchParams(Buffer.concat(chunks).toString('utf8'))
    const authorization = codes.get(form.get('code'))
    codes.delete(form.get('code'))
    const verifier = form.get('code_verifier') || ''
    const challenge = createHash('sha256').update(verifier, 'ascii').digest('base64url')
    if (!authorization || form.get('grant_type') !== 'authorization_code' ||
        form.get('client_id') !== clientId || form.get('client_secret') !== secret ||
        form.get('redirect_uri') !== callback || challenge !== authorization.challenge) {
      reply(response, 400, JSON.stringify({ error: 'invalid_grant' }))
      return
    }
    const token = randomUUID()
    tokens.add(token)
    reply(response, 200, JSON.stringify({ access_token: token, token_type: 'Bearer' }))
    return
  }
  if (url.pathname === '/userinfo' && request.method === 'GET') {
    const token = request.headers.authorization?.replace(/^Bearer /, '')
    if (!tokens.has(token)) { reply(response, 401, JSON.stringify({ error: 'invalid_token' })); return }
    reply(response, 200, JSON.stringify({ employee_id: 's19-oauth-user', mail: 's19-oauth@example.test', mail_verified: true, mfa: true }))
    return
  }
  reply(response, 404, JSON.stringify({ error: 'not_found' }))
})

server.listen(port, '127.0.0.1', () => process.stdout.write(`OAuth2 fixture listening on ${port}\n`))
