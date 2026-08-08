import { apiRequest } from '@/services/api'
import type {
  AiCapability,
  AiConfirmation,
  AiConfigurationFieldProposal,
  AiConfigurationArtifactProposal,
  AiGeneratedDraftProposal,
  AiPolicy,
  AiPolicyCheck,
  AiProvider,
  AiSession,
  AiSessionDetail,
  AiSessionPage,
  AiTurn,
  AiWorkProposal,
  CreateAiProviderInput,
  CreateAiSessionInput,
  SaveAiPolicyInput,
  SubmitAiMessageInput,
  UpdateAiProviderInput,
} from '@/types/ai'

const adminBase = (systemId: string) =>
  `/api/v1/systems/${encodeURIComponent(systemId)}/admin/ai`
const runtimeAiBase = (systemId: string) =>
  `/api/v1/systems/${encodeURIComponent(systemId)}/ai`
const runtimeBase = (systemId: string) => `${runtimeAiBase(systemId)}/sessions`

function items<T>(value: T[] | { items?: T[]; content?: T[] }) {
  return Array.isArray(value) ? value : value.items ?? value.content ?? []
}

type RawAiPolicy = Omit<
  AiPolicy,
  | 'outboundFields'
  | 'redactionMode'
  | 'allowedOperations'
  | 'writableFields'
  | 'fillFields'
  | 'confirmationMode'
  | 'confirmationExpiresSeconds'
> & {
  outboundFields?: Record<string, string[]>
  outputFieldCodes?: string[]
  redactionMode?: string
  allowedOperations?: AiPolicy['allowedOperations']
  writableFields?: Record<string, string[]>
  fillFields?: Record<string, string[]>
  confirmationMode?: AiPolicy['confirmationMode']
  confirmationExpiresSeconds?: number
}

type RawAiSessionPage = Partial<AiSessionPage> & {
  items?: AiSession[]
  total?: number
}

function qualifiedFields(values: string[] | undefined) {
  const result: Record<string, string[]> = {}
  for (const value of values ?? []) {
    const separator = value.indexOf('.')
    if (separator < 1 || separator === value.length - 1) continue
    const moduleCode = value.slice(0, separator)
    const fieldCode = value.slice(separator + 1)
    result[moduleCode] = [...new Set([...(result[moduleCode] ?? []), fieldCode])]
  }
  return result
}

function policy(value: RawAiPolicy): AiPolicy {
  return {
    ...value,
    outboundFields: value.outboundFields ?? qualifiedFields(value.outputFieldCodes),
    allowedOperations: value.allowedOperations ?? ['RECORD_QUERY'],
    writableFields: value.writableFields ?? {},
    fillFields: value.fillFields ?? {},
    confirmationMode: value.confirmationMode ?? 'REQUIRED',
    confirmationExpiresSeconds: value.confirmationExpiresSeconds ?? 600,
    redactionMode: 'STRICT',
  }
}

export const aiAdminApi = {
  async providers(systemId: string) {
    const result = await apiRequest<AiProvider[] | { items?: AiProvider[]; content?: AiProvider[] }>(
      `${adminBase(systemId)}/providers`,
    )
    return items(result)
  },
  createProvider(systemId: string, input: CreateAiProviderInput) {
    return apiRequest<AiProvider>(`${adminBase(systemId)}/providers`, {
      method: 'POST', body: input, idempotencyKey: crypto.randomUUID(),
    })
  },
  updateProvider(systemId: string, providerId: string, input: UpdateAiProviderInput) {
    return apiRequest<AiProvider>(
      `${adminBase(systemId)}/providers/${encodeURIComponent(providerId)}`,
      { method: 'PUT', body: input, idempotencyKey: crypto.randomUUID() },
    )
  },
  async policy(systemId: string) {
    return policy(await apiRequest<RawAiPolicy>(`${adminBase(systemId)}/policy`))
  },
  async savePolicy(systemId: string, input: SaveAiPolicyInput) {
    return policy(await apiRequest<RawAiPolicy>(`${adminBase(systemId)}/policy`, {
      method: 'PUT', body: input,
    }))
  },
  checkPolicy(systemId: string) {
    return apiRequest<AiPolicyCheck>(`${adminBase(systemId)}/policy:check`, { method: 'POST' })
  },
  async publishPolicy(systemId: string, expectedVersion: number) {
    return policy(await apiRequest<RawAiPolicy>(`${adminBase(systemId)}/policy:publish`, {
      method: 'POST', body: { expectedVersion }, idempotencyKey: crypto.randomUUID(),
    }))
  },
  capability(systemId: string) {
    return apiRequest<AiCapability>(`${adminBase(systemId)}/capability`)
  },
}

