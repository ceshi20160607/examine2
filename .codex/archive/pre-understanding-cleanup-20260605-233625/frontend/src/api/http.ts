import axios, { AxiosError } from 'axios';
import { API_SUCCESS_CODE } from './enums';
import type { ApiResult, RequestContext } from './types';
import { getRequestContext } from '../store/context';

export class ApiError extends Error {
  code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
  }
}

export class MissingContextError extends ApiError {
  fields: Array<keyof RequestContext>;

  constructor(fields: Array<keyof RequestContext>) {
    super('MISSING_CONTEXT', `缺少上下文：${fields.join(', ')}`);
    this.name = 'MissingContextError';
    this.fields = fields;
  }
}

export const http = axios.create({
  baseURL: '',
  timeout: 15000
});

http.interceptors.request.use((config) => {
  const context = getRequestContext();
  config.headers = config.headers ?? {};
  if (context.accountId) {
    config.headers['X-Account-Id'] = String(context.accountId);
  }
  if (context.tenantId) {
    config.headers['X-Tenant-Id'] = String(context.tenantId);
  }
  if (context.systemId) {
    config.headers['X-System-Id'] = String(context.systemId);
  }
  return config;
});

export async function request<T>(promise: Promise<{ data: ApiResult<T> }>): Promise<T> {
  try {
    const response = await promise;
    if (response.data.code !== API_SUCCESS_CODE) {
      throw new ApiError(response.data.code, response.data.message || '接口返回失败');
    }
    return response.data.data;
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    const axiosError = error as AxiosError<ApiResult<unknown>>;
    const code = axiosError.response?.data?.code || 'NETWORK_ERROR';
    const message = axiosError.response?.data?.message || axiosError.message || '网络请求失败';
    throw new ApiError(code, message);
  }
}

export function withContext<T extends Record<string, unknown>>(
  body: T,
  required: Array<keyof RequestContext>
): T {
  const context = getRequestContext();
  const missing = required.filter((key) => body[key as string] == null && context[key] == null);
  if (missing.length > 0) {
    throw new MissingContextError(missing);
  }
  return required.reduce(
    (next, key) => ({
      ...next,
      [key]: next[key as string] ?? context[key]
    }),
    body
  );
}

export function requireContext(required: Array<keyof RequestContext>): RequestContext {
  const context = getRequestContext();
  const missing = required.filter((key) => context[key] == null);
  if (missing.length > 0) {
    throw new MissingContextError(missing);
  }
  return context;
}
