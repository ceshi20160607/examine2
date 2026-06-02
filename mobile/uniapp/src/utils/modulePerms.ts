import { ref } from 'vue'
import { listModulePermissions } from '@/api/module'

export const MODULE_PERMS = Object.freeze({
  apps: 'mod.menu.moduleApp',
  models: 'mod.menu.moduleModel',
  fields: 'mod.menu.moduleField',
  relations: 'mod.menu.moduleRelation',
  dicts: 'mod.menu.moduleDict',
  departments: 'mod.menu.moduleDept',
  pages: 'mod.menu.modulePage',
  listViews: 'mod.menu.moduleListView',
  exports: 'mod.menu.moduleExport',
  exportJobs: 'mod.menu.moduleExportJob',
  rbac: 'mod.menu.moduleRbac',
  flowBindings: 'mod.menu.moduleFlowBinding',
  records: 'mod.menu.moduleRecord',
  uploads: 'mod.menu.upload',
  flowInbox: 'mod.menu.flowInbox',
  flow: 'mod.menu.flow'
})

export function createModulePermState() {
  const loaded = ref(false)
  const ownerWildcard = ref(false)
  const permKeys = ref<Set<string>>(new Set())
  const loadError = ref('')

  async function loadModulePerms() {
    try {
      const r = await listModulePermissions()
      const data: any = r.data || {}
      ownerWildcard.value = data.ownerWildcard === true || data.ownerWildcard === 1
      const keys = Array.isArray(data.permKeys) ? data.permKeys : []
      permKeys.value = new Set(keys.map((x: unknown) => String(x || '').trim()).filter(Boolean))
      loadError.value = ''
    } catch (e: any) {
      loadError.value = e?.message ?? String(e)
      ownerWildcard.value = false
      permKeys.value = new Set()
    } finally {
      loaded.value = true
    }
  }

  function hasModulePerm(key: string): boolean {
    if (!loaded.value) return true
    if (ownerWildcard.value || permKeys.value.has('*')) return true
    return permKeys.value.has(key)
  }

  return {
    loaded,
    ownerWildcard,
    permKeys,
    loadError,
    loadModulePerms,
    hasModulePerm
  }
}
