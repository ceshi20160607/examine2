export function appHubLinks(appId) {
  const id = Number(appId)
  const p = (suffix, label) => ({ to: `/apps/${id}${suffix}`, label })
  return [
    p('', '概览'),
    p('/models', '模型与字段'),
    p('/dicts', '字典'),
    p('/depts', '部门'),
    p('/relations', '模型关系'),
    p('/pages', '页面设计'),
    p('/list-views', '列表视图'),
    p('/exports', '导出模板'),
    p('/flow-bindings', '流程绑定'),
    p('/rbac', '权限 RBAC'),
    p('/menus', '运行时菜单')
  ]
}

export const flowLinks = [
  { to: '/flow/inbox', label: '待办箱' },
  { to: '/flow/start', label: '发起流程' },
  { to: '/flow/instances', label: '流程实例' },
  { to: '/flow/temps', label: '流程模板' }
]

export const platformLinks = [
  { to: '/systems', label: '系统与租户' },
  { to: '/platform/inbox', label: '平台消息/待办' },
  { to: '/platform/open-apps', label: '开放应用' },
  { to: '/upload', label: '文件上传' },
  { to: '/export-jobs', label: '导出任务' }
]
