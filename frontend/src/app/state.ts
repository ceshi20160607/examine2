import { apiClient } from '../api/client';
import type { SystemSwitchContext, TenantSwitchContext } from '../api/types';

export type PlatformRole = 'PLATFORM_MEMBER' | 'PLATFORM_ADMIN' | 'PLATFORM_ROOT';
export type SystemRole = 'SYSTEM_MEMBER' | 'SYSTEM_ADMIN' | 'SYSTEM_SUPER_ADMIN';

export interface AccountState {
  accountId: string;
  displayName: string;
  platformRoles: PlatformRole[];
  systemRoles: SystemRole[];
}

export interface AvailableSystem {
  systemId: string;
  systemName: string;
  tenantName: string;
  accountMemberBindingId: string;
  systemMemberId: string;
  tenantId?: string;
  enabled: boolean;
  disabledReason?: string;
}

interface AccountProfileResponse {
  accountId: string;
  accountName: string;
  systems: Array<{
    systemId: string;
    systemName: string;
    tenantId: string;
    systemMemberId: string;
    roles: SystemRole[];
    switchable: boolean;
    disabledReason?: string;
  }>;
  platformPermissions: PlatformRole[];
}

interface SwitchOption {
  systemId: string;
  systemName: string;
  tenantId: string;
  tenantName: string;
  switchable: boolean;
  disabledReason?: string;
}

export interface TenantOption {
  tenantId: string;
  systemId: string;
  tenantCode: string;
  tenantName: string;
  domain?: string;
  status: number;
  updatedAt?: string;
}

export interface ShellState {
  currentArea: string;
  account: AccountState;
  currentSystem?: SystemSwitchContext;
  currentTenant?: TenantSwitchContext;
  availableSystems: AvailableSystem[];
}

export const shellState: ShellState = {
  currentArea: 'auth',
  account: {
    accountId: '',
    displayName: '未登录',
    platformRoles: [],
    systemRoles: [],
  },
  availableSystems: [],
};

export async function initializeShellState(): Promise<void> {
  const accountResponse = await apiClient.get<AccountProfileResponse>('/api/v1/account/me');
  if (accountResponse.code !== 'SUCCESS' || !accountResponse.data) {
    return;
  }

  const profile = accountResponse.data;
  localStorage.setItem('unexamine.accountId', profile.accountId);
  shellState.account = {
    accountId: profile.accountId,
    displayName: profile.accountName,
    platformRoles: profile.platformPermissions ?? [],
    systemRoles: Array.from(new Set((profile.systems ?? []).flatMap((system) => system.roles ?? []))),
  };
  shellState.currentSystem = undefined;
  shellState.currentTenant = undefined;
  shellState.availableSystems = (profile.systems ?? []).map((system) => ({
    systemId: system.systemId,
    systemName: system.systemName,
    tenantName: system.tenantId,
    tenantId: system.tenantId,
    accountMemberBindingId: '',
    systemMemberId: system.systemMemberId,
    enabled: system.switchable,
    disabledReason: system.disabledReason,
  }));

  const optionsResponse = await apiClient.get<SwitchOption[]>('/api/v1/platform/system-switch/options');
  if (optionsResponse.code !== 'SUCCESS' || !optionsResponse.data?.length) {
    return;
  }
  shellState.availableSystems = optionsResponse.data.map((option) => ({
    systemId: option.systemId,
    systemName: option.systemName,
    tenantName: option.tenantName,
    tenantId: option.tenantId,
    accountMemberBindingId: '',
    systemMemberId: '',
    enabled: option.switchable,
    disabledReason: option.disabledReason,
  }));
}

export async function switchToSystem(systemId: string, tenantId?: string, reason = 'frontend system switch'): Promise<SystemSwitchContext> {
  const switchResponse = await apiClient.post<SystemSwitchContext>('/api/v1/platform/system-switch', {
    systemId,
    tenantId,
    reason,
  });
  if (switchResponse.code !== 'SUCCESS') {
    throw new Error(switchResponse.message || '系统切换失败。');
  }
  shellState.currentSystem = switchResponse.data;
  shellState.currentTenant = {
    systemId: switchResponse.data.systemId,
    tenantId: switchResponse.data.tenantId,
    tenantName: '',
    tenantRoleIds: switchResponse.data.effectiveRoleIds,
    tenantDataScope: switchResponse.data.dataScope,
    isTenantSwitchable: true,
    permissionSnapshotSummary: switchResponse.data.permissionSnapshotSummary,
  };
  const currentSystem = shellState.availableSystems.find((system) => system.systemId === switchResponse.data.systemId);
  if (currentSystem) {
    currentSystem.accountMemberBindingId = switchResponse.data.accountMemberBindingId;
    currentSystem.systemMemberId = switchResponse.data.systemMemberId;
    currentSystem.tenantId = switchResponse.data.tenantId;
  }
  shellState.account.systemRoles = systemRoleCodes(switchResponse.data.effectiveRoleIds);
  return switchResponse.data;
}

export async function loadSystemTenants(systemId: string): Promise<TenantOption[]> {
  const response = await apiClient.get<TenantOption[]>(`/api/v1/systems/${systemId}/tenants`);
  if (response.code !== 'SUCCESS') {
    throw new Error(response.message || '租户列表加载失败。');
  }
  return response.data ?? [];
}

export async function switchTenant(systemId: string, tenantId: string, reason = 'frontend tenant switch'): Promise<TenantSwitchContext> {
  const response = await apiClient.post<TenantSwitchContext>(`/api/v1/systems/${systemId}/tenant-switch`, {
    tenantId,
    reason,
  });
  if (response.code !== 'SUCCESS') {
    throw new Error(response.message || '租户切换失败。');
  }
  shellState.currentTenant = response.data;
  if (shellState.currentSystem?.systemId === systemId) {
    shellState.currentSystem = {
      ...shellState.currentSystem,
      tenantId: response.data.tenantId,
      effectiveRoleIds: response.data.tenantRoleIds,
      dataScope: response.data.tenantDataScope,
      permissionSnapshotSummary: response.data.permissionSnapshotSummary,
      messageTodoScope: {
        ...shellState.currentSystem.messageTodoScope,
        tenantId: response.data.tenantId,
      },
    };
  }
  const currentSystem = shellState.availableSystems.find((system) => system.systemId === systemId);
  if (currentSystem) {
    currentSystem.tenantId = response.data.tenantId;
    currentSystem.tenantName = response.data.tenantName;
  }
  shellState.account.systemRoles = systemRoleCodes(response.data.tenantRoleIds);
  return response.data;
}

export function canEnterPlatformAdmin(): boolean {
  return shellState.account.platformRoles.includes('PLATFORM_ADMIN') || shellState.account.platformRoles.includes('PLATFORM_ROOT');
}

export function canEnterSystemAdmin(): boolean {
  return shellState.account.systemRoles.includes('SYSTEM_ADMIN') || shellState.account.systemRoles.includes('SYSTEM_SUPER_ADMIN');
}

function systemRoleCodes(values: string[] = []): SystemRole[] {
  return values.filter((value): value is SystemRole => value === 'SYSTEM_MEMBER'
    || value === 'SYSTEM_ADMIN'
    || value === 'SYSTEM_SUPER_ADMIN');
}
