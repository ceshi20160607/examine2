const SESSION_KEY = 'examine_session'

export function getSession() {
  try {
    const raw = localStorage.getItem(SESSION_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

export function setSession(payload) {
  if (payload) localStorage.setItem(SESSION_KEY, JSON.stringify(payload))
  else localStorage.removeItem(SESSION_KEY)
}

export function hasSystemContext() {
  const s = getSession()
  return !!(s && s.systemId && Number(s.systemId) > 0)
}
