import type { ApiContext } from "../api/client";
import type { AccountStatus, EntityId, IsoDateTimeString } from "../api/types";

export interface AuthUser {
  accountId: EntityId;
  loginName: string;
  displayName: string;
  accountStatus: AccountStatus;
  platformRoles: string[];
  platformPermissions: string[];
  lastLoginAt?: IsoDateTimeString;
}

export interface TokenBundle {
  accessToken: string;
  refreshToken?: string;
  accessTokenExpiresAt?: IsoDateTimeString;
  refreshTokenExpiresAt?: IsoDateTimeString;
}

export type AuthStatus = "anonymous" | "authenticating" | "authenticated" | "expired";

export interface AuthState {
  status: AuthStatus;
  user?: AuthUser;
  token?: TokenBundle;
}

type AuthListener = (state: Readonly<AuthState>) => void;

export interface AuthStore {
  getState(): Readonly<AuthState>;
  subscribe(listener: AuthListener): () => void;
  beginLogin(): void;
  setAuthenticated(user: AuthUser, token: TokenBundle): void;
  updateToken(token: TokenBundle): void;
  markExpired(): void;
  logout(): void;
  hasPlatformPermission(permissionCode: string): boolean;
  toApiContext(requestId?: string): ApiContext;
}

const initialState: AuthState = {
  status: "anonymous",
};

export function createAuthStore(seed: AuthState = initialState): AuthStore {
  let state: AuthState = { ...seed };
  const listeners = new Set<AuthListener>();

  function emit(): void {
    listeners.forEach((listener) => listener(state));
  }

  function setState(next: AuthState): void {
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

    beginLogin() {
      setState({ ...state, status: "authenticating" });
    },

    setAuthenticated(user, token) {
      setState({
        status: "authenticated",
        user,
        token,
      });
    },

    updateToken(token) {
      if (state.status !== "authenticated" || !state.user) {
        setState({ status: "expired" });
        return;
      }
      setState({
        ...state,
        token,
      });
    },

    markExpired() {
      setState({
        ...state,
        status: "expired",
        token: undefined,
      });
    },

    logout() {
      setState({ status: "anonymous" });
    },

    hasPlatformPermission(permissionCode) {
      return state.user?.platformPermissions.includes(permissionCode) ?? false;
    },

    toApiContext(requestId) {
      return {
        accessToken: state.token?.accessToken,
        requestId,
      };
    },
  };
}

export const authStore = createAuthStore();
