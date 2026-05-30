export type IdValue = string | number | null | undefined

export function idToString(value: IdValue): string {
  if (value == null) return ''
  const text = String(value).trim()
  if (!text || text === 'null' || text === 'undefined') return ''
  return text
}

export function hasId(value: IdValue): boolean {
  const id = idToString(value)
  return id !== '' && id !== '0'
}

export function sameId(left: IdValue, right: IdValue): boolean {
  const a = idToString(left)
  const b = idToString(right)
  return a !== '' && a === b
}

export function uniqueIds(values: IdValue[] | readonly IdValue[] | null | undefined): string[] {
  const out: string[] = []
  const seen = new Set<string>()
  for (const value of values || []) {
    const id = idToString(value)
    if (!hasId(id) || seen.has(id)) continue
    seen.add(id)
    out.push(id)
  }
  return out
}
