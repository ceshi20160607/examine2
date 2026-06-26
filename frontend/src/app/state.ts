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

  const firstSwitchable = optionsResponse.data.find((option) => option.switchable);
  if (!firstSwitchable) {
    shellState.currentSystem = undefined;
    shellState.currentTenant = undefined;
    shellState.account.systemRoles = [];
    return;
  }
  await switchToSystem(firstSwitchable.systemId, firstSwitchable.tenantId, 'frontend bootstrap');
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
  const currentSystem = shellState.availableSystems.find((system) => system.systemId === switchResponse.data.systemId);
  if (currentSystem) {
    currentSystem.accountMemberBindingId = switchResponse.data.accountMemberBindingId;
    currentSystem.systemMemberId = switchResponse.data.systemMemberId;
    currentSystem.tenantId = switchResponse.data.tenantId;
  }
  shellState.account.systemRoles = systemRoleCodes(switchResponse.data.effectiveRoleIds);
  return switchResponse.data;
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
