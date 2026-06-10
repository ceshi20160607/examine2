import type { HttpTransport, TransportRequestConfig } from "./client";
import type { ApiResult } from "./types";

export interface FetchTransportOptions {
  baseUrl?: string;
  fetchImpl?: typeof fetch;
}

export function createFetchTransport(options: FetchTransportOptions = {}): HttpTransport {
  const fetchImpl = options.fetchImpl ?? fetch;
  const baseUrl = trimTrailingSlash(options.baseUrl ?? "");

  return {
    async request<TData>(config: TransportRequestConfig): Promise<{ data: ApiResult<TData> }> {
      const url = buildUrl(baseUrl, config.url, config.params);
      const multipart = isFormData(config.data);
      const response = await fetchImpl(url, {
        method: config.method,
        headers: {
          ...(multipart ? {} : { "Content-Type": "application/json" }),
          ...config.headers,
        },
        body: config.data === undefined ? undefined : multipart ? config.data as BodyInit : JSON.stringify(config.data),
      });

      const data = (await response.json()) as ApiResult<TData>;
      return { data };
    },
  };
}

function isFormData(value: unknown): value is FormData {
  return typeof FormData !== "undefined" && value instanceof FormData;
}

function buildUrl(baseUrl: string, path: string, params: unknown): string {
  const url = `${baseUrl}${path}`;
  const query = new URLSearchParams();
  if (params && typeof params === "object") {
    Object.entries(params as Record<string, unknown>).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== "") {
        query.set(key, String(value));
      }
    });
  }
  const queryString = query.toString();
  return queryString ? `${url}?${queryString}` : url;
}

function trimTrailingSlash(value: string): string {
  return value.endsWith("/") ? value.slice(0, -1) : value;
}
