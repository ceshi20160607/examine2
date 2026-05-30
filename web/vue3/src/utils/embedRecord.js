import { hasId, idToString } from './id.js'

const KEY = 'examine_embed_record'

export function setEmbedRecordCreated(recordId) {
  const id = idToString(recordId)
  if (hasId(id)) sessionStorage.setItem(KEY, id)
}

export function takeEmbedRecordCreated() {
  const raw = sessionStorage.getItem(KEY)
  sessionStorage.removeItem(KEY)
  if (!raw) return null
  const id = idToString(raw)
  return hasId(id) ? id : null
}
