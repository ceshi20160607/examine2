import { reactive } from 'vue'

let nextId = 1

export const notifications = reactive([])

function push(type, message, timeout = 2600) {
  const text = String(message || '').trim()
  if (!text) return null
  const item = { id: nextId++, type, message: text }
  notifications.push(item)
  window.setTimeout(() => remove(item.id), timeout)
  return item.id
}

export function remove(id) {
  const idx = notifications.findIndex((item) => item.id === id)
  if (idx >= 0) notifications.splice(idx, 1)
}

export const notify = {
  success(message, timeout) {
    return push('success', message, timeout)
  },
  error(message, timeout = 4200) {
    return push('error', message, timeout)
  },
  warn(message, timeout = 3600) {
    return push('warn', message, timeout)
  },
  info(message, timeout) {
    return push('info', message, timeout)
  }
}
