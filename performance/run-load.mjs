import { appendFile, mkdir, readFile, writeFile } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import { performance } from 'node:perf_hooks'
import path from 'node:path'

const arg = (name, fallback = null) => {
  const index = process.argv.indexOf(name)
  return index >= 0 ? process.argv[index + 1] : fallback
}
const flag = (name) => process.argv.includes(name)
const mode = arg('--mode')
if (!['read', 'write'].includes(mode)) throw new Error('--mode must be read or write')
const root = path.resolve(import.meta.dirname)
const loadJson = async (file) => JSON.parse(await readFile(file, 'utf8'))
const profile = await loadJson(path.join(root, 'profile.json'))
const fixture = await loadJson(path.resolve(arg('--fixture')))
const auth = await loadJson(path.resolve(arg('--auth')))
const output = path.resolve(arg('--output'))
const smoke = flag('--smoke')
await mkdir(output, { recursive: true })

if (!smoke) {
  if (fixture.seedManifest?.contractId !== profile.contractId || fixture.seedManifest?.recordCount !== profile.seedRecords || fixture.seedManifest?.seedSha256 !== profile.seedSha256 || !/^[a-f0-9]{64}$/u.test(fixture.seedManifest?.manifestSha256 ?? '')) {
    throw new Error('The deterministic one-million-record seed manifest is missing or invalid')
  }
}
const settings = smoke ? { ...profile[mode], vus: Math.min(profile[mode].vus, 2), warmupSeconds: 1, minimumMeasurementSeconds: 2, maximumMeasurementSeconds: 10, minimumSamplesPerQuery: 1, minimumListSamples: 1, minimumDetailSamples: 1, minimumSamplesPerClass: 1 } : profile[mode]
const baseUrl = fixture.baseUrl.replace(/\/$/u, '')
const headers = { 'content-type': 'application/json', cookie: auth.cookie, 'x-csrf-token': auth.csrfToken }
const rawPath = path.join(output, 'raw-samples.jsonl')
await writeFile(rawPath, '', 'utf8')
const durations = new Map()
const counts = new Map()
const errors = []
let unexpectedErrorCount = 0
let rawBuffer = ''
let sequence = 0
const add = async (sample) => {
  if (!durations.has(sample.class)) durations.set(sample.class, [])
  durations.get(sample.class).push(Math.max(0, Math.round(sample.durationMs)))
  counts.set(sample.id, (counts.get(sample.id) ?? 0) + 1)
  rawBuffer += `${JSON.stringify(sample)}\n`
  if (rawBuffer.length > 1024 * 1024) { const chunk = rawBuffer; rawBuffer = ''; await appendFile(rawPath, chunk, 'utf8') }
}
const request = async (id, klass, method, url, body, measured, authOverride = null) => {
  const started = performance.now()
  let status = 0; let valid = false; let data = null; let failure = null
  try {
    const requestHeaders = authOverride ? { ...headers, cookie: authOverride.cookie, 'x-csrf-token': authOverride.csrfToken } : headers
    const response = await fetch(`${baseUrl}${url}`, { method, headers: { ...requestHeaders, 'idempotency-key': `perf-${mode}-${Date.now()}-${sequence++}` }, body: body == null ? undefined : JSON.stringify(body) })
    status = response.status
    const envelope = await response.json()
    valid = response.ok && envelope && envelope.code === 'OK' && Object.hasOwn(envelope, 'data')
    data = envelope?.data
    if (!valid) failure = `HTTP_${status}_${envelope?.code ?? 'SCHEMA'}`
  } catch (error) { failure = error instanceof Error ? error.message : String(error) }
  const durationMs = performance.now() - started
  if (measured) {
    await add({ at: new Date().toISOString(), id, class: klass, durationMs, status, valid })
    if (failure) {
      unexpectedErrorCount++
      if (errors.length < 100) errors.push({ id, status, failure })
    }
  }
  if (failure) throw new Error(failure)
  return data
}
const replace = (value) => {
  if (Array.isArray(value)) return value.map(replace)
  if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, replace(item)]))
  if (typeof value === 'string') {
    const match = /^\$\{([A-Za-z0-9_]+)\}$/u.exec(value)
    if (match) return structuredClone(fixture[match[1]])
  }
  return value
}
let readCases = []
if (mode === 'read') {
  const cases = await loadJson(path.join(root, 'cases/read-cases.json'))
  const queryContract = await loadJson(path.join(root, 'cases/query-contract.json'))
  readCases = cases.flatMap((item) => {
    const resolved = { ...item, body: replace(item.body), baseId: item.id }
    if (item.id !== 'Q12') return [resolved]
    if (!fixture.scopeQueries || typeof fixture.scopeQueries !== 'object') throw new Error('fixture.scopeQueries with SC-01..SC-07 auth sessions is required')
    return item.scopeVariants.map((scope) => {
      const scopeFixture = fixture.scopeQueries[scope]
      if (!scopeFixture?.cookie || !scopeFixture?.csrfToken) throw new Error(`Missing ${scope} cookie/csrfToken in fixture.scopeQueries`)
      return { ...resolved, id: `Q12_${scope.replace('-', '')}`, scope, authOverride: scopeFixture }
    })
  })
  const resolvedContract = readCases.map(({ id, baseId, kind, body, scope }) => ({ id, baseId, kind, scope: scope ?? null, method: 'POST', path: `/api/v1/systems/${fixture.systemId}/runtime/modules/${fixture.moduleCode}/records:query`, body: { ...body, schemaVersionId: fixture.schemaVersionId }, expected: baseId === 'Q12' ? queryContract.cases.Q12.scopeVariants[scope] : queryContract.cases[baseId] }))
  await writeFile(path.join(output, 'resolved-query-contract.json'), `${JSON.stringify({ schemaVersion: 1, contractId: profile.contractId, cases: resolvedContract }, null, 2)}\n`, 'utf8')
}
const queryPath = `/api/v1/systems/${encodeURIComponent(fixture.systemId)}/runtime/modules/${encodeURIComponent(fixture.moduleCode)}/records:query`
const detailPath = (recordId) => `/api/v1/systems/${encodeURIComponent(fixture.systemId)}/runtime/modules/${encodeURIComponent(fixture.moduleCode)}/records/${encodeURIComponent(recordId)}`
const schemaBody = (body) => ({ ...body, schemaVersionId: fixture.schemaVersionId })
const readIteration = async (worker, measured) => {
  const cursor = (sequence + worker) % 10
  if (cursor < 7) {
    const candidates = readCases.filter((item) => item.kind === 'list')
    const item = candidates[(sequence + worker) % candidates.length]
    await request(item.id, 'list', 'POST', queryPath, schemaBody(item.body), measured, item.authOverride)
  } else if (cursor < 9) {
    const recordId = fixture.detailRecordIds[(sequence + worker) % fixture.detailRecordIds.length]
    await request('DETAIL', 'detail', 'GET', detailPath(recordId), null, measured)
  } else {
    const item = readCases.find((value) => value.id === 'Q09')
    await request(item.id, 'list', 'POST', queryPath, schemaBody(item.body), measured, item.authOverride)
  }
}
const createDraft = async (worker) => request('SETUP_CREATE', 'setup', 'POST', `/api/v1/systems/${fixture.systemId}/runtime/modules/${fixture.moduleCode}/records`, { schemaVersionId: fixture.schemaVersionId, title: `perf-${worker}-${sequence}`, values: fixture.createValues }, false)
const writeIteration = async (worker, measured) => {
  const selector = (sequence + worker) % 10
  const draft = await createDraft(worker)
  const recordId = draft.recordId; const version = draft.version
  if (selector < 6) {
    await request('CREATE_ACTIVATE', 'write', 'POST', `${detailPath(recordId)}:activate`, { expectedVersion: version }, measured)
  } else {
    const active = await request('SETUP_ACTIVATE', 'setup', 'POST', `${detailPath(recordId)}:activate`, { expectedVersion: version }, false)
    if (selector < 9) await request('UPDATE', 'write', 'PUT', detailPath(recordId), { schemaVersionId: fixture.schemaVersionId, title: `updated-${sequence}`, expectedVersion: active.version, values: fixture.updateValues }, measured)
    else await request('COMMENT', 'write', 'POST', `${detailPath(recordId)}/comments`, { recordVersion: active.version, body: `perf comment ${sequence}` }, measured)
  }
}
const iteration = mode === 'read' ? readIteration : writeIteration
const runFor = async (seconds, measured, stopWhen = null) => {
  const started = performance.now()
  await Promise.all(Array.from({ length: settings.vus }, (_, worker) => (async () => {
    while ((performance.now() - started) / 1000 < seconds || (stopWhen && !stopWhen())) {
      try { await iteration(worker, measured) } catch { if (!measured) await new Promise((resolve) => setTimeout(resolve, 50)) }
      if ((performance.now() - started) / 1000 >= settings.maximumMeasurementSeconds && measured) break
    }
  })()))
  return (performance.now() - started) / 1000
}
await runFor(settings.warmupSeconds, false)
const enough = () => mode === 'read'
  ? readCases.every((item) => (counts.get(item.id) ?? 0) >= settings.minimumSamplesPerQuery) && (durations.get('list')?.length ?? 0) >= settings.minimumListSamples && (durations.get('detail')?.length ?? 0) >= settings.minimumDetailSamples
  : ['CREATE_ACTIVATE', 'UPDATE', 'COMMENT'].every((id) => (counts.get(id) ?? 0) >= settings.minimumSamplesPerClass)
