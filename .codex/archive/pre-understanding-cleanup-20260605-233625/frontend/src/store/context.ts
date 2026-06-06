import { create } from 'zustand';
import type { Id, RequestContext } from '../api/types';

const STORAGE_KEY = 'unique-examine-context';

interface SessionState extends RequestContext {
  displayName?: string;
  username?: string;
  hydrate: () => void;
  setLogin: (payload: { accountId?: Id; username?: string; displayName?: string; tenantId?: Id; systemId?: Id }) => void;
  setContext: (payload: Partial<RequestContext>) => void;
  clear: () => void;
}

function readStoredContext(): Partial<SessionState> {
  const raw = window.localStorage.getItem(STORAGE_KEY);
  if (!raw) {
    return {};
  }
  try {
    return JSON.parse(raw) as Partial<SessionState>;
  } catch {
    window.localStorage.removeItem(STORAGE_KEY);
    return {};
  }
}

function persistContext(state: Partial<SessionState>) {
  window.localStorage.setItem(
    STORAGE_KEY,
    JSON.stringify({
      accountId: state.accountId,
      tenantId: state.tenantId,
      systemId: state.systemId,
      appId: state.appId,
      moduleId: state.moduleId,
      username: state.username,
      displayName: state.displayName
    })
  );
}

export const useSessionStore = create<SessionState>((set, get) => ({
  ...readStoredContext(),
  hydrate: () => set(readStoredContext()),
  setLogin: (payload) => {
    const next = {
      ...get(),
      accountId: payload.accountId,
      username: payload.username,
      displayName: payload.displayName,
      tenantId: payload.tenantId ?? get().tenantId,
      systemId: payload.systemId ?? get().systemId
    };
    persistContext(next);
    set(next);
  },
  setContext: (payload) => {
    const next = { ...get(), ...payload };
    persistContext(next);
    set(next);
  },
  clear: () => {
    window.localStorage.removeItem(STORAGE_KEY);
    set({
      accountId: undefined,
      tenantId: undefined,
      systemId: undefined,
      appId: undefined,
      moduleId: undefined,
      username: undefined,
      displayName: undefined
    });
  }
}));

export function getRequestContext(): RequestContext {
  const state = useSessionStore.getState();
  return {
    accountId: state.accountId,
    tenantId: state.tenantId,
    systemId: state.systemId,
    appId: state.appId,
    moduleId: state.moduleId
  };
}
