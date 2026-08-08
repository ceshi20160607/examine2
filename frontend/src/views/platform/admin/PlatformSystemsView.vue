<script setup lang="ts">
import type { TableColumnsType } from 'ant-design-vue'
import { message } from 'ant-design-vue'
import { Archive, Pencil, Plus, Power, RefreshCw, RotateCcw, Trash2 } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref } from 'vue'

import AdminMobileNotice from '@/components/admin/AdminMobileNotice.vue'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import { useAdminViewport } from '@/composables/useAdminViewport'
import { ApiRequestError } from '@/services/api'
import { platformAdminApi } from '@/services/admin'
import type { LifecycleStatus, PlatformSystem, SystemDeletePreview, SystemTombstone } from '@/types/admin'

const { isMobile } = useAdminViewport()
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const systems = ref<PlatformSystem[]>([])
const page = ref(1)
const total = ref(0)
const editorOpen = ref(false)
const commandOpen = ref(false)
const deletionOpen = ref(false)
const deletionPreview = ref<SystemDeletePreview | null>(null)
const deletionReason = ref('')
const tombstoneOpen = ref(false)
const tombstoneLoading = ref(false)
const tombstones = ref<SystemTombstone[]>([])
const selectedTombstone = ref<SystemTombstone | null>(null)
const restoreReason = ref('')
const command = ref<'activate' | 'disable' | 'archive' | 'restore' | 'retry-initialization'>('disable')
const form = reactive({ id: '', code: '', name: '', description: '', tenantMode: 'SINGLE' as 'SINGLE' | 'MULTI', version: '' })
const commandForm = reactive({ id: '', name: '', reason: '', version: '' })

const columns: TableColumnsType = [
  { title: '系统', key: 'system', width: 260 },
  { title: '租户模式', dataIndex: 'tenantMode', width: 110 },
  { title: '状态', dataIndex: 'status', width: 120 },
  { title: '说明', dataIndex: 'description' },
  { title: '操作', key: 'actions', width: 300, fixed: 'right' },
]

const statusText: Record<LifecycleStatus, string> = {
  INITIALIZING: '初始化中',
  INIT_FAILED: '初始化失败',
  ACTIVE: '运行中',
  DISABLED: '已停用',
  ARCHIVED: '已归档',
}

const pagination = computed(() => ({
  current: page.value,
  pageSize: 20,
  total: total.value,
  showSizeChanger: false,
  onChange: (next: number) => { page.value = next; void load() },
}))

function reportError(error: unknown) {
  errorMessage.value = error instanceof ApiRequestError ? error.message : '无法连接管理服务，请稍后重试'
}

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await platformAdminApi.listSystems({ page: page.value, size: 20 })
    systems.value = result.items
    total.value = result.total
  } catch (error) {
    reportError(error)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { id: '', code: '', name: '', description: '', tenantMode: 'SINGLE', version: '' })
  editorOpen.value = true
}

function openEdit(system: PlatformSystem) {
  Object.assign(form, system)
  editorOpen.value = true
}

async function save() {
  if (!form.name.trim() || (!form.id && !form.code.trim())) return
  saving.value = true
  try {
    if (form.id) {
      await platformAdminApi.updateSystem(form.id, { name: form.name, description: form.description, version: form.version })
    } else {
      await platformAdminApi.createSystem({ code: form.code, name: form.name, description: form.description, tenantMode: form.tenantMode })
    }
    editorOpen.value = false
    message.success('系统信息已保存')
    await load()
  } catch (error) {
    reportError(error)
  } finally {
    saving.value = false
  }
}

function openCommand(system: PlatformSystem, next: typeof command.value) {
  command.value = next
  Object.assign(commandForm, { id: system.id, name: system.name, reason: '', version: system.version })
  commandOpen.value = true
}

async function runCommand() {
  if (!commandForm.reason.trim()) return
  saving.value = true
  try {
    await platformAdminApi.commandSystem(commandForm.id, command.value, {
      reason: commandForm.reason,
      version: commandForm.version,
      impactConfirmed: true,
    })
    commandOpen.value = false
    message.success('系统状态已更新')
    await load()
  } catch (error) {
    reportError(error)
  } finally {
    saving.value = false
  }
}

async function openDeletion(system: PlatformSystem) {
  saving.value = true
  errorMessage.value = ''
  try {
    deletionPreview.value = await platformAdminApi.previewSystemDeletion(system.id)
    deletionReason.value = ''
    deletionOpen.value = true
  } catch (error) {
    reportError(error)
  } finally {
    saving.value = false
  }
}

