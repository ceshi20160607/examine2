import { test as setup, expect } from '@playwright/test';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const authFile = path.join(__dirname, '..', '.auth', 'admin.json');
const apiBase = (process.env.EXAMINE_HOST || 'http://127.0.0.1:9999').replace(/\/$/, '');
const TOKEN_KEY = 'examine_token';

setup('authenticate as admin', async ({ page, request }) => {
  const user = process.env.SMOKE_USER || 'admin';
  const pass = process.env.SMOKE_PASS || '123123aa';

  const res = await request.post(`${apiBase}/v1/platform/auth/login`, {
    data: { username: user, password: pass },
  });
  expect(res.ok()).toBeTruthy();
  const json = await res.json();
  expect(json.code).toBe(0);
  const token = json.data?.token;
  expect(token).toBeTruthy();

  await page.goto('/login');
  await page.evaluate(
    ([key, value]) => localStorage.setItem(key, value),
    [TOKEN_KEY, token]
  );
  await page.goto('/systems');
  await expect(page.getByRole('heading', { name: '选择系统' })).toBeVisible({ timeout: 15_000 });

  fs.mkdirSync(path.dirname(authFile), { recursive: true });
  await page.context().storageState({ path: authFile });
});
