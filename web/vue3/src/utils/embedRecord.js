const KEY = 'examine_embed_record'

export function setEmbedRecordCreated(recordId) {
  sessionStorage.setItem(KEY, String(recordId))
}

export function takeEmbedRecordCreated() {
  const raw = sessionStorage.getItem(KEY)
  sessionStorage.removeItem(KEY)
  if (!raw) return null
  const id = Number(raw)
  return id > 0 ? id : null
}
