export type PlatformMessageStatus = 'UNREAD' | 'READ' | 'ARCHIVED'
export type PlatformMessageFilter = 'ALL' | PlatformMessageStatus
export type PlatformMessageType = 'AUTHORIZATION' | 'TASK' | 'LOG' | 'SYSTEM_SWITCH' | 'AGENT'
export interface PlatformMessageTarget { type: 'PLATFORM_AUTHORIZATION' | 'PLATFORM_TASK' | 'PLATFORM_PROJECT' | 'PLATFORM_LOG' | 'SYSTEM_SWITCH' | 'PLATFORM_AGENT'; id: string; path: string }
export interface PlatformMessage { id: string; templateCode: string; type: PlatformMessageType; title: string; body: string; target: PlatformMessageTarget | null; status: PlatformMessageStatus; createdAt: string; readAt: string | null; archivedAt: string | null; version: number }
export interface PlatformMessagePage { items: PlatformMessage[]; page: number; size: number; total: number }
