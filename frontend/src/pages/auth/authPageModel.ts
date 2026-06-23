import type { ApiClient } from "../../api/client";
import type { AccountStatus, EntityId, IsoDateTimeString } from "../../api/types";
import { DEFAULT_AUTHENTICATED_ROUTE, LOGIN_ROUTE } from "../../router";
import {
  authStore,
  errorStore,
  permissionStore,
  systemContextStore,
  type AuthStore,
  type AuthUser,
  type ErrorStore,
  type RequestErrorDisplay,
  type TokenBundle,
} from "../../stores";

export type AuthPageMode = "login" | "register";

export interface LoginFormModel {
  loginName: string;
  password: string;
  captchaToken?: string;
}

export interface RegisterFormModel {
  loginName: string;
  password: string;
  displayName?: string;
  mobile?: string;
  email?: string;
}

export interface AuthNavigationResult {
  route: string;
  requestId?: string;
}

export interface AuthPageState {
  mode: AuthPageMode;
  loading: boolean;
  lastRequestId?: string;
  lastError?: RequestErrorDisplay;
  accountStatus?: AccountStatus;
}

export interface AuthPageModel {
  readonly state: AuthPageState;
  setMode(mode: AuthPageMode): void;
  login(form: LoginFormModel, redirectTo?: string): Promise<AuthNavigationResult>;
  register(form: RegisterFormModel, redirectTo?: string): Promise<AuthNavigationResult>;
  refresh(refreshToken?: string): Promise<void>;
  loadCurrentUser(requestId?: string): Promise<AuthUser>;
  logout(): Promise<AuthNavigationResult>;
}

export interface AuthPageModelDependencies {
  apiClient: ApiClient;
  auth?: AuthStore;
  errors?: ErrorStore;
  createRequestId?: (scope: string) => string;
  now?: () => Date;
}

interface AuthSessionData {
  accountId?: EntityId;
  loginName?: string;
  displayName?: string;
  accountStatus?: AccountStatus;
  accessToken: string;
  refreshToken?: string;
  expiresIn?: number;
  refreshExpiresIn?: number;
  platformRoles?: string[];
  platformPermissions?: string[];
  account?: Partial<AuthUser>;
}

interface CurrentUserData {
  accountId: EntityId;
  loginName: string;
  displayName?: string;
  accountStatus?: AccountStatus;
  platformRoles?: string[];
  platformPermissions?: string[];
  lastLoginAt?: IsoDateTimeString;
}

interface RefreshTokenData {
  accessToken: string;
  refreshToken?: string;
  expiresIn?: number;
  refreshExpiresIn?: number;
}

