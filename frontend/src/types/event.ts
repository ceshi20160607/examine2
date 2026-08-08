export type InboxMessageStatus = 'UNREAD' | 'READ' | 'ARCHIVED'
export type InboxMessageFilter = 'ALL' | InboxMessageStatus
export type DeliveryChannel = 'INBOX' | 'EMAIL' | 'WEBHOOK'
export type DeliveryLogStatus = 'PENDING' | 'DELIVERED' | 'SKIPPED' | 'FAILED'
export type DeliveryLogStatusFilter = 'ALL' | DeliveryLogStatus
export type DeliveryChannelFilter = 'ALL' | DeliveryChannel

export interface MessageTarget {
  type: string
  id: string
}

export interface InboxMessage {
  id: string
  systemId: string
  tenantId: string
  senderMemberId: string
  recipientMemberId: string
  templateCode: string
  title: string
  body: string
  target: MessageTarget | null
  targetPath?: string | null
  status: InboxMessageStatus
  createdAt: string
  readAt: string | null
  archivedAt: string | null
  version: number
}

export interface UnreadCount {
  unreadCount: number
}

export interface ChangedCount {
  changedCount: number
}

export interface InboxMessagePage {
  items: InboxMessage[]
  page: number
  size: number
  total: number
}

export interface InboxMessageQuery {
  status?: InboxMessageFilter
  page?: number
  size?: number
}

export interface DeliveryPreference {
  templateCode: string
  eventType: string
  name: string
  channel: DeliveryChannel
  enabled: boolean
  version: number
  updatedAt: string | null
}

export interface DeliveryPreferenceUpdate {
  enabled: boolean
  expectedVersion: number
}

export interface MessageTemplate {
  templateCode: string
  eventType: string
  name: string
  enabled: boolean
  titleTemplate: string
  bodyTemplate: string
  channels: DeliveryChannel[]
  allowedVariables: string[]
  publishedVersion: number | null
  publishedSourceDraftVersion: number | null
  publishedEnabled: boolean | null
  version: number
  updatedAt: string | null
}

export interface MessageTemplateUpdate {
  expectedVersion: number
  name: string
  enabled: boolean
  titleTemplate: string
  bodyTemplate: string
  channels: DeliveryChannel[]
}

export interface DeliveryLogQuery {
  page?: number
  size?: number
  channel?: DeliveryChannelFilter
  status?: DeliveryLogStatusFilter
  templateCode?: string
}

export interface DeliveryLogAttempt {
  attemptNo: number
  status: DeliveryLogStatus
  durationMs: number | null
  traceId: string
  failureCode: string | null
  failureMessage: string | null
  startedAt: string
  completedAt: string | null
}

export interface DeliveryLog {
  deliveryId: string
  templateCode: string
  channel: DeliveryChannel
  status: DeliveryLogStatus
  attemptCount: number
  durationMs: number | null
  traceId: string
  recipientMasked: string
  targetType: string | null
  targetId: string | null
  createdAt: string
  completedAt: string | null
  retryable: boolean
}

export interface DeliveryLogDetail extends DeliveryLog {
  templateVersionId: string | null
  targetPath: string | null
  failureCode: string | null
  failureMessage: string | null
  dedupeFingerprint: string | null
  attempts: DeliveryLogAttempt[]
}

export interface DeliveryLogPage {
  items: DeliveryLog[]
  total: number
  page: number
  size: number
}

export type ExternalDeliveryChannel = Exclude<DeliveryChannel, 'INBOX'>
export type ChannelCheckStatus = 'SENT' | 'TEMPORARY_FAILURE' | 'PERMANENT_FAILURE'

/**
 * Deliberately safe channel projection. It has no raw credential, recipient,
 * endpoint query or external response fields, so views cannot accidentally
 * render those values.
 */
export interface EventChannelConfiguration {
  channel: DeliveryChannel
  displayName: string
  enabled: boolean
  available: boolean
  configured: boolean
  maskedDestination: string | null
  secretRefMasked: string | null
  timeoutMs: number | null
  version: number
  updatedAt: string | null
  lastCheckAt: string | null
  lastCheckStatus: ChannelCheckStatus | null
}

/** Blank optional values mean "preserve the deployed value", never clear it. */
export interface EventChannelUpdate {
  enabled: boolean
  expectedVersion: number
  endpoint?: string
  secretRef?: string
  timeoutMs?: number
}

export interface EventChannelCheckResult {
  channel: ExternalDeliveryChannel
  status: ChannelCheckStatus
  message: string
  traceId: string
  checkedAt: string
  durationMs: number
}
