import type { Page } from '@playwright/test';

/** 在页面内带 token 调 API；返回 data，id 类字段为 string。 */
export async function postApi<T = Record<string, unknown>>(
  page: Page,
  path: string,
  body?: unknown
): Promise<T> {
  return page.evaluate(
    async ({ path, body }) => {
      const token = window.localStorage.getItem('examine_token') || '';
      const res = await fetch(path, {
        method: body === undefined ? 'GET' : 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: body === undefined ? undefined : JSON.stringify(body),
      });
      const text = await res.text();
      const patched = text.replace(/"([A-Za-z]*[iI]d|[iI]d)":\s*(\d{15,})/g, '"$1":"$2"');
      const json = JSON.parse(patched);
      if (!json || json.code !== 0) {
        const msg = json?.message || `HTTP error`;
        throw new Error(`${msg} (${path}, code=${json?.code ?? '?'})`);
      }
      return json.data;
    },
    { path, body }
  ) as Promise<T>;
}
