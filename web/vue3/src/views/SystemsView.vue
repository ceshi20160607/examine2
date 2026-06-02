<template>
  <AppLayout>
    <div class="page-head">
      <h2>选择系统</h2>
      <p class="muted">进入自建系统后，可继续管理应用、模型、页面、流程与数据。</p>
    </div>

    <div class="create-box">
      <input v-model="newSystemName" data-testid="system-name-input" placeholder="新系统名称" class="name-input" />
      <label class="check-label">
        <input v-model="newMultiTenant" type="checkbox" />
        多租户
      </label>
      <button
        type="button"
        data-testid="system-create-btn"
        :disabled="creating || !newSystemName.trim() || !canCreateSystem"
        @click="createNew"
      >
        {{ creating ? '创建中…' : '创建系统' }}
      </button>
    </div>
    <p v-if="permissionsLoaded && !canCreateSystem" class="muted">
      当前账号没有创建系统权限，可进入已授权的系统。
    </p>

    <p v-if="error" class="error">{{ error }}</p>
    <ul v-if="systems.length" class="list">
      <li v-for="s in systems" :key="s.id" class="system-row">
        <button type="button" class="list__btn" :disabled="entering === s.id || s.status !== 1" @click="enter(s)">
          <strong>{{ s.name || `System#${s.id}` }}</strong>
          <span class="muted">id={{ s.id }} · {{ s.multiTenantEnabled === 1 ? '多租户' : '单租户' }}</span>
        </button>
        <span :class="['status', s.status === 1 ? 'ok' : 'off']">{{ s.status === 1 ? '启用' : '停用' }}</span>
        <button v-if="canChangeSystemStatus" type="button" class="secondary" @click="toggleStatus(s)">{{ s.status === 1 ? '停用' : '启用' }}</button>
        <button v-if="canDeleteSystem" type="button" class="danger" @click="remove(s)">删除</button>
      </li>
    </ul>
    <p v-else-if="!loading" class="muted">暂无系统，请在上方创建。</p>
    <p v-if="tenantStep" class="tenant-box">
      <span>选择租户：</span>
      <select v-model="selectedTenantId">
        <option v-for="t in tenants" :key="t.id" :value="t.id">{{ t.tenantName || t.id }}</option>
      </select>
      <button type="button" @click="confirmTenant">确认</button>
    </p>
  </AppLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppLayout from '../layouts/AppLayout.vue'
import {
  createSystem,
  deleteSystem,
  enterSystem,
  listPlatformPermissions,
  listSystems,
  listTenants,
  selectTenant,
  setSystemStatus
} from '../api/platform'
import { confirmDialog } from '../utils/dialog.js'
import { notify } from '../utils/notify.js'

const router = useRouter()
const systems = ref([])
const tenants = ref([])
const loading = ref(false)
const creating = ref(false)
const error = ref('')
const entering = ref(0)
const tenantStep = ref(false)
const selectedTenantId = ref(0)
const newSystemName = ref('')
const newMultiTenant = ref(false)
const permissionsLoaded = ref(false)
const platformPermCodes = ref([])
const canCreateSystem = ref(true)
const canChangeSystemStatus = ref(true)
const canDeleteSystem = ref(true)
let pendingSystem = null

function hasPlatformPerm(code) {
  return platformPermCodes.value.includes(code)
}

async function loadPermissions() {
  try {
    const r = await listPlatformPermissions()
    const data = r.data || {}
    platformPermCodes.value = Array.isArray(data.platPermCodes) ? data.platPermCodes : []
    canCreateSystem.value = Number(data.canCreateSystem || 0) === 1 || hasPlatformPerm('SYSTEM_CREATE')
    canChangeSystemStatus.value = hasPlatformPerm('SYSTEM_STATUS')
    canDeleteSystem.value = hasPlatformPerm('SYSTEM_DELETE')
  } catch {
    canCreateSystem.value = true
    canChangeSystemStatus.value = true
    canDeleteSystem.value = true
  } finally {
    permissionsLoaded.value = true
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const r = await listSystems()
    systems.value = r.data || []
  } catch (e) {
    error.value = e?.message || String(e)
  } finally {
    loading.value = false
  }
}

async function createNew() {
  const name = newSystemName.value.trim()
  if (!name) return
  creating.value = true
  error.value = ''
  try {
    await createSystem(name, newMultiTenant.value ? 1 : 0)
    newSystemName.value = ''
    newMultiTenant.value = false
    notify.success('系统已创建')
    await load()
  } catch (e) {
    error.value = e?.message || String(e)
  } finally {
    creating.value = false
  }
}

