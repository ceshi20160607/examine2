import { defineConfig, devices } from '@playwright/test';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
// 与 vite --host 127.0.0.1 一致；localhost 在部分 Windows 环境下代理 /v1 会异常
const baseURL = process.env.WEB_BASE_URL || 'http://127.0.0.1:5173';
const authFile = path.join(__dirname, '.auth', 'admin.json');

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  timeout: 60_000,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'setup', testMatch: /auth\.setup\.ts/ },
    {
      name: 'chromium',
      testIgnore: [/auth\.setup\.ts/, /auth\.spec\.ts/],
      dependencies: ['setup'],
      use: { ...devices['Desktop Chrome'], storageState: authFile },
    },
    {
      name: 'chromium-auth',
      testMatch: /auth\.spec\.ts/,
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  ...(process.env.E2E_SKIP_WEB_SERVER
    ? {}
    : {
        webServer: {
          command: 'npm run dev',
          cwd: '../../web/vue3',
          url: baseURL,
          reuseExistingServer: !process.env.CI,
          timeout: 180_000,
        },
      }),
});