const elapsedSeconds = await runFor(settings.minimumMeasurementSeconds, true, enough)
if (rawBuffer) await appendFile(rawPath, rawBuffer, 'utf8')
const percentile = (values, p) => values.length ? [...values].sort((a, b) => a - b)[Math.max(0, Math.ceil(values.length * p) - 1)] : null
const metrics = Object.fromEntries([...durations].map(([key, values]) => [key, { samples: values.length, p75: percentile(values, .75), p95: percentile(values, .95), max: values.reduce((maximum, value) => Math.max(maximum, value), 0) }]))
const buckets = Object.fromEntries([...durations].map(([key, values]) => [key, Object.fromEntries([...values.reduce((map, value) => map.set(value, (map.get(value) ?? 0) + 1), new Map())].sort((a, b) => a[0] - b[0]))]))
const thresholdPass = unexpectedErrorCount === 0 && enough() && (mode === 'read' ? metrics.list?.p95 <= profile.thresholdsMs.listP95 && metrics.detail?.p95 <= profile.thresholdsMs.detailP95 : metrics.write?.p95 <= profile.thresholdsMs.writeP95)
const summary = { schemaVersion: 1, contractId: profile.contractId, smoke, mode, startedAt: new Date(Date.now() - elapsedSeconds * 1000).toISOString(), elapsedSeconds, vus: settings.vus, counts: Object.fromEntries(counts), metrics, unexpectedErrors: unexpectedErrorCount, errorExamples: errors, thresholdPass }
const rawSha256 = createHash('sha256').update(await readFile(rawPath)).digest('hex')
const histogramEvidence = { format: 'HDR_EQUIVALENT_EXACT_1MS', schemaVersion: 1, precisionMs: 1, nearestRank: true, rawSamplesSha256: rawSha256, classes: Object.fromEntries([...durations].map(([key, values]) => [key, { samples: values.length, p75: percentile(values, .75), p95: percentile(values, .95), buckets: buckets[key] }])) }
histogramEvidence.histogramSha256 = createHash('sha256').update(JSON.stringify(histogramEvidence)).digest('hex')
await writeFile(path.join(output, 'histogram-1ms.json'), JSON.stringify(histogramEvidence, null, 2), 'utf8')
await writeFile(path.join(output, 'summary.json'), JSON.stringify(summary, null, 2), 'utf8')
if (!thresholdPass) process.exitCode = 1