async function enter(s) {
  if (s.status !== 1) {
    error.value = '系统已停用，请先启用后再进入'
    return
  }
  entering.value = s.id
  error.value = ''
  try {
    await enterSystem(s.id)
    if (s.multiTenantEnabled === 1) {
      pendingSystem = s
      const tr = await listTenants(s.id)
      tenants.value = tr.data || []
      if (tenants.value.length) {
        selectedTenantId.value = tenants.value[0].id
        tenantStep.value = true
      } else {
        error.value = '该系统无可用租户'
      }
    } else {
      router.push('/apps')
    }
  } catch (e) {
    error.value = e?.message || String(e)
  } finally {
    entering.value = 0
  }
}

async function confirmTenant() {
  if (!selectedTenantId.value) return
  try {
    await selectTenant(selectedTenantId.value)
    tenantStep.value = false
    pendingSystem = null
    router.push('/apps')
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function toggleStatus(s) {
  const next = s.status === 1 ? 2 : 1
  if (!(await confirmDialog(`确认${next === 1 ? '启用' : '停用'}系统 ${s.name || s.id}？`, {
    danger: next !== 1,
    confirmText: next === 1 ? '启用' : '停用'
  }))) return
  error.value = ''
  try {
    await setSystemStatus(s.id, next)
    notify.success(`系统已${next === 1 ? '启用' : '停用'}`)
    await load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function remove(s) {
  if (!(await confirmDialog(`删除系统 ${s.name || s.id}？该操作会停用系统入口。`, {
    danger: true,
    confirmText: '删除'
  }))) return
  error.value = ''
  try {
    await deleteSystem(s.id)
    notify.success('系统已删除')
    await load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

onMounted(load)
onMounted(loadPermissions)
</script>

<style scoped>
.muted {
  color: var(--color-muted);
  font-size: 0.9rem;
}
.error {
  color: var(--color-danger);
  background: var(--color-danger-soft);
  border: 1px solid #ffd3cf;
  border-radius: 7px;
  padding: 0.55rem 0.7rem;
}
.page-head {
  margin-bottom: 1rem;
}
.create-box {
  display: flex;
  flex-wrap: wrap;
  gap: 0.7rem;
  align-items: center;
  margin: 1rem 0 1.25rem;
  padding: 0.85rem;
  background: rgba(255, 255, 255, 0.78);
  border-radius: 8px;
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-sm);
}
.name-input {
  flex: 1;
  min-width: 160px;
  min-height: 38px;
  padding: 0.5rem 0.65rem;
  border: 1px solid var(--color-border-strong);
  border-radius: 7px;
}
.check-label {
  font-size: 0.9rem;
  display: flex;
  align-items: center;
  gap: 0.35rem;
}
.list {
  list-style: none;
  padding: 0;
  margin: 1rem 0;
  display: grid;
  gap: 0.65rem;
}
.system-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto auto;
  align-items: center;
  gap: 0.5rem;
}
.list__btn {
  width: 100%;
  text-align: left;
  padding: 0.85rem 1rem;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  box-shadow: var(--shadow-sm);
  transition: border-color 0.16s ease, transform 0.16s ease, box-shadow 0.16s ease;
}
.list__btn:hover:not(:disabled) {
  border-color: var(--color-primary);
  transform: translateY(-1px);
  box-shadow: 0 8px 20px rgba(31, 41, 51, 0.08);
}
.list__btn:disabled {
  cursor: not-allowed;
  opacity: 0.7;
}
.list__btn strong {
  display: block;
  margin-bottom: 0.15rem;
}
.status {
  min-width: 3rem;
  font-size: 0.86rem;
  font-weight: 800;
}
.status.ok { color: #047857; }
.status.off { color: #b45309; }
.tenant-box {
  margin-top: 1rem;
  display: flex;
  gap: 0.5rem;
  align-items: center;
  flex-wrap: wrap;
  padding: 0.75rem;
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: 8px;
}
.tenant-box select,
.tenant-box button,
.create-box button {
  min-height: 38px;
  padding: 0.45rem 0.75rem;
  border-radius: 7px;
}
.tenant-box select {
  border: 1px solid var(--color-border-strong);
  background: #fff;
}
.tenant-box button,
.create-box button {
  border: 1px solid var(--color-primary);
  background: var(--color-primary);
  color: #fff;
  font-weight: 700;
  cursor: pointer;
}
@media (max-width: 720px) {
  .system-row {
    grid-template-columns: 1fr 1fr;
  }
  .list__btn {
    grid-column: 1 / -1;
  }
}
</style>
