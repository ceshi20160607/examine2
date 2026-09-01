import type { CommandCenterItem } from './types'

const GROUP_ORDER = ['MENU', 'CREATE', 'MODULE', 'RECORD']

export function groupCommandResults(items: CommandCenterItem[]) {
  const groups = new Map<string, { code: string; name: string; items: CommandCenterItem[] }>()
  items.forEach((item) => {
    if (!groups.has(item.groupCode)) groups.set(item.groupCode, { code: item.groupCode, name: item.groupName, items: [] })
    groups.get(item.groupCode)?.items.push(item)
  })
  return [...groups.values()].sort((left, right) => {
    const leftOrder = GROUP_ORDER.indexOf(left.code)
    const rightOrder = GROUP_ORDER.indexOf(right.code)
    return (leftOrder < 0 ? GROUP_ORDER.length : leftOrder) - (rightOrder < 0 ? GROUP_ORDER.length : rightOrder)
  })
}

export function shouldOpenCommandCenter(event: Pick<KeyboardEvent, 'key' | 'ctrlKey' | 'metaKey' | 'target'>) {
  if (!(event.ctrlKey || event.metaKey) || event.key.toLowerCase() !== 'k') return false
  const target = event.target as { tagName?: string; isContentEditable?: boolean } | null
  if (!target?.tagName) return true
  return !['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName) && !target.isContentEditable
}