export function createAuthPageModel(dependencies: AuthPageModelDependencies): AuthPageModel {
  const auth = dependencies.auth ?? authStore;
  const errors = dependencies.errors ?? errorStore;
  const now = dependencies.now ?? (() => new Date());
  const createRequestId = dependencies.createRequestId ?? defaultRequestId;
  const state: AuthPageState = {
    mode: "login",
    loading: false,
  };

  function begin(scope: string): string {
    const requestId = createRequestId(scope);
    state.loading = true;
    state.lastRequestId = requestId;
    state.lastError = undefined;
    return requestId;
  }

  function fail(error: unknown): never {
    const display = errors.capture(error);
    state.lastError = display;
    state.accountStatus =
      display.code === "AUTH_ACCOUNT_LOCKED"
        ? "LOCKED"
        : display.code === "AUTH_ACCOUNT_DISABLED"
          ? "DISABLED"
          : state.accountStatus;
    throw error;
  }

  async function loadCurrentUser(requestId = createRequestId("AUTH-005")): Promise<AuthUser> {
    state.loading = true;
    state.lastRequestId = requestId;
    try {
      const response = await dependencies.apiClient.call<CurrentUserData>("AUTH-005", {
        context: auth.toApiContext(requestId),
      });
      const current = toAuthUser(response.data);
      const token = auth.getState().token;
      if (token) {
        auth.setAuthenticated(current, token);
      }
      state.accountStatus = current.accountStatus;
      return current;
    } catch (error) {
      auth.markExpired();
      fail(error);
    } finally {
      state.loading = false;
    }
  }

  return {
    state,

    setMode(mode) {
      state.mode = mode;
      state.lastError = undefined;
    },

    async login(form, redirectTo = DEFAULT_AUTHENTICATED_ROUTE) {
      validateLogin(form);
      const requestId = begin("AUTH-002");
      auth.beginLogin();
      try {
        const response = await dependencies.apiClient.call<AuthSessionData, LoginFormModel>("AUTH-002", {
          body: form,
          context: { requestId },
        });
        const token = toTokenBundle(response.data, now());
        auth.setAuthenticated(toAuthUser(response.data), token);
        await loadCurrentUser(requestId);
        state.accountStatus = auth.getState().user?.accountStatus;
        return { route: redirectTo, requestId: response.requestId };
      } catch (error) {
        auth.logout();
        fail(error);
      } finally {
        state.loading = false;
      }
    },

    async register(form, redirectTo = DEFAULT_AUTHENTICATED_ROUTE) {
      validateRegister(form);
      const requestId = begin("AUTH-001");
      try {
        const response = await dependencies.apiClient.call<AuthSessionData, RegisterFormModel>("AUTH-001", {
          body: form,
          context: { requestId },
        });
        const token = toTokenBundle(response.data, now());
        auth.setAuthenticated(toAuthUser(response.data), token);
        await loadCurrentUser(requestId);
        state.accountStatus = auth.getState().user?.accountStatus;
        return { route: redirectTo, requestId: response.requestId };
      } catch (error) {
        auth.logout();
        fail(error);
      } finally {
        state.loading = false;
      }
    },

    async refresh(refreshToken) {
      const token = refreshToken ?? auth.getState().token?.refreshToken;
      if (!token) {
        auth.markExpired();
        throw localError("AUTH_REFRESH_INVALID", "Missing refreshToken for AUTH-003.");
      }

      const requestId = begin("AUTH-003");
      try {
        const response = await dependencies.apiClient.call<RefreshTokenData, { refreshToken: string }>("AUTH-003", {
          body: { refreshToken: token },
          context: { requestId },
        });
        auth.updateToken(toTokenBundle(response.data, now()));
      } catch (error) {
        auth.markExpired();
        fail(error);
      } finally {
        state.loading = false;
      }
    },

    loadCurrentUser,

    async logout() {
      const requestId = begin("AUTH-004");
      try {
        if (auth.getState().status === "authenticated") {
          await dependencies.apiClient.call<Record<string, never>>("AUTH-004", {
            context: auth.toApiContext(requestId),
          });
        }
        return { route: LOGIN_ROUTE, requestId };
      } catch (error) {
        state.lastError = errors.capture(error);
        return { route: LOGIN_ROUTE, requestId };
      } finally {
        auth.logout();
        systemContextStore.clear();
        permissionStore.clear();
        state.loading = false;
      }
    },
  };
}

function validateLogin(form: LoginFormModel): void {
  if (!form.loginName || !form.password) {
    throw localError("COMMON_PARAM_INVALID", "loginName and password are required by AUTH-002.");
  }
}

function validateRegister(form: RegisterFormModel): void {
  if (!form.loginName || !form.password) {
    throw localError("COMMON_PARAM_INVALID", "loginName and password are required by AUTH-001.");
  }
}

function toAuthUser(data: AuthSessionData | CurrentUserData): AuthUser {
  const account = "account" in data ? data.account : undefined;
  return {
    accountId: data.accountId ?? account?.accountId ?? "",
    loginName: data.loginName ?? account?.loginName ?? "",
    displayName: data.displayName ?? account?.displayName ?? data.loginName ?? account?.loginName ?? "",
    accountStatus: data.accountStatus ?? account?.accountStatus ?? "NORMAL",
    platformRoles: data.platformRoles ?? account?.platformRoles ?? [],
    platformPermissions: data.platformPermissions ?? account?.platformPermissions ?? [],
    lastLoginAt: "lastLoginAt" in data ? data.lastLoginAt : account?.lastLoginAt,
  };
}

function toTokenBundle(data: AuthSessionData | RefreshTokenData, baseTime: Date): TokenBundle {
  return {
    accessToken: data.accessToken,
    refreshToken: data.refreshToken,
    accessTokenExpiresAt: data.expiresIn ? secondsFrom(baseTime, data.expiresIn) : undefined,
    refreshTokenExpiresAt: data.refreshExpiresIn ? secondsFrom(baseTime, data.refreshExpiresIn) : undefined,
  };
}

function secondsFrom(baseTime: Date, seconds: number): IsoDateTimeString {
  return new Date(baseTime.getTime() + seconds * 1000).toISOString();
}

function defaultRequestId(scope: string): string {
  return `${scope}_${Date.now().toString(36)}`;
}

function localError(code: string, message: string): Error {
  const error = new Error(message);
  error.name = code;
  return error;
}
