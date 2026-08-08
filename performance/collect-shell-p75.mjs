import { mkdir, readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { pathToFileURL } from 'node:url'
import { performance } from 'node:perf_hooks'
import { CONTRACT_ID, nearestRank } from './lib/evidence.mjs'

const arg = (name, fallback = null) => { const index = process.argv.indexOf(name); return index >= 0 ? process.argv[index + 1] : fallback }
const smoke = process.argv.includes('--smoke')
const root = path.resolve(import.meta.dirname, '..')
const playwrightPath = path.join(root, 'frontend/node_modules/playwright/index.mjs')
const { chromium } = await import(pathToFileURL(playwrightPath).href)
const baseUrl = arg('--base-url') ?? (() => { throw new Error('--base-url is required') })()
const route = arg('--route') ?? (() => { throw new Error('--route is required') })()
const readySelector = arg('--ready-selector') ?? (() => { throw new Error('--ready-selector is required (critical list interactive marker)') })()
const storageState = path.resolve(arg('--storage-state') ?? (() => { throw new Error('--storage-state is required') })())
const output = path.resolve(arg('--output') ?? (() => { throw new Error('--output is required') })())
const executablePath = arg('--browser-executable')
const runs = smoke ? Math.min(Number(arg('--runs', '3')), 3) : Number(arg('--runs', '30'))
if (!smoke && runs < 30) throw new Error('Release shell evidence requires at least 30 runs')
JSON.parse(await readFile(storageState, 'utf8'))
await mkdir(path.join(output, 'traces'), { recursive: true })
const browser = await chromium.launch(executablePath ? { executablePath } : {})
const samples = []
try {
  for (let index = 0; index < runs; index++) {
    // A fresh context makes browser HTTP/cache storage empty while preserving authenticated cookies from storageState.
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, storageState })
    await context.tracing.start({ screenshots: true, snapshots: true, sources: false })
    const page = await context.newPage()
    const started = performance.now()
    await page.goto(new URL(route, baseUrl).toString(), { waitUntil: 'domcontentloaded' })
    const ready = page.locator(readySelector)
    await ready.waitFor({ state: 'visible', timeout: 30_000 })
    await ready.evaluate((element) => {
      if (element instanceof HTMLButtonElement && element.disabled) throw new Error('Critical ready element is disabled')
      const style = getComputedStyle(element)
      if (style.pointerEvents === 'none') throw new Error('Critical ready element is not interactive')
    })
    const durationMs = Math.round(performance.now() - started)
    const traceFile = `traces/shell-${String(index + 1).padStart(2, '0')}.zip`
    await context.tracing.stop({ path: path.join(output, traceFile) })
    samples.push({ run: index + 1, durationMs, traceFile })
    await context.close()
  }
} finally { await browser.close() }
const p75 = nearestRank(samples.map((item) => item.durationMs), .75)
const summary = { schemaVersion: 1, contractId: CONTRACT_ID, smoke, viewport: { width: 1440, height: 900 }, cacheClearedPerRun: true, samples, p75, thresholdMs: 2500, thresholdPass: p75 <= 2500 }
await writeFile(path.join(output, 'shell-summary.json'), `${JSON.stringify(summary, null, 2)}\n`, 'utf8')
console.log(JSON.stringify({ output, smoke, runs, p75, thresholdPass: summary.thresholdPass }, null, 2))
if (!summary.thresholdPass) process.exitCode = 1