async function confirmDeletion() {
  const preview = deletionPreview.value
  if (!preview?.eligible || !deletionReason.value.trim()) return
  saving.value = true
  try {
    await platformAdminApi.confirmSystemDeletion(preview.systemId, {
      previewId: preview.previewId, confirmationToken: preview.confirmationToken,
      expectedVersion: preview.systemVersion, reason: deletionReason.value.trim(), impactConfirmed: true,
    })
    deletionOpen.value = false
    deletionPreview.value = null
    message.success('系统已移入回收站，相关会话已撤销；可从回收站恢复')
    await load()
  } catch (error) {
    reportError(error)
  } finally {
    saving.value = false
  }
}

async function openTombstones() {
  tombstoneOpen.value = true
  tombstoneLoading.value = true
  try {
    tombstones.value = (await platformAdminApi.listSystemTombstones({ page: 1, size: 100 })).items
  } catch (error) { reportError(error) } finally { tombstoneLoading.value = false }
}

function selectTombstone(item: SystemTombstone) {
  selectedTombstone.value = item
  restoreReason.value = ''
}

async function restoreTombstone() {
  if (!selectedTombstone.value || !restoreReason.value.trim()) return
  saving.value = true
  try {
    await platformAdminApi.restoreSystemTombstone(selectedTombstone.value.id, {
      reason: restoreReason.value.trim(), version: selectedTombstone.value.version,
    })
    message.success('系统已从回收站恢复为归档状态')
    selectedTombstone.value = null
    await openTombstones()
    await load()
  } catch (error) { reportError(error) } finally { saving.value = false }
}

onMounted(load)
</script>

