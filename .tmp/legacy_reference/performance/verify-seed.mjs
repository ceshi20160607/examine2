import path from 'node:path'
import { createReadStream } from 'node:fs'
import readline from 'node:readline'
import { CONTRACT_ID, CONTRACT_SHA256, fileSha256, loadJson, sha256, stableJson } from './lib/evidence.mjs'

const arg = (name, fallback = null) => { const index = process.argv.indexOf(name); return index >= 0 ? process.argv[index + 1] : fallback }
const release = process.argv.includes('--release')
const directory = path.resolve(arg('--input') ?? (() => { throw new Error('--input is required') })())
const manifest = await loadJson(path.join(directory, 'seed-manifest.json'))
if (manifest.contractId !== CONTRACT_ID || manifest.seedSha256 !== CONTRACT_SHA256 || sha256(CONTRACT_ID) !== CONTRACT_SHA256) throw new Error('Seed contract mismatch')
const unsigned = { ...manifest }; delete unsigned.manifestSha256
if (sha256(stableJson(unsigned)) !== manifest.manifestSha256) throw new Error('Manifest checksum mismatch')
const implementationRoot = path.resolve(import.meta.dirname)
if (manifest.generatorSha256 !== await fileSha256(path.join(implementationRoot, 'generate-seed.mjs')) || manifest.specSha256 !== await fileSha256(path.join(implementationRoot, 'seed/seed-spec.json'))) throw new Error('Manifest was produced by a different seed implementation')
const scanTsv = async (file, idColumn, expectedColumns) => {
  let rows = 0; let minId = null; let maxId = null
  const input = readline.createInterface({ input: createReadStream(file), crlfDelay: Infinity })
  for await (const line of input) {
    if (!line) continue
    const columns = line.split('\t')
    if (columns.length !== expectedColumns) throw new Error(`Invalid column count in ${file} line ${rows + 1}: expected ${expectedColumns}, got ${columns.length}`)
    const id = Number(columns[idColumn])
    if (!Number.isSafeInteger(id)) throw new Error(`Invalid ID in ${file} line ${rows + 1}`)
    rows++; minId ??= id; maxId = id
  }
  return { rows, minId, maxId }
}
for (const [table, item] of Object.entries(manifest.tables ?? {})) {
  if (!item.name || !Number.isSafeInteger(item.rows) || item.rows < 1 || item.minId == null || item.maxId == null) throw new Error(`Invalid ${table} manifest entry`)
  const file = path.join(directory, item.name)
  if (await fileSha256(file) !== item.sha256) throw new Error(`${table} file checksum mismatch`)
  if (!Array.isArray(item.columns) || item.columns.length < 1) throw new Error(`${table} column manifest missing`)
  const actual = await scanTsv(file, item.idColumn ?? 0, item.columns.length)
  if (actual.rows !== item.rows || actual.minId !== item.minId || actual.maxId !== item.maxId) throw new Error(`${table} row/min/max mismatch`)
}
if (manifest.tables.records?.rows !== manifest.recordCount) throw new Error('Record count mismatch')
const count = manifest.recordCount
const relationRecords = Math.floor(count / 10) * 3 + Math.min(count % 10, 2)
const expectedForCount = { accounts: 10_000, members: 10_000, memberTenants: 10_000, departments: 50, departmentClosure: 139, memberDepartments: 10_000, records: count, values: count * 20, indexes: count * 9, search: count, relations: relationRecords * 2, subRecords: Math.floor(count / 5) * 5, subValues: Math.floor(count / 5) * 5, referenceStates: relationRecords, teams: Math.floor((count + 7) / 10), teamMembers: Math.floor((count + 7) / 10) * 3, comments: Math.floor((count + 6) / 10) * 5 }
for (const [table, rows] of Object.entries(expectedForCount)) if (manifest.tables[table]?.rows !== rows) throw new Error(`${table} deterministic distribution must contain ${rows} rows`)
if (release) {
  if (manifest.recordCount !== 1_000_000 || !manifest.releaseEligible) throw new Error('Reduced seed cannot be release evidence')
  if (manifest.shape?.archivedRecords !== 100_000 || manifest.shape?.members !== 10_000 || manifest.shape?.departments !== 50 || manifest.shape?.relationRecords !== 300_000 || manifest.shape?.subtableRecords !== 200_000 || manifest.shape?.teamRecords !== 100_000 || manifest.shape?.commentRecords !== 100_000) throw new Error('Release seed distribution is incomplete')
}
console.log(JSON.stringify({ status: 'PASS', release, recordCount: manifest.recordCount, manifestSha256: manifest.manifestSha256 }, null, 2))
