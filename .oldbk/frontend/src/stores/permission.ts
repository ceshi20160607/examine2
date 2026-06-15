import type { ApiEndpointId } from "../api/endpoints";
import type { AvailableAction, EffectivePermissionVO, FieldPermission, PermissionHint } from "../api/types";

export interface PermissionRequirement {
  anyOperations?: string[];
  allOperations?: string[];
  menu?: string;
  apiIds?: ApiEndpointId[];
}

export interface PermissionDecision {
  visible: boolean;
  enabled: boolean;
  disabledReason?: string;
  matchedPermission?: string;
}

export interface PermissionState {
  loading: boolean;
  stale: boolean;
  version?: number;
  effective?: EffectivePermissionVO;
  runtimeMenus: string[];
}

type PermissionListener = (state: Readonly<PermissionState>) => void;

export interface PermissionStore {
  getState(): Readonly<PermissionState>;
  subscribe(listener: PermissionListener): () => void;
  beginRefresh(): void;
  setEffectivePermission(permission: EffectivePermissionVO, runtimeMenus?: string[]): void;
  markStale(): void;
  clear(): void;
  hasOperation(operationCode: string): boolean;
  hasMenu(menuCode: string): boolean;
  decide(requirement?: PermissionRequirement): PermissionDecision;
  action(actionCode: string): PermissionDecision;
  field(fieldCode: string): FieldPermission | undefined;
  hint(permissionCode: string): PermissionHint | undefined;
}

const initialState: PermissionState = {
  loading: false,
  stale: true,
  runtimeMenus: [],
};

export function createPermissionStore(seed: PermissionState = initialState): PermissionStore {
  let state: PermissionState = {
    ...seed,
    runtimeMenus: [...seed.runtimeMenus],
  };
  const listeners = new Set<PermissionListener>();

  function emit(): void {
    listeners.forEach((listener) => listener(state));
  }

  function setState(next: PermissionState): void {
    state = next;
    emit();
  }

  function operations(): string[] {
    return state.effective?.operations ?? [];
  }

  function menus(): string[] {
    return state.runtimeMenus.length > 0 ? state.runtimeMenus : (state.effective?.menus ?? []);
  }

  function grantsOperation(operationCode: string): boolean {
    const granted = operations();
    if (granted.includes(operationCode) || granted.includes("*")) {
      return true;
    }
    if (!granted.includes("SYS_MANAGE_ALL")) {
      return false;
    }
    return !operationCode.startsWith("PLAT_") && !operationCode.startsWith("OPS_");
  }

  return {
    getState() {
      return state;
    },

    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },

    beginRefresh() {
      setState({ ...state, loading: true });
    },

    setEffectivePermission(permission, runtimeMenus = []) {
      setState({
        loading: false,
        stale: false,
        version: permission.version,
        effective: permission,
        runtimeMenus,
      });
    },

    markStale() {
      setState({ ...state, stale: true });
    },

    clear() {
      setState({ ...initialState, runtimeMenus: [] });
    },

    hasOperation(operationCode) {
      return grantsOperation(operationCode);
    },

    hasMenu(menuCode) {
      return menus().includes(menuCode);
    },

    decide(requirement) {
      if (!requirement) {
        return { visible: true, enabled: true };
      }

      if (requirement.menu && !menus().includes(requirement.menu)) {
        return {
          visible: false,
          enabled: false,
          disabledReason: "PERM_DENIED",
          matchedPermission: requirement.menu,
        };
      }

      if (requirement.allOperations?.some((item) => !grantsOperation(item))) {
        return {
          visible: true,
          enabled: false,
          disabledReason: "PERM_DENIED",
        };
      }

      if (requirement.anyOperations && !requirement.anyOperations.some((item) => grantsOperation(item))) {
        return {
          visible: true,
          enabled: false,
          disabledReason: "PERM_DENIED",
        };
      }

      return { visible: true, enabled: true };
    },

    action(actionCode) {
      const action = state.effective?.availableActions.find((item) => item.actionCode === actionCode);
      if (!action) {
        if (grantsOperation(actionCode)) {
          return {
            visible: true,
            enabled: true,
            matchedPermission: actionCode,
          };
        }
        return {
          visible: false,
          enabled: false,
          disabledReason: "PERM_DENIED",
        };
      }
      return {
        visible: action.visible,
        enabled: action.enabled,
        disabledReason: action.disabledReason ?? action.stateReason,
        matchedPermission: action.requiredPermission,
      };
    },

    field(fieldCode) {
      return state.effective?.fieldPermissions.find((item) => item.fieldCode === fieldCode);
    },

    hint(permissionCode) {
      return state.effective?.availableActions
        .map<PermissionHint>((item) => ({
          permissionCode: item.requiredPermission ?? item.actionCode,
          granted: item.visible && item.enabled,
          message: item.disabledReason ?? item.stateReason,
        }))
        .find((item) => item.permissionCode === permissionCode);
    },
  };
}

export const permissionStore = createPermissionStore();
