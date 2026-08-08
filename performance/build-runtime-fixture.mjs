import { mkdir, readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'

const argument = (name) => {
  const index = process.argv.indexOf(name)
  if (index < 0 || !process.argv[index + 1]) throw new Error(`${name} is required`)
  return process.argv[index + 1]
}

const fragmentPath = path.resolve(argument('--fragment'))
const sessionsPath = path.resolve(argument('--sessions'))
const fixturePath = path.resolve(argument('--fixture-output'))
const authPath = path.resolve(argument('--auth-output'))
const baseUrl = argument('--base-url').replace(/\/$/u, '')
const load = async (file) => JSON.parse(await readFile(file, 'utf8'))

const fragment = await load(fragmentPath)
const sessions = await load(sessionsPath)
const scopes = ['SC-01', 'SC-02', 'SC-03', 'SC-04', 'SC-05', 'SC-06', 'SC-07']
for (const scope of scopes) {
  if (!sessions[scope]?.cookie || !sessions[scope]?.csrfToken) {
    throw new Error(`${scope} requires an independent cookie and CSRF token`)
  }
}
if (new Set(scopes.map((scope) => sessions[scope].cookie)).size !== scopes.length) {
  throw new Error('Scope sessions must use seven independent cookies')
}

const recordNumber = () => ({
  kind: 'PREDICATE',
  fieldCode: 'record_no',
  operator: 'EQ',
  value: 'R-0500000',
})
const leaves = Array.from({ length: 20 }, recordNumber)
const q11Filter = {
  kind: 'AND',
  children: [
    ...leaves.slice(0, 16),
    {
      kind: 'AND',
      children: [
        leaves[16],
        {
          kind: 'AND',
          children: [
            leaves[17],
            { kind: 'AND', children: [leaves[18], leaves[19]] },
          ],
        },
      ],
    },
  ],
}
const q07Filter = {
  kind: 'AND',
  children: [
    { kind: 'PREDICATE', fieldCode: 'perf_text', operator: 'PREFIX', value: '华东-20' },
    { kind: 'PREDICATE', fieldCode: 'perf_number', operator: 'BETWEEN', value: [1000, 2000] },
  ],
}

const fixture = {
  ...fragment,
  baseUrl,
  systemId: String(fragment.systemId),
  tenantId: String(fragment.tenantId),
  schemaVersionId: String(fragment.schemaVersionId),
  moduleSnapshotId: String(fragment.moduleSnapshotId),
  logicalModuleId: String(fragment.logicalModuleId),
  member42Id: String(fragment.member42Id),
  department10Id: String(fragment.department10Id),
  detailRecordIds: fragment.detailRecordIds.map(String),
  q07Filter,
  q11Filter,
  createValues: {
    perf_text: 'write-performance',
    perf_number: 1234,
    perf_date: '2026-01-15',
    perf_member: String(fragment.member42Id),
    perf_department: String(fragment.department10Id),
  },
  updateValues: {
    perf_text: 'write-performance-updated',
    perf_number: 1235,
    perf_date: '2026-01-16',
    perf_member: String(fragment.member42Id),
    perf_department: String(fragment.department10Id),
  },
  scopeQueries: Object.fromEntries(scopes.map((scope) => [scope, {
    cookie: sessions[scope].cookie,
    csrfToken: sessions[scope].csrfToken,
  }])),
}
const auth = {
  cookie: sessions['SC-01'].cookie,
  csrfToken: sessions['SC-01'].csrfToken,
}

await mkdir(path.dirname(fixturePath), { recursive: true })
await mkdir(path.dirname(authPath), { recursive: true })
await writeFile(fixturePath, `${JSON.stringify(fixture, null, 2)}\n`, { mode: 0o600 })
await writeFile(authPath, `${JSON.stringify(auth, null, 2)}\n`, { mode: 0o600 })
console.log(JSON.stringify({
  fixturePath,
  authPath,
  scopes: scopes.length,
  recordCount: fixture.seedManifest.recordCount,
  q11Leaves: 20,
  q11Depth: 5,
}))
