import { defineConfig, devices } from '@playwright/test'

const managedWebServer = process.env.E2E_MANAGED_WEB_SERVER === 'true'
const managedBaseUrl = process.env.E2E_BASE_URL ?? 'http://127.0.0.1:5173'

export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: false,
  workers: process.env.E2E_WORKERS ? Number(process.env.E2E_WORKERS) : 1,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }], ['json', { outputFile: 'test-results/results.json' }], ['junit', { outputFile: 'test-results/results.xml' }]],
  webServer: managedWebServer ? {
    command: `node node_modules/vite/bin/vite.js --port ${process.env.E2E_WEB_PORT ?? '5173'}`,
    url: managedBaseUrl,
    timeout: 120_000,
    reuseExistingServer: false,
    env: {
      VITE_API_PROXY_TARGET: process.env.VITE_API_PROXY_TARGET ?? 'http://127.0.0.1:18080',
    },
  } : undefined,
  use: {
    baseURL: managedBaseUrl,
    launchOptions: process.env.E2E_BROWSER_EXECUTABLE
      ? { executablePath: process.env.E2E_BROWSER_EXECUTABLE }
      : undefined,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } } },
    { name: 'desktop-compact', testMatch: /(vs3|p4-a1)\.spec\.ts/, use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 720 } } },
    { name: 'mobile', use: { ...devices['Pixel 7'], viewport: { width: 390, height: 844 } } },
  ],
})
