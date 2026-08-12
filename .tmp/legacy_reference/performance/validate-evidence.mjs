import { access, readFile } from 'node:fs/promises'
import path from 'node:path'
import { CONTRACT_ID, CONTRACT_SHA256, fileSha256, loadJson, nearestRank, readJsonLines, sha256, stableJson } from './lib/evidence.mjs'

const arg = (name, fallback = null) => { const index = process.argv.indexOf(name); return index >= 0 ? process.argv[index + 1] : fallback }
const root = path.resolve(arg('--evidence') ?? (() => { throw new Error('--evidence is required') })())
const requireFile = async (...parts) => { const file = path.join(root, ...parts); await access(file); return file }
const profile = await loadJson(path.join(import.meta.dirname, 'profile.json'))
const seed = await loadJson(await requireFile('seed', 'seed-manifest.json'))
const unsignedSeed = { ...seed }; delete unsignedSeed.manifestSha256
if (seed.contractId !== CONTRACT_ID || seed.seedSha256 !== CONTRACT_SHA256 || seed.recordCount !== 1_000_000 || !seed.releaseEligible || sha256(stableJson(unsignedSeed)) !== seed.manifestSha256) throw new Error('Seed manifest is not release eligible')
if (seed.generatorSha256 !== await fileSha256(path.join(import.meta.dirname, 'generate-seed.mjs')) || seed.specSha256 !== await fileSha256(path.join(import.meta.dirname, 'seed/seed-spec.json'))) throw new Error('Seed implementation provenance mismatch')
for (const [table, rows] of Object.entries({ accounts: 10_000, members: 10_000, memberTenants: 10_000, departments: 50, departmentClosure: 139, memberDepartments: 10_000, records: 1_000_000, values: 20_000_000, indexes: 9_000_000, search: 1_000_000, relations: 600_000, subRecords: 1_000_000, subValues: 1_000_000, referenceStates: 300_000, teams: 100_000, teamMembers: 300_000, comments: 500_000 })) if (seed.tables?.[table]?.rows !== rows) throw new Error(`Release seed ${table} row count is incomplete`)
if (seed.shape?.archivedRecords !== 100_000 || seed.shape?.members !== 10_000 || seed.shape?.departments !== 50 || seed.shape?.relationRecords !== 300_000 || seed.shape?.subtableRecords !== 200_000 || seed.shape?.teamRecords !== 100_000 || seed.shape?.commentRecords !== 100_000) throw new Error('Release seed distribution is incomplete')
for (const item of Object.values(seed.tables)) if (await fileSha256(await requireFile('seed', item.name)) !== item.sha256) throw new Error(`Seed checksum mismatch: ${item.name}`)

