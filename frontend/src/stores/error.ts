import { ApiClientError } from "../api/client";
import { isKnownApiErrorCode, type ApiErrorCode } from "../api/errorCodes";
import type { ApiErrorDetail, JsonValue } from "../api/types";

export type AppErrorLevel = "info" | "warning" | "error";

export interface RequestErrorDisplay {
  level: AppErrorLevel;
  code: ApiErrorCode | string;
  message: string;
  requestId?: string;
  retryable: boolean;
  details: ApiErrorDetail[];
  raw?: JsonValue;
}

export interface ErrorState {
  latest?: RequestErrorDisplay;
  history: RequestErrorDisplay[];
}

type ErrorListener = (state: Readonly<ErrorState>) => void;

export interface ErrorStore {
  getState(): Readonly<ErrorState>;
  subscribe(listener: ErrorListener): () => void;
  capture(error: unknown): RequestErrorDisplay;
  push(error: RequestErrorDisplay): void;
  clearLatest(): void;
  clearAll(): void;
}

const initialState: ErrorState = {
  history: [],
};

export function normalizeError(error: unknown): RequestErrorDisplay {
  if (error instanceof ApiClientError) {
    const raw = error.details;
    const response =
      raw && typeof raw === "object" && !Array.isArray(raw) ? (raw as { errors?: unknown }) : undefined;
    const details = Array.isArray(response?.errors) ? (response.errors as ApiErrorDetail[]) : [];
    return {
      level: error.retryable ? "warning" : "error",
      code: isKnownApiErrorCode(error.code) ? error.code : error.code,
      message: error.message,
      requestId: error.requestId,
      retryable: error.retryable,
      details,
      raw,
    };
  }

  if (error instanceof Error) {
    return {
      level: "error",
      code: "COMMON_PARAM_INVALID",
      message: error.message,
      retryable: false,
      details: [],
    };
  }

  return {
    level: "error",
    code: "COMMON_PARAM_INVALID",
    message: "Unknown request error",
    retryable: false,
    details: [],
  };
}

export function createErrorStore(seed: ErrorState = initialState): ErrorStore {
  let state: ErrorState = {
    ...seed,
    history: [...seed.history],
  };
  const listeners = new Set<ErrorListener>();

  function emit(): void {
    listeners.forEach((listener) => listener(state));
  }

  function setState(next: ErrorState): void {
    state = next;
    emit();
  }

  return {
    getState() {
      return state;
    },

    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },

    capture(error) {
      const display = normalizeError(error);
      this.push(display);
      return display;
    },

    push(error) {
      setState({
        latest: error,
        history: [error, ...state.history].slice(0, 20),
      });
    },

    clearLatest() {
      setState({
        ...state,
        latest: undefined,
      });
    },

    clearAll() {
      setState({ history: [] });
    },
  };
}

export const errorStore = createErrorStore();
