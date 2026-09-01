import type { AiWriteCandidate } from './types'

export function aiWriteCanConfirm(candidate: AiWriteCandidate | undefined, explicitlyConfirmed: boolean, title: string) {
  return Boolean(candidate && candidate.confirmationRequired && candidate.status !== 'REFUSED'
    && candidate.status !== 'CONFIRMED' && candidate.status !== 'CANCELLED'
    && !candidate.businessWritten && explicitlyConfirmed && title.trim())
}

export function normalizedAiWriteFields(fields: Record<string, unknown>) {
  return Object.fromEntries(Object.entries(fields)
    .filter(([, value]) => value !== undefined && value !== null && value !== ''))
}
