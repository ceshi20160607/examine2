export interface PlatformProfile { name: string; description: string }
export interface PlatformStoragePolicy { defaultMode: 'LOCAL' | 'S3'; maximumUploadBytes: number; retentionDays: number }
export interface PlatformSecurityPolicy { sessionIdleMinutes: number; passwordMinimumLength: number; requireMfaForAdmins: boolean }
export interface PlatformQuotaPolicy { defaultMemberLimit: number; defaultModuleLimit: number; defaultStorageBytes: number }
export interface PlatformBackupPolicy { enabled: boolean; retentionDays: number; intervalHours: number }
export interface PlatformReleasePolicy { maintenanceMode: boolean; channel: 'STABLE' | 'CANARY'; approvalRequired: boolean }

export interface PlatformGlobalSettings {
  profile: PlatformProfile
  storage: PlatformStoragePolicy
  security: PlatformSecurityPolicy
  quota: PlatformQuotaPolicy
  backup: PlatformBackupPolicy
  release: PlatformReleasePolicy
  version: string
  updatedAt?: string
}

export type PlatformGlobalSettingsUpdate = Omit<PlatformGlobalSettings, 'version' | 'updatedAt'> & { expectedVersion: string }
