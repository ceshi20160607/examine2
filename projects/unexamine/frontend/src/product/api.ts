/** 当前页面使用的公开账号和系统字段。 */
export interface Account { id: number; username: string; displayName: string }
export interface System { id: number; code: string; name: string; description: string | null; status: string; owner: boolean }

/** 同源请求由 Vite 转发至固定 9999 后端；Cookie 由浏览器管理。 */
export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`/api${path}`, {
    ...options,
    headers: options.body instanceof FormData ? options.headers : { 'Content-Type': 'application/json', ...options.headers },
  })
  if (!response.ok) {
    const error = await response.json().catch(() => null)
    const message = error?.detail || error?.message || `操作失败（${response.status}）`
    throw new Error(message)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export function send<T>(path: string, body?: object): Promise<T> {
  return api<T>(path, { method: 'POST', body: body ? JSON.stringify(body) : undefined })
}
