// 第二个本机 LDAPS 目录使用未导入后台信任库的证书，发布前必须拒绝。
import { writeFileSync } from 'node:fs'

const base = 'http://127.0.0.1:15173'
async function api(path, method = 'GET', body, token) {
  const response = await fetch(base + path, {
    method, headers: {
      ...(body === undefined ? {} : { 'content-type': 'application/json' }),
      ...(token ? { authorization: `Bearer ${token}` } : {}),
    }, body: body === undefined ? undefined : JSON.stringify(body),
  })
  const result = await response.json()
  if (!response.ok || result.code !== 'OK') throw new Error(`${path}: ${response.status} ${result.code}`)
  return result.data
}
const admin = await api('/api/auth/login', 'POST', {
  username: 'admin', password: process.env.S19_LDAP_ADMIN_PASSWORD || '123123aa',
})
const draft = await api('/api/admin/platform/sso-providers', 'POST', {
  code: 's19-ldap-untrusted', name: 'S19 未受信目录', protocol: 'LDAP',
  issuer: 'ldaps://127.0.0.1:1637', clientId: 's19-ldap-untrusted', clientSecretRef: null,
  protocolConfig: { directoryEndpoint: 'ldaps://127.0.0.1:1637',
    bindDn: 'cn=Directory Manager', bindSecretRef: 'env:TEST_SSO_BIND',
    searchBase: 'ou=people,dc=example,dc=test', loginAttribute: 'uid' },
  allowedDomains: ['example.test'], attributeMapping: { externalUserId: 'uid', email: 'mail' },
  jitPolicy: { enabled: false }, mfaPolicy: { required: false }, callbackUris: [],
}, admin.accessToken)
const report = await api(`/api/admin/platform/sso-providers/${draft.id}/versions/${draft.versions[0].id}/test`,
  'POST', undefined, admin.accessToken)
if (report.status !== 'FAILED' || !report.checks.some(check => check.code === 'SSO_DIRECTORY_UNAVAILABLE' && check.status === 'FAILED')) {
  throw new Error(`Untrusted LDAPS certificate passed preflight: ${JSON.stringify(report.checks)}`)
}
const visible = await api('/api/auth/sso/providers')
if (visible.some(item => item.code === draft.code)) throw new Error('Untrusted directory appeared on login page')
const evidence = { step: 'S19.5', candidate: '0.1.0-s19.5-dev1',
  untrustedCertificatePreflight: report.status, failureCode: 'SSO_DIRECTORY_UNAVAILABLE',
  loginEntryHidden: true, certificateBoundary: 'Different local certificate on 127.0.0.1:1637 was absent from backend trust store.' }
writeFileSync('projects/unexamine/ai/evidence/s19-ldap-untrusted.json', JSON.stringify(evidence, null, 2) + '\n')
console.log(JSON.stringify(evidence, null, 2))
