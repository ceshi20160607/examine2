import { apiRequest } from '@/services/api'
import type { PlatformMessage, PlatformMessageFilter, PlatformMessagePage, PlatformMessageType } from '@/types/platformMessage'

const root = '/api/v1/platform/messages'
export const platformMessageApi = {
  list(query: { status?: PlatformMessageFilter; type?: 'ALL' | PlatformMessageType; templateCode?: string; keyword?: string; from?: string; to?: string; page?: number; size?: number } = {}) {
    const params = new URLSearchParams({ status: query.status ?? 'ALL', type: query.type ?? 'ALL', page: String(query.page ?? 1), size: String(query.size ?? 20) })
    if (query.templateCode) params.set('templateCode', query.templateCode)
    if (query.keyword) params.set('keyword', query.keyword)
    if (query.from) params.set('from', query.from)
    if (query.to) params.set('to', query.to)
    return apiRequest<PlatformMessagePage>(`${root}?${params}`)
  },
  unreadCount: () => apiRequest<{ unreadCount: number }>(`${root}/unread-count`),
  read: (id: string) => apiRequest<PlatformMessage>(`${root}/${encodeURIComponent(id)}:read`, { method: 'POST', idempotencyKey: crypto.randomUUID() }),
  readAll: () => apiRequest<{ changedCount: number }>(`${root}/read-all`, { method: 'POST', idempotencyKey: crypto.randomUUID() }),
  archive: (id: string) => apiRequest<PlatformMessage>(`${root}/${encodeURIComponent(id)}:archive`, { method: 'POST', idempotencyKey: crypto.randomUUID() }),
}
