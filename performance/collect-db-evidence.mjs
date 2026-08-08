import { spawn } from 'node:child_process'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { CONTRACT_ID, nearestRank } from './lib/evidence.mjs'

const arg = (name, fallback = null) => { const index = process.argv.indexOf(name); return index >= 0 ? process.argv[index + 1] : fallback }
const artifactsFile = path.resolve(arg('--query-artifacts') ?? (() => { throw new Error('--query-artifacts is required') })())
const output = path.resolve(arg('--output') ?? (() => { throw new Error('--output is required') })())
const defaultsFile = path.resolve(arg('--defaults-extra-file') ?? (() => { throw new Error('--defaults-extra-file is required') })())
const database = arg('--database') ?? (() => { throw new Error('--database is required') })()
const mysql = arg('--mysql', 'mysql')
const slowLogFile = arg('--slow-log') ? path.resolve(arg('--slow-log')) : null
const artifacts = JSON.parse(await readFile(artifactsFile, 'utf8'))
if (!Array.isArray(artifacts)) throw new Error('Query artifacts must be an array')
const required = [...Array.from({ length: 11 }, (_, index) => `Q${String(index + 1).padStart(2, '0')}`), ...Array.from({ length: 7 }, (_, index) => `Q12_SC${String(index + 1).padStart(2, '0')}`)]
for (const id of required) if (!artifacts.some((item) => item.id === id)) throw new Error(`Missing ${id} query artifact`)
await mkdir(output, { recursive: true })

const runMysql = (sql) => new Promise((resolve, reject) => {
  const child = spawn(mysql, [`--defaults-extra-file=${defaultsFile}`, '--batch', '--raw', '--skip-column-names', '--local-infile=1', database], { stdio: ['pipe', 'pipe', 'pipe'] })
  let stdout = ''; let stderr = ''
  child.stdout.on('data', (value) => { stdout += value })
  child.stderr.on('data', (value) => { stderr += value })
  child.on('error', reject)
  child.on('close', (code) => code === 0 ? resolve(stdout) : reject(new Error(`mysql exited ${code}: ${stderr}`)))
  child.stdin.end(sql)
})
const sections = (text) => {
  const result = {}; let current = null
  for (const line of text.split(/\r?\n/u)) {
    const match = /^__([A-Z_]+)__$/.exec(line)
    if (match) { current = match[1]; result[current] = []; continue }
    if (current && line) result[current].push(line)
  }
  return result
}
const planFindings = (formatJson, analyze) => ({
  accessTypeAll: /"access_type"\s*:\s*"ALL"/iu.test(formatJson),
  chosenKeyMissing: /"key"\s*:\s*null/iu.test(formatJson),
  diskTemporary: /disk temporary|on-disk temporary|using temporary table/iu.test(`${formatJson}\n${analyze}`),
  unboundedFilesort: /filesort/iu.test(`${formatJson}\n${analyze}`) && !/limit/iu.test(`${formatJson}\n${analyze}`),
})

const evidence = []
for (const item of artifacts) {
  if (!required.includes(item.id)) continue
  if (!item.canonicalAst || !Array.isArray(item.bindings) || !item.generatedSql || !item.executableSql || item.expectedCardinality == null) throw new Error(`${item.id} lacks AST/bindings/generated SQL/executable SQL/cardinality`)
  const sql = String(item.executableSql).trim().replace(/;$/u, '')
  if (!/^select\b/iu.test(sql) || /;/.test(sql)) throw new Error(`${item.id} executableSql must be one SELECT`)
  const tagged = `/* PERF:${item.id} */ ${sql}`
  const raw = await runMysql([
    `SELECT '__FORMAT_JSON__'; EXPLAIN FORMAT=JSON ${tagged};`,
    `SELECT '__ANALYZE__'; EXPLAIN ANALYZE ${tagged};`,
    `SELECT '__EXECUTE__'; ${tagged};`,
    `SELECT '__ROWS_EXAMINED__'; SELECT ROWS_EXAMINED FROM performance_schema.events_statements_history_long WHERE SQL_TEXT LIKE '/* PERF:${item.id} */%' ORDER BY EVENT_ID DESC LIMIT 1;`,
  ].join('\n'))
  const parsed = sections(raw)
  const formatJson = (parsed.FORMAT_JSON ?? []).join('\n')
  const analyze = (parsed.ANALYZE ?? []).join('\n')
  const rowsExamined = Number((parsed.ROWS_EXAMINED ?? []).at(-1))
  if (!formatJson || !analyze || !Number.isFinite(rowsExamined)) throw new Error(`${item.id} did not produce complete DB evidence; ensure performance_schema history_long is enabled`)
  const finding = planFindings(formatJson, analyze)
  await writeFile(path.join(output, `${item.id}-explain-format.json.txt`), `${formatJson}\n`, 'utf8')
  await writeFile(path.join(output, `${item.id}-explain-analyze.txt`), `${analyze}\n`, 'utf8')
  evidence.push({ ...item, executableSql: undefined, rowsExamined, findings: finding, explainFormatFile: `${item.id}-explain-format.json.txt`, explainAnalyzeFile: `${item.id}-explain-analyze.txt` })
}
let slowLogRows = []
if (slowLogFile) {
  const log = await readFile(slowLogFile, 'utf8')
  for (const id of required) {
    const expression = new RegExp(`# Query_time:[^\\r\\n]*Rows_examined:\\s*(\\d+)[\\s\\S]{0,2000}?/\\* PERF:${id} \\*/`, 'gu')
    for (const match of log.matchAll(expression)) slowLogRows.push({ id, rowsExamined: Number(match[1]) })
  }
}
const rows = evidence.map((item) => item.rowsExamined)
const result = {
  schemaVersion: 1, contractId: CONTRACT_ID, source: 'performance_schema.events_statements_history_long',
  slowLogProvided: Boolean(slowLogFile), slowLogSamples: slowLogRows,
  queries: evidence, rowsExaminedP95: nearestRank(rows, .95),
  pass: evidence.length === required.length && evidence.every((item) => !Object.values(item.findings).some(Boolean)) && nearestRank(rows, .95) <= 50_000 && (!slowLogFile || required.every((id) => slowLogRows.some((row) => row.id === id))),
}
await writeFile(path.join(output, 'db-evidence.json'), `${JSON.stringify(result, null, 2)}\n`, 'utf8')
console.log(JSON.stringify({ output, pass: result.pass, rowsExaminedP95: result.rowsExaminedP95 }, null, 2))
if (!result.pass) process.exitCode = 1
