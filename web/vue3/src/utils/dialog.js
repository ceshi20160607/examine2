import { reactive } from 'vue'

let nextId = 1

export const dialogState = reactive({
  current: null
})

function openDialog(options) {
  return new Promise((resolve) => {
    dialogState.current = {
      id: nextId++,
      title: options.title || '确认操作',
      message: options.message || '',
      kind: options.kind || 'confirm',
      value: options.defaultValue || '',
      multiline: options.multiline === true,
      confirmText: options.confirmText || '确定',
      cancelText: options.cancelText || '取消',
      danger: options.danger === true,
      resolve
    }
  })
}

export function closeDialog(result) {
  const cur = dialogState.current
  if (!cur) return
  dialogState.current = null
  cur.resolve(result)
}

export async function confirmDialog(message, options = {}) {
  const result = await openDialog({
    ...options,
    kind: 'confirm',
    message,
    confirmText: options.confirmText || '确定'
  })
  return result === true
}

export async function promptText(title, options = {}) {
  const result = await openDialog({
    ...options,
    kind: 'prompt',
    title,
    confirmText: options.confirmText || '确定'
  })
  return typeof result === 'string' ? result : null
}
