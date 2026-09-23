import type { WorkTask, WorkTaskGroup } from './types'

export function groupProjectTasks(tasks: WorkTask[], groups: WorkTaskGroup[]) {
  const ordered = [...groups].sort((a, b) => a.sortOrder - b.sortOrder)
  return [
    ...ordered.map(group => ({ id: String(group.id), name: group.name, details: group, tasks: tasks.filter(task => task.taskGroupId === group.id) })),
    { id: 'ungrouped', name: '未分组', details: undefined, tasks: tasks.filter(task => !task.taskGroupId) },
  ].filter(group => group.id !== 'ungrouped' || group.tasks.length)
}

export function nextTaskAction(status: WorkTask['status']) {
  if (status === 'TODO' || status === 'BACKLOG' || status === 'BLOCKED') return { status: 'IN_PROGRESS' as const, label: '开始任务' }
  return undefined
}
