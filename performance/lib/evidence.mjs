import { createHash } from 'node:crypto'
import { createReadStream } from 'node:fs'
import { readFile } from 'node:fs/promises'
import readline from 'node:readline'

export const CONTRACT_ID = 'VS4-PERF-SEED-V1:20260715'
export const CONTRACT_SHA256 = 'b89b13fac77ca7f67d3ab8cde1d0d2d47f7681c4a5a16fd1218ff4cc668494f0'

export const sha256 = (value) => createHash('sha256').update(value).digest('hex')

export const fileSha256 = async (file) => {
  const hash = createHash('sha256')
  for await (const chunk of createReadStream(file)) hash.update(chunk)
  return hash.digest('hex')
}

export const loadJson = async (file) => JSON.parse(await readFile(file, 'utf8'))

export const stableJson = (value) => {
  if (Array.isArray(value)) return `[${value.map(stableJson).join(',')}]`
  if (value && typeof value === 'object') return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${stableJson(value[key])}`).join(',')}}`
  return JSON.stringify(value)
}

export const nearestRank = (values, percentile) => {
  if (!values.length) return null
  const sorted = [...values].sort((a, b) => a - b)
  return sorted[Math.max(0, Math.ceil(sorted.length * percentile) - 1)]
}

export const readJsonLines = async (file) => {
  const values = []
  const input = readline.createInterface({ input: createReadStream(file), crlfDelay: Infinity })
  for await (const line of input) if (line.trim()) values.push(JSON.parse(line))
  return values
}

export const histogram = (samples, selector = (sample) => sample.durationMs) => {
  const groups = new Map()
  for (const sample of samples) {
    const key = sample.class
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key).push(Math.max(0, Math.round(selector(sample))))
  }
  return Object.fromEntries([...groups].map(([key, values]) => {
    const buckets = Object.fromEntries([...values.reduce((map, value) => map.set(value, (map.get(value) ?? 0) + 1), new Map())].sort((a, b) => a[0] - b[0]))
    return [key, { count: values.length, min: Math.min(...values), max: Math.max(...values), p75: nearestRank(values, .75), p95: nearestRank(values, .95), buckets }]
  }))
}