const validateLoad = async (mode) => {
  const directory = path.join(root, mode)
  const summary = await loadJson(path.join(directory, 'summary.json'))
  const rawFile = path.join(directory, 'raw-samples.jsonl')
  const histogram = await loadJson(path.join(directory, 'histogram-1ms.json'))
  const samples = await readJsonLines(rawFile)
  if (summary.contractId !== CONTRACT_ID || summary.smoke || summary.mode !== mode || !summary.thresholdPass || summary.unexpectedErrors !== 0) throw new Error(`Invalid ${mode} summary`)
  if (summary.vus !== profile[mode].vus || summary.elapsedSeconds < profile[mode].minimumMeasurementSeconds) throw new Error(`${mode} concurrency/duration incomplete`)
  if (histogram.format !== 'HDR_EQUIVALENT_EXACT_1MS' || histogram.precisionMs !== 1 || !histogram.nearestRank || histogram.rawSamplesSha256 !== await fileSha256(rawFile)) throw new Error(`${mode} histogram provenance invalid`)
  const unsigned = { ...histogram }; delete unsigned.histogramSha256
  if (sha256(JSON.stringify(unsigned)) !== histogram.histogramSha256) throw new Error(`${mode} histogram checksum invalid`)
  for (const [klass, evidence] of Object.entries(histogram.classes)) {
    const values = samples.filter((sample) => sample.class === klass).map((sample) => Math.max(0, Math.round(sample.durationMs)))
    if (values.length !== evidence.samples || nearestRank(values, .75) !== evidence.p75 || nearestRank(values, .95) !== evidence.p95) throw new Error(`${mode}/${klass} histogram does not match raw samples`)
    const bucketCount = Object.values(evidence.buckets).reduce((sum, count) => sum + count, 0)
    if (bucketCount !== values.length) throw new Error(`${mode}/${klass} histogram bucket count mismatch`)
  }
  if (mode === 'read') {
    const ids = [...Array.from({ length: 11 }, (_, index) => `Q${String(index + 1).padStart(2, '0')}`), ...Array.from({ length: 7 }, (_, index) => `Q12_SC${String(index + 1).padStart(2, '0')}`)]
    for (const id of ids) if ((summary.counts[id] ?? 0) < profile.read.minimumSamplesPerQuery) throw new Error(`${id} sample floor missing`)
    const resolved = await loadJson(path.join(directory, 'resolved-query-contract.json'))
    const resolvedIds = new Set(resolved.cases?.map((item) => item.id))
    if (resolved.contractId !== CONTRACT_ID || ids.some((id) => !resolvedIds.has(id)) || resolved.cases.some((item) => item.method !== 'POST' || !item.path.endsWith('/records:query') || !item.body?.schemaVersionId || item.expected == null)) throw new Error('Resolved Q01-Q12 request contract is incomplete')
  } else for (const id of ['CREATE_ACTIVATE', 'UPDATE', 'COMMENT']) if ((summary.counts[id] ?? 0) < profile.write.minimumSamplesPerClass) throw new Error(`${id} sample floor missing`)
  return summary.metrics
}
const readMetrics = await validateLoad('read')
const writeMetrics = await validateLoad('write')
const db = await loadJson(await requireFile('db', 'db-evidence.json'))
const dbIds = new Set(db.queries?.map((item) => item.id))
const requiredDbIds = [...Array.from({ length: 11 }, (_, index) => `Q${String(index + 1).padStart(2, '0')}`), ...Array.from({ length: 7 }, (_, index) => `Q12_SC${String(index + 1).padStart(2, '0')}`)]
if (db.contractId !== CONTRACT_ID || !db.pass || db.rowsExaminedP95 > profile.thresholdsMs.rowsExaminedP95 || !db.slowLogProvided || requiredDbIds.some((id) => !dbIds.has(id) || !db.slowLogSamples.some((row) => row.id === id))) throw new Error('DB evidence is incomplete or failed')
if (db.queries.some((item) => !item.canonicalAst || !Array.isArray(item.bindings) || !item.generatedSql || !item.explainFormatFile || !item.explainAnalyzeFile || Object.values(item.findings).some(Boolean))) throw new Error('DB query evidence lacks contract artifacts or has plan violations')
for (const item of db.queries) { await requireFile('db', item.explainFormatFile); await requireFile('db', item.explainAnalyzeFile) }
const shell = await loadJson(await requireFile('browser', 'shell-summary.json'))
if (shell.contractId !== CONTRACT_ID || shell.smoke || shell.viewport?.width !== 1440 || shell.viewport?.height !== 900 || !shell.cacheClearedPerRun || shell.samples?.length < 30 || shell.p75 > profile.thresholdsMs.shellP75 || !shell.thresholdPass) throw new Error('Browser shell evidence is incomplete or failed')
for (const sample of shell.samples) await requireFile('browser', sample.traceFile)
console.log(JSON.stringify({ status: 'PASS', contractId: CONTRACT_ID, seedManifestSha256: seed.manifestSha256, read: readMetrics, write: writeMetrics, rowsExaminedP95: db.rowsExaminedP95, shellP75: shell.p75 }, null, 2))
