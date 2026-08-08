import { apiRequest } from './api'
import type {
  ChangedCount,
  DeliveryChannel,
  DeliveryLogDetail,
  DeliveryLogPage,
  DeliveryLogQuery,
  DeliveryPreference,
  DeliveryPreferenceUpdate,
  EventChannelCheckResult,
  EventChannelConfiguration,
  EventChannelUpdate,
  ExternalDeliveryChannel,
  InboxMessage,
  InboxMessagePage,
  InboxMessageQuery,
  MessageTemplate,
  MessageTemplateUpdate,
  UnreadCount,
} from '@/types/event'

function messageRoot(systemId: string) {
  return `/api/v1/systems/${encodeURIComponent(systemId)}/event/messages`
}

function templateRoot(systemId: string) {
  return `/api/v1/systems/${encodeURIComponent(systemId)}/admin/event/message-templates`
}

function preferenceRoot(systemId: string) {
  return `/api/v1/systems/${encodeURIComponent(systemId)}/event/delivery-preferences`
}

function deliveryLogRoot(systemId: string) {
  return `/api/v1/systems/${encodeURIComponent(systemId)}/event/delivery-logs`
}

function channelRoot(systemId: string) {
  return `/api/v1/systems/${encodeURIComponent(systemId)}/event/channels`
}

export const messageTemplateApi = {
  list: (systemId: string) => apiRequest<MessageTemplate[]>(templateRoot(systemId)),
  update: (systemId: string, templateCode: string, body: MessageTemplateUpdate) =>
    apiRequest<MessageTemplate>(`${templateRoot(systemId)}/${encodeURIComponent(templateCode)}`, {
      method: 'PUT', body,
    }),
  publish: (systemId: string, templateCode: string, expectedVersion: number) =>
    apiRequest<MessageTemplate>(`${templateRoot(systemId)}/${encodeURIComponent(templateCode)}:publish`, {
      method: 'POST', body: { expectedVersion }, idempotencyKey: crypto.randomUUID(),
    }),
}


export const eventAdministrationApi = {
  channels(systemId: string) {
    return apiRequest<EventChannelConfiguration[]>(channelRoot(systemId))
  },
  updateChannel(
    systemId: string,
    channel: ExternalDeliveryChannel,
    input: EventChannelUpdate,
  ) {
    return apiRequest<EventChannelConfiguration>(
      `${channelRoot(systemId)}/${encodeURIComponent(channel)}`,
      { method: 'PUT', body: input },
    )
  },
  checkChannel(systemId: string, channel: ExternalDeliveryChannel) {
    return apiRequest<EventChannelCheckResult>(
      `${channelRoot(systemId)}/${encodeURIComponent(channel)}:check`,
      { method: 'POST', idempotencyKey: crypto.randomUUID() },
    )
  },
}

export const eventApi = {
  list(systemId: string, query: InboxMessageQuery = {}) {
    const search = new URLSearchParams({
      status: query.status ?? 'ALL',
      page: String(query.page ?? 1),
      size: String(query.size ?? 20),
    })
    return apiRequest<InboxMessagePage>(`${messageRoot(systemId)}?${search}`)
  },
  unreadCount(systemId: string) {
    return apiRequest<UnreadCount>(`${messageRoot(systemId)}/unread-count`)
  },
  read(systemId: string, messageId: string) {
    return apiRequest<InboxMessage>(`${messageRoot(systemId)}/${encodeURIComponent(messageId)}:read`, {
      method: 'POST',
      idempotencyKey: crypto.randomUUID(),
    })
  },
  readAll(systemId: string) {
    return apiRequest<ChangedCount>(`${messageRoot(systemId)}/read-all`, {
      method: 'POST',
      idempotencyKey: crypto.randomUUID(),
    })
  },
  archive(systemId: string, messageId: string) {
    return apiRequest<InboxMessage>(`${messageRoot(systemId)}/${encodeURIComponent(messageId)}:archive`, {
      method: 'POST',
      idempotencyKey: crypto.randomUUID(),
    })
  },
  deliveryPreferences(systemId: string) {
    return apiRequest<DeliveryPreference[]>(preferenceRoot(systemId))
  },
  updateDeliveryPreference(
    systemId: string,
    templateCode: string,
    channel: DeliveryChannel,
    input: DeliveryPreferenceUpdate,
  ) {
    return apiRequest<DeliveryPreference>(
      `${preferenceRoot(systemId)}/${encodeURIComponent(templateCode)}/${encodeURIComponent(channel)}`,
      { method: 'PUT', body: input },
    )
  },
  deliveryLogs(systemId: string, query: DeliveryLogQuery = {}) {
    const search = new URLSearchParams({
      page: String(query.page ?? 0),
      size: String(query.size ?? 20),
    })
    if (query.channel && query.channel !== 'ALL') search.set('channel', query.channel)
    if (query.status && query.status !== 'ALL') search.set('status', query.status)
    if (query.templateCode?.trim()) search.set('templateCode', query.templateCode.trim())
    return apiRequest<DeliveryLogPage>(`${deliveryLogRoot(systemId)}?${search}`)
  },
  deliveryLog(systemId: string, deliveryId: string) {
    return apiRequest<DeliveryLogDetail>(
      `${deliveryLogRoot(systemId)}/${encodeURIComponent(deliveryId)}`,
    )
  },
}
