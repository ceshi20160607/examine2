<template>
  <AppLayout>
    <h2>选择系统</h2>
    <p class="muted">进入自建系统后才能管理应用与数据。</p>

    <div class="create-box">
      <input v-model="newSystemName" data-testid="system-name-input" placeholder="新系统名称" class="name-input" />
      <label class="check-label">
        <input v-model="newMultiTenant" type="checkbox" />
        多租户
      </label>
      <button type="button" data-testid="system-create-btn" :disabled="creating || !newSystemName.trim()" @click="createNew">
        {{ creating ? '创建中…' : '创建系统' }}
      </button>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <ul v-if="systems.length" class="list">
      <li v-for="s in systems" :key="s.id">
        <button type="button" class="list__btn" :disabled="entering === s.id" @click="enter(s)">
          <strong>{{ s.name || `System#${s.id}` }}</strong>
          <span class="muted">id={{ s.id }}</span>
        </button>
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
import { createSystem, enterSystem, listSystems, listTenants, selectTenant } from '../api/platform'

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
let pendingSystem = null

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
    await load()
  } catch (e) {
    error.value = e?.message || String(e)
  } finally {
    creating.value = false
  }
}

async function enter(s) {
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
    router.push('/apps')
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

onMounted(load)
</script>

<style scoped>
.muted {
  color: #666;
  font-size: 0.9rem;
}
.error {
  color: #c00;
}
.create-box {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  align-items: center;
  margin: 1rem 0;
  padding: 0.75rem;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
}
.name-input {
  flex: 1;
  min-width: 160px;
  padding: 0.5rem 0.65rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
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
}
.list__btn {
  width: 100%;
  text-align: left;
  padding: 0.75rem 1rem;
  margin-bottom: 0.5rem;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
}
.list__btn:hover {
  border-color: #1677ff;
}
.tenant-box {
  margin-top: 1rem;
  display: flex;
  gap: 0.5rem;
  align-items: center;
  flex-wrap: wrap;
}
</style>
