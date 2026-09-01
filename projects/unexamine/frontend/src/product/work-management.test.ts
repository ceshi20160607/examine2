import { describe, expect, it } from 'vitest'
import { groupProjectTasks, nextTaskAction } from './work-management'
import type { WorkTask } from './types'

describe('project task presentation', () => {
  it('keeps group order and one source task record', () => {
    const task = { id: 7, taskGroupId: 2, status: 'TODO' } as WorkTask
    const result = groupProjectTasks([task], [
      { id: 2, name: '实现', sortOrder: 20, status: 'ACTIVE', version: 0 },
      { id: 1, name: '设计', sortOrder: 10, status: 'ACTIVE', version: 0 },
    ])
    expect(result.map(group => group.name)).toEqual(['设计', '实现'])
    expect(result.flatMap(group => group.tasks).map(item => item.id)).toEqual([7])
  })

  it('offers only legal primary lifecycle actions', () => {
    expect(nextTaskAction('TODO')).toEqual({ status: 'IN_PROGRESS', label: '开始任务' })
    expect(nextTaskAction('IN_PROGRESS')).toEqual({ status: 'COMPLETED', label: '完成任务' })
    expect(nextTaskAction('COMPLETED')).toEqual({ status: 'IN_PROGRESS', label: '重新打开' })
  })
})
