import { apiRequest } from '@/services/api'
import type {
  CreatePlatformAiProviderInput,
  CreatePlatformAiSessionInput,
  ActOnPlatformAiTaskProposalInput,
  PlatformAiCapability,
  PlatformAiPolicy,
  PlatformAiPolicyCheck,
  PlatformAiProvider,
  PlatformAiSession,
  PlatformAiSessionDetail,
  PlatformAiSessionPage,
  PlatformAiTaskProposal,
  PlatformAiTurn,
  SavePlatformAiPolicyInput,
  SubmitPlatformAiMessageInput,
  UpdatePlatformAiProviderInput,
} from '@/types/platformAi'

const adminBase = '/api/v1/platform/admin/ai'
const runtimeBase = '/api/v1/platform/ai'
const sessionsBase = `${runtimeBase}/sessions`

function items<T>(value: T[] | { items?: T[]; content?: T[] }) {
  return Array.isArray(value) ? value : value.items ?? value.content ?? []
}

type RawPlatformAiSessionPage = Partial<PlatformAiSessionPage> & {
  items?: PlatformAiSession[]
  total?: number
}

export const platformAiAdminApi = {
  async providers() {
    return items(await apiRequest<PlatformAiProvider[] | { items?: PlatformAiProvider[]; content?: PlatformAiProvider[] }>(
      `${adminBase}/providers`,
    ))
  },
  createProvider(input: CreatePlatformAiProviderInput) {
    return apiRequest<PlatformAiProvider>(`${adminBase}/providers`, {
      method: 'POST', body: input, idempotencyKey: crypto.randomUUID(),
    })
  },
  updateProvider(providerId: string, input: UpdatePlatformAiProviderInput) {
    return apiRequest<PlatformAiProvider>(`${adminBase}/providers/${encodeURIComponent(providerId)}`, {
      method: 'PUT', body: input, idempotencyKey: crypto.randomUUID(),
    })
  },
  policy() {
    return apiRequest<PlatformAiPolicy>(`${adminBase}/policy`)
  },
  savePolicy(input: SavePlatformAiPolicyInput) {
    return apiRequest<PlatformAiPolicy>(`${adminBase}/policy`, { method: 'PUT', body: input })
  },
  checkPolicy() {
    return apiRequest<PlatformAiPolicyCheck>(`${adminBase}/policy:check`, { method: 'POST' })
  },
  publishPolicy(expectedVersion: number) {
    return apiRequest<PlatformAiPolicy>(`${adminBase}/policy:publish`, {
      method: 'POST', body: { expectedVersion }, idempotencyKey: crypto.randomUUID(),
    })
  },
  capability() {
    return apiRequest<PlatformAiCapability>(`${adminBase}/capability`)
  },
}

export const platformAiRuntimeApi = {
  capability() {
    return apiRequest<PlatformAiCapability>(`${runtimeBase}/capability`)
  },
  async sessions(page = 1, size = 20): Promise<PlatformAiSessionPage> {
    const query = new URLSearchParams({ page: String(page), size: String(size) })
    const result = await apiRequest<RawPlatformAiSessionPage | PlatformAiSession[]>(`${sessionsBase}?${query}`)
    if (Array.isArray(result)) return { rows: result, page, size, hasMore: false }
    const rows = result.rows ?? result.items ?? []
    return {
      rows,
      page: result.page ?? page,
      size: result.size ?? size,
      hasMore: result.hasMore ?? (result.total === undefined ? false : page * size < result.total),
    }
  },
  createSession(input: CreatePlatformAiSessionInput) {
    return apiRequest<PlatformAiSession>(sessionsBase, {
      method: 'POST', body: input, idempotencyKey: crypto.randomUUID(),
    })
  },
  session(sessionId: string) {
    return apiRequest<PlatformAiSessionDetail>(`${sessionsBase}/${encodeURIComponent(sessionId)}`)
  },
  submitMessage(sessionId: string, input: SubmitPlatformAiMessageInput) {
    return apiRequest<PlatformAiTurn>(`${sessionsBase}/${encodeURIComponent(sessionId)}/messages`, {
      method: 'POST', body: input, idempotencyKey: crypto.randomUUID(),
    })
  },
  taskProposal(sessionId: string, proposalId: string) {
    return apiRequest<PlatformAiTaskProposal>(
      `${sessionsBase}/${encodeURIComponent(sessionId)}/proposals/${encodeURIComponent(proposalId)}`,
    )
  },
  confirmTaskProposal(
    sessionId: string,
    proposalId: string,
    input: ActOnPlatformAiTaskProposalInput,
    idempotencyKey: string,
  ) {
    return apiRequest<PlatformAiTaskProposal>(
      `${sessionsBase}/${encodeURIComponent(sessionId)}/proposals/${encodeURIComponent(proposalId)}/confirm`,
      { method: 'POST', body: input, idempotencyKey },
    )
  },
  rejectTaskProposal(
    sessionId: string,
    proposalId: string,
    input: ActOnPlatformAiTaskProposalInput,
  ) {
    return apiRequest<PlatformAiTaskProposal>(
      `${sessionsBase}/${encodeURIComponent(sessionId)}/proposals/${encodeURIComponent(proposalId)}/reject`,
      { method: 'POST', body: input },
    )
  },
}