export const aiRuntimeApi = {
  capability(systemId: string) {
    return apiRequest<AiCapability>(`${runtimeAiBase(systemId)}/capability`)
  },
  async sessions(systemId: string, page = 1, size = 20): Promise<AiSessionPage> {
    const query = new URLSearchParams({ page: String(page), size: String(size) })
    const result = await apiRequest<RawAiSessionPage | AiSession[]>(`${runtimeBase(systemId)}?${query}`)
    if (Array.isArray(result)) return { rows: result, page, size, hasMore: false }
    const rows = result.rows ?? result.items ?? []
    return {
      rows,
      page: result.page ?? page,
      size: result.size ?? size,
      hasMore: result.hasMore ?? (result.total === undefined ? false : page * size < result.total),
    }
  },
  createSession(systemId: string, input: CreateAiSessionInput) {
    return apiRequest<AiSession>(runtimeBase(systemId), {
      method: 'POST', body: input, idempotencyKey: crypto.randomUUID(),
    })
  },
  session(systemId: string, sessionId: string) {
    return apiRequest<AiSessionDetail>(`${runtimeBase(systemId)}/${encodeURIComponent(sessionId)}`)
  },
  submitMessage(systemId: string, sessionId: string, input: SubmitAiMessageInput) {
    return apiRequest<AiTurn>(
      `${runtimeBase(systemId)}/${encodeURIComponent(sessionId)}/messages`,
      { method: 'POST', body: input, idempotencyKey: crypto.randomUUID() },
    )
  },
  confirmation(systemId: string, confirmationId: string) {
    return apiRequest<AiConfirmation>(
      `${runtimeAiBase(systemId)}/confirmations/${encodeURIComponent(confirmationId)}`,
    )
  },
  confirm(
    systemId: string,
    confirmationId: string,
    expectedVersion: number,
    idempotencyKey: string,
  ) {
    return apiRequest<AiConfirmation>(
      `${runtimeAiBase(systemId)}/confirmations/${encodeURIComponent(confirmationId)}/confirm`,
      { method: 'POST', body: { expectedVersion }, idempotencyKey },
    )
  },
  reject(systemId: string, confirmationId: string, expectedVersion: number) {
    return apiRequest<AiConfirmation>(
      `${runtimeAiBase(systemId)}/confirmations/${encodeURIComponent(confirmationId)}/reject`,
      { method: 'POST', body: { expectedVersion } },
    )
  },
  configurationProposal(systemId: string, sessionId: string, proposalId: string) {
    return apiRequest<AiConfigurationFieldProposal>(
      `${runtimeBase(systemId)}/${encodeURIComponent(sessionId)}/configuration-proposals/${encodeURIComponent(proposalId)}`,
    )
  },
  confirmConfigurationProposal(
    systemId: string,
    sessionId: string,
    proposalId: string,
    expectedRevision: number,
    idempotencyKey: string,
  ) {
    return apiRequest<AiConfigurationFieldProposal>(
      `${runtimeBase(systemId)}/${encodeURIComponent(sessionId)}/configuration-proposals/${encodeURIComponent(proposalId)}/confirm`,
      { method: 'POST', body: { expectedRevision }, idempotencyKey },
    )
  },
  rejectConfigurationProposal(systemId: string, sessionId: string, proposalId: string, expectedRevision: number) {
    return apiRequest<AiConfigurationFieldProposal>(
      `${runtimeBase(systemId)}/${encodeURIComponent(sessionId)}/configuration-proposals/${encodeURIComponent(proposalId)}/reject`,
      { method: 'POST', body: { expectedRevision } },
    )
  },
  configurationArtifactProposal(systemId: string, sessionId: string, proposalId: string) {
    return apiRequest<AiConfigurationArtifactProposal>(
      `${runtimeBase(systemId)}/${encodeURIComponent(sessionId)}/configuration-artifact-proposals/${encodeURIComponent(proposalId)}`,
    )
  },
  confirmConfigurationArtifactProposal(
    systemId: string,
    sessionId: string,
    proposalId: string,
    expectedRevision: number,
    idempotencyKey: string,
  ) {
    return apiRequest<AiConfigurationArtifactProposal>(
      `${runtimeBase(systemId)}/${encodeURIComponent(sessionId)}/configuration-artifact-proposals/${encodeURIComponent(proposalId)}/confirm`,
      { method: 'POST', body: { expectedRevision }, idempotencyKey },
    )
  },
  rejectConfigurationArtifactProposal(systemId: string, sessionId: string, proposalId: string, expectedRevision: number) {
    return apiRequest<AiConfigurationArtifactProposal>(
      `${runtimeBase(systemId)}/${encodeURIComponent(sessionId)}/configuration-artifact-proposals/${encodeURIComponent(proposalId)}/reject`,
      { method: 'POST', body: { expectedRevision } },
    )
  },
  workProposal(systemId: string, sessionId: string, proposalId: string) {
    return apiRequest<AiWorkProposal>(
      `${runtimeBase(systemId)}/${encodeURIComponent(sessionId)}/work-proposals/${encodeURIComponent(proposalId)}`,
    )
  },
  confirmWorkProposal(
    systemId: string,
    sessionId: string,
    proposalId: string,
    expectedRevision: number,
    idempotencyKey: string,
  ) {
    return apiRequest<AiWorkProposal>(
      `${runtimeBase(systemId)}/${encodeURIComponent(sessionId)}/work-proposals/${encodeURIComponent(proposalId)}/confirm`,
      { method: 'POST', body: { expectedRevision }, idempotencyKey },
    )
  },
  rejectWorkProposal(systemId: string, sessionId: string, proposalId: string, expectedRevision: number) {
    return apiRequest<AiWorkProposal>(
      `${runtimeBase(systemId)}/${encodeURIComponent(sessionId)}/work-proposals/${encodeURIComponent(proposalId)}/reject`,
      { method: 'POST', body: { expectedRevision } },
    )
  },
  generatedDraftProposal(systemId: string, sessionId: string, proposalId: string) {
    return apiRequest<AiGeneratedDraftProposal>(
      `${runtimeBase(systemId)}/${encodeURIComponent(sessionId)}/generated-draft-proposals/${encodeURIComponent(proposalId)}`,
    )
  },
  confirmGeneratedDraftProposal(
    systemId: string,
    sessionId: string,
    proposalId: string,
    expectedRevision: number,
    idempotencyKey: string,
  ) {
    return apiRequest<AiGeneratedDraftProposal>(
      `${runtimeBase(systemId)}/${encodeURIComponent(sessionId)}/generated-draft-proposals/${encodeURIComponent(proposalId)}/confirm`,
      { method: 'POST', body: { expectedRevision }, idempotencyKey },
    )
  },
  rejectGeneratedDraftProposal(systemId: string, sessionId: string, proposalId: string, expectedRevision: number) {
    return apiRequest<AiGeneratedDraftProposal>(
      `${runtimeBase(systemId)}/${encodeURIComponent(sessionId)}/generated-draft-proposals/${encodeURIComponent(proposalId)}/reject`,
      { method: 'POST', body: { expectedRevision } },
    )
  },
}
