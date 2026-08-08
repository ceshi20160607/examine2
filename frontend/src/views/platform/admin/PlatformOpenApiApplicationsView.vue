<script setup lang="ts">
import type { TableColumnsType } from 'ant-design-vue'
import { message } from 'ant-design-vue'
import { KeyRound, Plus, RefreshCw } from 'lucide-vue-next'
import { onMounted, reactive, ref } from 'vue'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import { ApiRequestError } from '@/services/api'
import { platformOpenApiApi } from '@/services/platformOpenApi'
import type { PlatformOpenApiApplication } from '@/types/platformOpenApi'

const loading=ref(false),saving=ref(false),errorMessage=ref(''),editorOpen=ref(false),rotateOpen=ref(false)
const applications=ref<PlatformOpenApiApplication[]>([])
const form=reactive({serviceAccountId:'',name:'',secretRef:'',ipAllowlist:'127.0.0.1',rateLimitPerMinute:120})
const rotation=reactive({id:'',name:'',secretRef:'',version:0})
const columns:TableColumnsType=[{title:'应用',key:'application'},{title:'服务账号',dataIndex:'serviceAccountId',width:160},{title:'Scope',key:'scope',width:190},{title:'凭证版本',dataIndex:'credentialVersion',width:100},{title:'状态',dataIndex:'status',width:100},{title:'操作',key:'actions',width:220}]
function report(error:unknown){errorMessage.value=error instanceof ApiRequestError?error.message:'平台应用服务暂时不可用'}
async function load(){loading.value=true;errorMessage.value='';try{applications.value=(await platformOpenApiApi.list()).items}catch(e){report(e)}finally{loading.value=false}}
async function create(){if(!form.serviceAccountId.trim()||!form.name.trim()||!form.secretRef.trim())return;saving.value=true;try{await platformOpenApiApi.create({serviceAccountId:form.serviceAccountId,name:form.name,secretRef:form.secretRef,scopes:['platform.task.read'],ipAllowlist:form.ipAllowlist.split('\n').map(v=>v.trim()).filter(Boolean),rateLimitPerMinute:form.rateLimitPerMinute});editorOpen.value=false;message.success('平台应用已创建');await load()}catch(e){report(e)}finally{saving.value=false}}
function openRotate(app:PlatformOpenApiApplication){Object.assign(rotation,{id:app.id,name:app.name,secretRef:'',version:app.version});rotateOpen.value=true}
async function rotate(){if(!rotation.secretRef.trim())return;saving.value=true;try{await platformOpenApiApi.rotate(rotation.id,rotation.secretRef,rotation.version);rotateOpen.value=false;message.success('SecretRef 已轮换');await load()}catch(e){report(e)}finally{saving.value=false}}
async function toggle(app:PlatformOpenApiApplication){saving.value=true;try{await platformOpenApiApi.status(app.id,app.status==='ACTIVE'?'disable':'enable',app.version,app.status==='ACTIVE'?'管理员停用':'管理员启用');await load()}catch(e){report(e)}finally{saving.value=false}}
onMounted(load)
</script>
<template>
  <section class="admin-page">
    <AdminPageHeader title="平台开放应用" description="独立于系统 OpenAPI 的平台级机器身份；当前仅开放个人平台任务只读能力。">
      <template #actions><a-button @click="load"><RefreshCw :size="15" />刷新</a-button><a-button type="primary" @click="editorOpen=true"><Plus :size="15" />新建应用</a-button></template>
    </AdminPageHeader>
    <a-alert v-if="errorMessage" type="error" show-icon :message="errorMessage" />
    <a-alert type="info" show-icon message="签名范围" description="GET /openapi/v1/platform/tasks；scope 与服务账号实时权限均必须包含 platform.task.read。" />
    <a-table row-key="id" :columns="columns" :data-source="applications" :loading="loading" :pagination="false" :scroll="{ x: 980 }">
      <template #bodyCell="{column,record}">
        <template v-if="column.key==='application'"><div class="cell-title"><KeyRound :size="16" />{{ record.name }}</div><code>{{ record.appKey }}</code></template>
        <template v-else-if="column.key==='scope'"><a-tag v-for="scope in record.scopes" :key="scope">{{ scope }}</a-tag></template>
        <template v-else-if="column.key==='actions'"><a-space><a-button size="small" @click="openRotate(record)">轮换</a-button><a-button size="small" :danger="record.status==='ACTIVE'" :loading="saving" @click="toggle(record)">{{ record.status==='ACTIVE'?'停用':'启用' }}</a-button></a-space></template>
      </template>
    </a-table>
    <a-modal v-model:open="editorOpen" title="新建平台开放应用" :confirm-loading="saving" @ok="create"><a-form layout="vertical"><a-form-item label="服务账号 ID" required><a-input v-model:value="form.serviceAccountId" /></a-form-item><a-form-item label="名称" required><a-input v-model:value="form.name" /></a-form-item><a-form-item label="SecretRef" required><a-input v-model:value="form.secretRef" placeholder="env://PLATFORM_APP_SECRET" /></a-form-item><a-form-item label="IP 白名单（每行一个 CIDR/IP）"><a-textarea v-model:value="form.ipAllowlist" :rows="3" /></a-form-item><a-form-item label="每分钟限流"><a-input-number v-model:value="form.rateLimitPerMinute" :min="1" :max="60000" /></a-form-item></a-form></a-modal>
    <a-modal v-model:open="rotateOpen" :title="`轮换 ${rotation.name}`" :confirm-loading="saving" @ok="rotate"><a-alert type="warning" show-icon message="旧凭证会立即撤销" /><a-form layout="vertical"><a-form-item label="新 SecretRef" required><a-input v-model:value="rotation.secretRef" /></a-form-item></a-form></a-modal>
  </section>
</template>
<style scoped>.admin-page{display:grid;gap:16px}.cell-title{display:flex;align-items:center;gap:7px;font-weight:650}code{font-size:12px;color:var(--color-text-muted)}</style>
