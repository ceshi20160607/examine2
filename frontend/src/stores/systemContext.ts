import type { EntityId, IsoDateTimeString, SystemStatus, TenantStatus } from "../api/types";

export type TenantMode = "SINGLE" | "MULTI";
export type ContextStatus = "empty" | "loading" | "ready" | "disabled" | "stale" | "error";

export interface SystemSummary {
  systemId: EntityId;
  systemCode?: string;
  systemName: string;
  tenantMode: TenantMode;
  status: SystemStatus;
}

export interface TenantSummary {
  tenantId: EntityId;
  tenantCode?: string;
  tenantName: string;
  status: TenantStatus;
}

export interface MemberSummary {
  memberId: EntityId;
  displayName: string;
  roles: string[];
  status?: "ENABLED" | "DISABLED";
}

export interface SystemContextSnapshot {
  system: SystemSummary;
  tenant?: TenantSummary;
  member: MemberSummary;
  runtimeHomePage?: string;
  enteredAt?: IsoDateTimeString;
}

export interface SystemContextState {
  status: ContextStatus;
  current?: SystemContextSnapshot;
  missing: ContextMissingKey[];
  disabledReason?: string;
  staleReason?: string;
}

export type ContextMissingKey = "systemId" | "tenantId" | "memberId";

export interface RequiredContext {
  system?: boolean;
  tenant?: boolean;
  member?: boolean;
}

type SystemContextListener = (state: Readonly<SystemContextState>) => void;

export interface SystemContextStore {
  getState(): Readonly<SystemContextState>;
  subscribe(listener: SystemContextListener): () => void;
  beginEnter(systemId: EntityId): void;
  setContext(snapshot: SystemContextSnapshot): void;
  markStale(reason: string): void;
  clear(): void;
  validate(required: RequiredContext): ContextMissingKey[];
  toPathParams(extra?: Record<string, string | number | undefined>): Record<string, string | number>;
  toTenantHeader(): string | undefined;
}

const initialState: SystemContextState = {
  status: "empty",
  missing: ["systemId", "memberId"],
};

export function createSystemContextStore(seed: SystemContextState = initialState): SystemContextStore {
  let state: SystemContextState = { ...seed, missing: [...seed.missing] };
  const listeners = new Set<SystemContextListener>();

  function emit(): void {
    listeners.forEach((listener) => listener(state));
  }

  function setState(next: SystemContextState): void {
    state = next;
    emit();
  }

  function computeMissing(snapshot: SystemContextSnapshot | undefined): ContextMissingKey[] {
    const missing: ContextMissingKey[] = [];
    if (!snapshot?.system.systemId) {
      missing.push("systemId");
    }
    if (!snapshot?.member.memberId) {
      missing.push("memberId");
    }
    if (snapshot?.system.tenantMode === "MULTI" && !snapshot.tenant?.tenantId) {
      missing.push("tenantId");
    }
    return missing;
  }

  return {
    getState() {
      return state;
    },

    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },

    beginEnter(systemId) {
      setState({
        status: "loading",
        current: {
          system: {
            systemId,
            systemName: "",
            tenantMode: "SINGLE",
            status: "DRAFT",
          },
          member: {
            memberId: "",
            displayName: "",
            roles: [],
          },
        },
        missing: ["memberId"],
      });
    },

    setContext(snapshot) {
      const missing = computeMissing(snapshot);
      const systemDisabled = snapshot.system.status === "DISABLED" || snapshot.system.status === "ARCHIVED";
      const tenantDisabled = snapshot.tenant?.status === "DISABLED";
      const memberDisabled = snapshot.member.status === "DISABLED";
      setState({
        status: systemDisabled || tenantDisabled || memberDisabled ? "disabled" : "ready",
        current: snapshot,
        missing,
        disabledReason: systemDisabled
          ? "SYS_DISABLED/SYS_ARCHIVED"
          : tenantDisabled
            ? "SYS_TENANT_DISABLED"
            : memberDisabled
              ? "SYS_MEMBER_DISABLED"
              : undefined,
      });
    },

    markStale(reason) {
      setState({
        ...state,
        status: state.current ? "stale" : "empty",
        staleReason: reason,
      });
    },

    clear() {
      setState({ ...initialState, missing: [...initialState.missing] });
    },

    validate(required) {
      const missing = computeMissing(state.current);
      return missing.filter((key) => {
        if (key === "systemId") {
          return required.system;
        }
        if (key === "tenantId") {
          return required.tenant;
        }
        return required.member;
      });
    },

    toPathParams(extra = {}) {
      const params: Record<string, string | number> = {};
      if (state.current?.system.systemId) {
        params.systemId = state.current.system.systemId;
      }
      Object.entries(extra).forEach(([key, value]) => {
        if (value !== undefined && value !== "") {
          params[key] = value;
        }
      });
      return params;
    },

    toTenantHeader() {
      return state.current?.tenant?.tenantId;
    },
  };
}

export const systemContextStore = createSystemContextStore();
