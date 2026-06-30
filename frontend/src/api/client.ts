import type { ApiResponse, PageRequest, PageResult } from './types';

export interface ApiClientOptions {
  baseUrl?: string;
  getToken?: () => string | undefined;
  getTraceId?: () => string | undefined;
}

export class ApiClient {
  private readonly baseUrl: string;
  private readonly getToken?: () => string | undefined;
  private readonly getTraceId?: () => string | undefined;

  constructor(options: ApiClientOptions = {}) {
    this.baseUrl = options.baseUrl ?? '';
    this.getToken = options.getToken;
    this.getTraceId = options.getTraceId;
  }

  async get<T>(path: string): Promise<ApiResponse<T>> {
    return this.request<T>(path, { method: 'GET' });
  }

  async post<T>(path: string, body?: unknown, idempotencyKey?: string): Promise<ApiResponse<T>> {
    return this.request<T>(path, {
      method: 'POST',
      body: body === undefined ? undefined : JSON.stringify(body),
      headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined,
    });
  }

  async postForm<T>(path: string, body: FormData, idempotencyKey?: string): Promise<ApiResponse<T>> {
    return this.request<T>(
      path,
      {
        method: 'POST',
        body,
        headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined,
      },
      false,
    );
  }

  async patch<T>(path: string, body?: unknown, idempotencyKey?: string): Promise<ApiResponse<T>> {
    return this.request<T>(path, {
      method: 'PATCH',
      body: body === undefined ? undefined : JSON.stringify(body),
      headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined,
    });
  }

  async put<T>(path: string, body?: unknown, idempotencyKey?: string): Promise<ApiResponse<T>> {
    return this.request<T>(path, {
      method: 'PUT',
      body: body === undefined ? undefined : JSON.stringify(body),
      headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined,
    });
  }

  async delete<T>(path: string, idempotencyKey?: string, body?: unknown): Promise<ApiResponse<T>> {
    return this.request<T>(path, {
      method: 'DELETE',
      body: body === undefined ? undefined : JSON.stringify(body),
      headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined,
    });
  }

  async search<T>(path: string, page: PageRequest): Promise<ApiResponse<PageResult<T>>> {
    return this.post<PageResult<T>>(path, page);
  }

  private async request<T>(path: string, init: RequestInit, jsonContentType = true): Promise<ApiResponse<T>> {
    const headers = new Headers(init.headers);
    if (jsonContentType) {
      headers.set('Content-Type', 'application/json');
    }
    const token = this.getToken?.();
    const traceId = this.getTraceId?.();
    if (token) {
      headers.set('Authorization', `Bearer ${token}`);
    }
    if (traceId) {
      headers.set('X-Trace-Id', traceId);
    }

    const response = await fetch(`${this.baseUrl}${path}`, { ...init, headers });
    return (await response.json()) as ApiResponse<T>;
  }
}

function configuredApiBaseUrl(): string {
  const runtimeBaseUrl = window.__UNEXAMINE_API_BASE_URL__?.trim() ?? '';
  return runtimeBaseUrl.endsWith('/') ? runtimeBaseUrl.slice(0, -1) : runtimeBaseUrl;
}

export const apiClient = new ApiClient({
  baseUrl: configuredApiBaseUrl(),
  getToken: () => localStorage.getItem('unexamine.accessToken') ?? undefined,
});
