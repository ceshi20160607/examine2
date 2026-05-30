export function idToString(value) {
  if (value == null) return ''
  const text = String(value).trim()
  if (!text || text === 'null' || text === 'undefined') return ''
  return text
}

export function hasId(value) {
  const id = idToString(value)
  return id !== '' && id !== '0'
}

export function sameId(left, right) {
  const a = idToString(left)
  const b = idToString(right)
  return a !== '' && a === b
}

export function uniqueIds(values) {
  const out = []
  const seen = new Set()
  for (const value of values || []) {
    const id = idToString(value)
    if (!hasId(id) || seen.has(id)) continue
    seen.add(id)
    out.push(id)
  }
  return out
}
