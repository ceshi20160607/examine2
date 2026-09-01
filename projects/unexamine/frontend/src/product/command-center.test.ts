import { describe, expect, it } from 'vitest'
import { groupCommandResults, shouldOpenCommandCenter } from './command-center'
import type { CommandCenterItem } from './types'

function item(id: string, groupCode: CommandCenterItem['groupCode']): CommandCenterItem {
  return { id, groupCode, groupName: groupCode, label: id, description: id, target: 'HOME' }
}

describe('command center presentation', () => {
  it('groups command results in daily-use order without changing items inside a group', () => {
    const grouped = groupCommandResults([
      item('record:customer:1', 'RECORD'), item('module:customer', 'MODULE'),
      item('menu:home', 'MENU'), item('create:customer', 'CREATE'), item('module:opportunity', 'MODULE'),
    ])
    expect(grouped.map((group) => group.code)).toEqual(['MENU', 'CREATE', 'MODULE', 'RECORD'])
    expect(grouped.find((group) => group.code === 'MODULE')?.items.map((entry) => entry.id))
      .toEqual(['module:customer', 'module:opportunity'])
  })

  it('opens with Ctrl/Cmd K outside inputs and leaves typing controls alone', () => {
    const body = { tagName: 'BODY', isContentEditable: false } as unknown as EventTarget
    const input = { tagName: 'INPUT', isContentEditable: false } as unknown as EventTarget
    expect(shouldOpenCommandCenter({ key: 'k', ctrlKey: true, metaKey: false, target: body })).toBe(true)
    expect(shouldOpenCommandCenter({ key: 'K', ctrlKey: false, metaKey: true, target: body })).toBe(true)
    expect(shouldOpenCommandCenter({ key: 'k', ctrlKey: true, metaKey: false, target: input })).toBe(false)
  })
})