<template>
  <section class="admin-page">
    <AdminPageHeader title="系统治理" description="管理系统基础信息和访问生命周期。">
      <template v-if="!isMobile" #actions>
        <a-button :loading="loading" @click="load"><RefreshCw :size="16" />刷新</a-button>
        <a-button @click="openTombstones"><Trash2 :size="16" />回收站</a-button>
        <a-button type="primary" @click="openCreate"><Plus :size="16" />创建系统</a-button>
      </template>
    </AdminPageHeader>
    <a-alert v-if="errorMessage" class="admin-alert" type="error" show-icon :message="errorMessage" />

    <template v-if="isMobile">
      <AdminMobileNotice />
      <div v-if="systems.length" class="mobile-record-list">
        <article v-for="system in systems" :key="system.id" class="mobile-record">
          <div class="mobile-record-title"><strong>{{ system.name }}</strong><a-tag>{{ statusText[system.status] }}</a-tag></div>
          <dl><dt>编码</dt><dd>{{ system.code }}</dd><dt>租户</dt><dd>{{ system.tenantMode === 'MULTI' ? '多租户' : '单租户' }}</dd></dl>
        </article>
      </div>
      <a-empty v-else-if="!loading" description="暂无系统" />
      <a-spin v-else />
    </template>

    <div v-else class="admin-table-region">
      <a-table :columns="columns" :data-source="systems" :loading="loading" :pagination="pagination" row-key="id" :scroll="{ x: 960 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'system'"><strong>{{ record.name }}</strong><span class="cell-secondary">{{ record.code }}</span></template>
          <template v-else-if="column.dataIndex === 'tenantMode'">{{ record.tenantMode === 'MULTI' ? '多租户' : '单租户' }}</template>
          <template v-else-if="column.dataIndex === 'status'"><a-tag :color="record.status === 'ACTIVE' ? 'green' : record.status === 'INIT_FAILED' ? 'red' : 'default'">{{ statusText[record.status as LifecycleStatus] }}</a-tag><small v-if="record.initFailureCode" class="cell-secondary">{{ record.initFailureCode }}</small></template>
          <template v-else-if="column.key === 'actions'">
            <div class="table-actions">
              <a-button type="link" size="small" @click="openEdit(record as PlatformSystem)"><Pencil :size="14" />编辑</a-button>
              <a-button v-if="record.status === 'ACTIVE'" type="link" size="small" danger @click="openCommand(record as PlatformSystem, 'disable')"><Power :size="14" />停用</a-button>
              <a-button v-if="record.status === 'DISABLED'" type="link" size="small" @click="openCommand(record as PlatformSystem, 'activate')"><Power :size="14" />启用</a-button>
              <a-button v-if="record.status === 'DISABLED'" type="link" size="small" @click="openCommand(record as PlatformSystem, 'archive')"><Archive :size="14" />归档</a-button>
              <a-button v-if="record.status === 'ARCHIVED'" type="link" size="small" @click="openCommand(record as PlatformSystem, 'restore')"><Archive :size="14" />恢复</a-button>
              <a-button v-if="record.status === 'ARCHIVED'" type="link" size="small" danger @click="openDeletion(record as PlatformSystem)"><Trash2 :size="14" />移入回收站</a-button>
              <a-button v-if="record.status === 'INIT_FAILED'" type="link" size="small" @click="openCommand(record as PlatformSystem, 'retry-initialization')"><RotateCcw :size="14" />重试初始化</a-button>
            </div>
          </template>
        </template>
      </a-table>
    </div>

    <a-modal v-model:open="editorOpen" :title="form.id ? '编辑系统' : '创建系统'">
      <a-form :model="form" layout="vertical">
        <a-form-item label="系统名称" name="name" required><a-input v-model:value="form.name" :maxlength="64" /></a-form-item>
        <a-form-item v-if="!form.id" label="系统编码" name="code" required><a-input v-model:value="form.code" :maxlength="32" /></a-form-item>
        <a-form-item v-if="!form.id" label="租户模式" name="tenantMode"><a-select v-model:value="form.tenantMode"><a-select-option value="SINGLE">单租户</a-select-option><a-select-option value="MULTI">多租户</a-select-option></a-select></a-form-item>
        <a-form-item label="系统说明" name="description"><a-textarea v-model:value="form.description" :rows="4" :maxlength="500" /></a-form-item>
      </a-form>
      <template #footer>
        <a-button @click="editorOpen = false">取消</a-button>
        <a-button type="primary" :loading="saving" @click="save">保存</a-button>
      </template>
    </a-modal>
    <a-modal v-model:open="commandOpen" title="确认生命周期变更">
      <p>对象：{{ commandForm.name }}</p>
      <a-form layout="vertical"><a-form-item label="变更原因" required><a-textarea v-model:value="commandForm.reason" :rows="3" :maxlength="300" /></a-form-item></a-form>
      <template #footer>
        <a-button @click="commandOpen = false">取消</a-button>
        <a-button type="primary" :loading="saving" @click="runCommand">确认执行</a-button>
      </template>
    </a-modal>
    <a-modal v-model:open="deletionOpen" title="移入系统回收站" width="720px">
      <template v-if="deletionPreview">
        <a-alert v-if="!deletionPreview.eligible" type="error" show-icon message="当前系统不可删除" :description="deletionPreview.blockers.join('、')" />
        <a-alert v-else type="warning" show-icon message="系统将进入可恢复回收站；活动会话会被撤销，租户会被停用。" />
        <a-descriptions class="deletion-impact" bordered :column="1" size="small">
          <a-descriptions-item label="影响指纹">{{ deletionPreview.impactFingerprint }}</a-descriptions-item>
          <a-descriptions-item label="确认有效期">{{ deletionPreview.expiresAt }}</a-descriptions-item>
          <a-descriptions-item label="依赖对象">
            <a-empty v-if="!Object.keys(deletionPreview.dependencies).length" description="无依赖数据" />
            <a-space v-else wrap><a-tag v-for="(count, table) in deletionPreview.dependencies" :key="table">{{ table }}: {{ count }}</a-tag></a-space>
          </a-descriptions-item>
        </a-descriptions>
        <a-form layout="vertical"><a-form-item label="移入回收站原因" required><a-textarea v-model:value="deletionReason" :rows="3" :maxlength="1000" /></a-form-item></a-form>
      </template>
      <template #footer><a-button @click="deletionOpen = false">取消</a-button><a-button type="primary" danger :loading="saving" :disabled="!deletionPreview?.eligible || !deletionReason.trim()" @click="confirmDeletion">确认移入回收站</a-button></template>
    </a-modal>
    <a-modal v-model:open="tombstoneOpen" title="系统回收站" width="760px">
      <a-spin v-if="tombstoneLoading" />
      <div v-else class="tombstone-list">
        <article v-for="item in tombstones" :key="item.id">
          <div><strong>{{ item.name }}</strong><small>{{ item.code }} · {{ item.deletedAt }}</small><p>{{ item.reason }}</p></div>
          <a-button @click="selectTombstone(item)"><RotateCcw :size="14" />恢复</a-button>
        </article>
        <a-empty v-if="!tombstones.length" description="回收站为空" />
      </div>
      <a-form v-if="selectedTombstone" layout="vertical"><a-alert type="info" show-icon :message="`恢复 ${selectedTombstone.name} 为归档状态`" /><a-form-item label="恢复原因" required><a-textarea v-model:value="restoreReason" :rows="3" :maxlength="1000" /></a-form-item></a-form>
      <template #footer><a-button @click="tombstoneOpen = false">关闭</a-button><a-button type="primary" :loading="saving" :disabled="!selectedTombstone || !restoreReason.trim()" @click="restoreTombstone">确认恢复</a-button></template>
    </a-modal>
  </section>
</template>
