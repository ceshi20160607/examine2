export type RbacMenuRow = {
  id: number
  parentId?: number
  menuName?: string
  permKey?: string
  apiPattern?: string
  pageId?: number
  sortNo?: number
}

export type RbacMenuFlatRow = RbacMenuRow & { depth: number }

function menuCmp(a: RbacMenuRow, b: RbacMenuRow) {
  const sa = Number(a.sortNo ?? 0)
  const sb = Number(b.sortNo ?? 0)
  if (sa !== sb) return sa - sb
  return Number(a.id) - Number(b.id)
}

function buildChildrenMap(rows: RbacMenuRow[]): Map<number, RbacMenuRow[]> {
  const map = new Map<number, RbacMenuRow[]>()
  for (const m of rows) {
    const pid = Number(m.parentId ?? 0) || 0
    const list = map.get(pid) || []
    list.push(m)
    map.set(pid, list)
  }
  for (const [, list] of map) {
    list.sort(menuCmp)
  }
  return map
}

/**
 * Flatten menus in tree order (DFS from parentId=0), with indentation depth.
 * Orphan nodes (missing parent) are appended with a marker prefix.
 */
export function flattenRbacMenusTree(rows: RbacMenuRow[]): RbacMenuFlatRow[] {
  if (!rows.length) return []
  const byParent = buildChildrenMap(rows)
  const out: RbacMenuFlatRow[] = []
  const seen = new Set<number>()

  const visit = (parentId: number, depth: number) => {
    const kids = byParent.get(parentId) || []
    for (const m of kids) {
      const id = Number(m.id)
      if (!id || seen.has(id)) continue
      seen.add(id)
      out.push({ ...m, depth })
      visit(id, depth + 1)
    }
  }

  visit(0, 0)

  const idSet = new Set(rows.map((x) => Number(x.id)).filter((x) => !!x))
  const orphans = rows.filter((m) => {
    const pid = Number(m.parentId ?? 0) || 0
    const id = Number(m.id)
    if (!id) return false
    if (seen.has(id)) return false
    if (pid === 0) return false
    return !idSet.has(pid)
  })

  if (orphans.length) {
    orphans.sort(menuCmp)
    for (const m of orphans) {
      const id = Number(m.id)
      if (!id || seen.has(id)) continue
      seen.add(id)
      out.push({
        ...m,
        depth: 0,
        menuName: `(orphan) ${m.menuName || `Menu#${m.id}`}`
      })
      visit(id, 1)
    }
  }

  return out
}

export function rbacMenuTitleIndented(m: RbacMenuFlatRow) {
  const pad = m.depth > 0 ? `${'　'.repeat(m.depth)}└ ` : ''
  const name = m.menuName || `Menu#${m.id}`
  return `${pad}${name}`
}

export function rbacMenuNote(m: RbacMenuFlatRow) {
  const bits: string[] = []
  bits.push(`id=${m.id}`)
  bits.push(`parent=${Number(m.parentId ?? 0) || 0}`)
  if (m.permKey) bits.push(`perm=${m.permKey}`)
  if (m.apiPattern) bits.push(`api=${m.apiPattern}`)
  return bits.join(' ')
}
