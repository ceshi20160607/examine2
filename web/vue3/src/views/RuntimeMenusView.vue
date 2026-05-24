<template>
  <AdminLayout>
    <h2>运行时菜单</h2>
    <p class="muted">appId={{ appId }}</p>
    <p v-if="error" class="error">{{ error }}</p>
    <ul v-if="menus.length" class="list">
      <li v-for="m in menus" :key="m.id">
        <button type="button" class="list__btn" :disabled="!m.pageId" @click="openPage(m)">
          {{ m.menuName }}
          <span class="muted">{{ m.pageId ? `pageId=${m.pageId}` : '未绑定页面' }}</span>
        </button>
      </li>
    </ul>
    <p v-else-if="!loading" class="muted">无可见菜单（检查角色菜单权限）</p>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import { listRuntimeMenus } from '../api/module'
import { getPageRuntime } from '../api/pages'

const route = useRoute()
const router = useRouter()
const appId = computed(() => String(route.params.appId || ''))
const menus = ref([])
const loading = ref(false)
const error = ref('')

async function openPage(m) {
  if (!m.pageId) return
  error.value = ''
  try {
    const r = await getPageRuntime(m.pageId)
    const rt = r.data
    if (!rt?.modelId) {
      error.value = '页面未配置 modelId'
      return
    }
    const type = String(rt.pageType || 'list').toLowerCase()
    const base = { appId: rt.appId, modelId: rt.modelId, pageId: rt.pageId }
    if (type === 'form') {
      router.push({ path: '/records/form', query: base })
    } else {
      router.push({ path: '/records', query: base })
    }
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

onMounted(async () => {
  loading.value = true
  try {
    const r = await listRuntimeMenus(appId.value)
    menus.value = r.data || []
  } catch (e) {
    error.value = e?.message || String(e)
  } finally {
    loading.value = false
  }
})
</script>

<style src="./admin-shared.css"></style>
<style scoped>
.list {
  list-style: none;
  padding: 0;
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
.list__btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.list__btn .muted {
  display: block;
  margin-top: 0.25rem;
  font-size: 0.85rem;
  color: #666;
}
</style>
