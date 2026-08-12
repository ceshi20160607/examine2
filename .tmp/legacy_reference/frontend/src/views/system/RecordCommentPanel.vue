<script setup lang="ts">
import { AtSign, MessageCircle, Pencil, RefreshCw, Reply, Send, Trash2 } from 'lucide-vue-next'
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { ApiRequestError } from '@/services/api'
import { recordTeamApi } from '@/services/collab'
import { recordCommentApi } from '@/services/comment'
import { useSessionStore } from '@/stores/session'
import type { RecordTeam } from '@/types/collab'
import type { RecordComment } from '@/types/comment'

const props = defineProps<{
  systemId: string
  moduleCode: string
  recordId: string
}>()

const session = useSessionStore()
const route = useRoute()
const comments = ref<RecordComment[]>([])
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const mutation = ref('')
const error = ref('')
const disabled = ref(false)
const body = ref('')
const replyTo = ref<RecordComment | null>(null)
const editing = ref<RecordComment | null>(null)
const editBody = ref('')
const mentionTeam = ref<RecordTeam | null>(null)
const mentionLoading = ref(false)
const mentionedMemberIds = ref<string[]>([])
let loadGeneration = 0
let teamGeneration = 0

const canView = computed(() =>
  session.hasPermission('system.runtime.access')
  && session.hasPermission(`module.${props.moduleCode}.view`))
const scopeKey = computed(() => [
  props.systemId,
  props.moduleCode,
  props.recordId,
  session.context?.tenantId ?? '',
  session.context?.permissionVersion ?? '',
].join(':'))
const focusedCommentId = computed(() => typeof route.query.comment === 'string'
  ? route.query.comment
  : '')
const mentionOptions = computed(() => (mentionTeam.value?.members ?? [])
  .filter(member => member.memberId !== session.context?.memberId)
  .map(member => ({
    value: member.memberId,
    label: `成员 ${member.memberId}（${roleLabel(member.role)}）`,
  })))

function roleLabel(role: string) {
  return ({ OWNER: '所有者', COLLABORATOR: '协作者', VIEWER: '查看者', FOLLOWER: '关注者' } as Record<string, string>)[role]
    ?? role
}

async function loadMentionTeam() {
  const generation = ++teamGeneration
  mentionTeam.value = null
  mentionedMemberIds.value = []
  if (!canView.value) return
  mentionLoading.value = true
  try {
    const result = await recordTeamApi.get(props.systemId, props.moduleCode, props.recordId)
    if (generation === teamGeneration) mentionTeam.value = result
  } catch (cause) {
    if (generation === teamGeneration
      && !(cause instanceof ApiRequestError && cause.code === 'RECORD_TEAM_NOT_FOUND')) {
      error.value = message(cause)
    }
  } finally {
    if (generation === teamGeneration) mentionLoading.value = false
  }
}

async function loadComments() {
  const generation = ++loadGeneration
  comments.value = []
  total.value = 0
  error.value = ''
  disabled.value = false
  if (!canView.value) {
    loading.value = false
    return
  }
  loading.value = true
  try {
    const result = await recordCommentApi.list(
      props.systemId,
      props.moduleCode,
      props.recordId,
      page.value,
    )
    if (generation !== loadGeneration) return
    comments.value = result.items
    total.value = result.total
  } catch (cause) {
    if (generation !== loadGeneration) return
    if (cause instanceof ApiRequestError && cause.code.includes('COMMENTS_DISABLED')) {
      disabled.value = true
    } else {
      error.value = message(cause)
    }
  } finally {
    if (generation === loadGeneration) loading.value = false
  }
}

async function createComment() {
  const content = body.value.trim()
  if (!content || content.length > 4000) return
  mutation.value = 'create'
  error.value = ''
  try {
    await recordCommentApi.create(props.systemId, props.moduleCode, props.recordId, {
      body: content,
      ...(replyTo.value ? { parentCommentId: replyTo.value.commentId } : {}),
      ...(mentionedMemberIds.value.length ? { mentionedMemberIds: mentionedMemberIds.value } : {}),
    })
    body.value = ''
    replyTo.value = null
    mentionedMemberIds.value = []
    const nextPage = Math.max(1, Math.ceil((total.value + 1) / 20))
    if (page.value === nextPage) await loadComments()
    else page.value = nextPage
  } catch (cause) {
    error.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

function beginReply(comment: RecordComment) {
  replyTo.value = comment
  body.value = ''
}

function beginEdit(comment: RecordComment) {
  editing.value = comment
  editBody.value = comment.body ?? ''
}

async function saveEdit() {
  if (!editing.value) return
  const content = editBody.value.trim()
  if (!content || content.length > 4000) return
  mutation.value = `edit:${editing.value.commentId}`
  error.value = ''
  try {
    await recordCommentApi.update(
      props.systemId,
      props.moduleCode,
      props.recordId,
      editing.value.commentId,
      { body: content, version: editing.value.version },
    )
    editing.value = null
    await loadComments()
  } catch (cause) {
    error.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

async function removeComment(comment: RecordComment) {
  mutation.value = `delete:${comment.commentId}`
  error.value = ''
  try {
    await recordCommentApi.remove(
      props.systemId,
      props.moduleCode,
      props.recordId,
      comment.commentId,
      comment.version,
    )
    await loadComments()
  } catch (cause) {
    error.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

function message(cause: unknown) {
  if (cause instanceof ApiRequestError) {
    if (cause.status === 409) return '评论已被其他人修改，请刷新后重试'
    return cause.message || cause.code
  }
  return cause instanceof Error ? cause.message : '评论请求失败，请稍后重试'
}

function time(value: string) {
  const parsed = new Date(value)
  return Number.isNaN(parsed.valueOf()) ? value : parsed.toLocaleString('zh-CN')
}

function resetScope() {
  page.value = 1
  body.value = ''
  replyTo.value = null
  editing.value = null
  mentionedMemberIds.value = []
  void loadMentionTeam()
  loadComments()
}

watch(scopeKey, resetScope, { immediate: true })
watch(page, loadComments)
</script>

<template>
  <section class="comment-panel" aria-label="记录评论">
    <header class="comment-heading">
      <div>
        <h3><MessageCircle :size="19" /> 评论</h3>
        <p>围绕当前记录补充信息和协作意见。</p>
      </div>
      <a-button v-if="canView && !disabled" :loading="loading" @click="loadComments">
        <RefreshCw :size="15" />刷新
      </a-button>
    </header>

    <a-alert
      v-if="!canView"
      type="warning"
      show-icon
      message="无权查看记录评论"
      :description="`需要 system.runtime.access 和 module.${moduleCode}.view 权限。`"
    />
    <a-alert
      v-else-if="disabled"
      type="info"
      show-icon
      message="当前模块未启用评论"
    />
    <template v-else>
      <a-alert v-if="error" type="error" show-icon closable :message="error" @close="error = ''" />
      <a-spin :spinning="loading">
        <a-empty v-if="!comments.length && !loading" description="还没有评论" />
        <div v-else class="comment-list">
          <article
            v-for="comment in comments"
            :key="comment.commentId"
            class="comment-item"
            :id="`record-comment-${comment.commentId}`"
            :class="{
              reply: Boolean(comment.parentCommentId),
              deleted: comment.deleted,
              focused: comment.commentId === focusedCommentId,
            }"
          >
            <div class="comment-meta">
              <strong>成员 {{ comment.authorMemberId }}</strong>
              <span>{{ time(comment.createdAt) }}</span>
              <span v-if="comment.updatedAt !== comment.createdAt">已编辑</span>
            </div>
            <p>{{ comment.deleted ? '该评论已删除' : comment.body }}</p>
            <div v-if="!comment.deleted && comment.mentionedMemberIds.length" class="comment-mentions">
              <AtSign :size="14" />
              <a-tag v-for="memberId in comment.mentionedMemberIds" :key="memberId">
                成员 {{ memberId }}
              </a-tag>
            </div>
            <div v-if="!comment.deleted" class="comment-actions">
              <a-button
                v-if="!comment.parentCommentId"
                type="text"
                size="small"
                :disabled="Boolean(mutation)"
                @click="beginReply(comment)"
              >
                <Reply :size="14" />回复
              </a-button>
              <a-button
                v-if="comment.canEdit"
                type="text"
                size="small"
                :disabled="Boolean(mutation)"
                @click="beginEdit(comment)"
              >
                <Pencil :size="14" />编辑
              </a-button>
              <a-popconfirm
                v-if="comment.canDelete"
                title="确认删除这条评论？"
                ok-text="删除"
                cancel-text="取消"
                @confirm="removeComment(comment)"
              >
                <a-button
                  type="text"
                  size="small"
                  danger
                  :loading="mutation === `delete:${comment.commentId}`"
                  :disabled="Boolean(mutation) && mutation !== `delete:${comment.commentId}`"
                >
                  <Trash2 :size="14" />删除
                </a-button>
              </a-popconfirm>
            </div>
          </article>
        </div>
      </a-spin>

      <a-pagination
        v-if="total > 20"
        v-model:current="page"
        :page-size="20"
        :total="total"
        :show-size-changer="false"
      />

      <div class="comment-composer">
        <div v-if="replyTo" class="replying">
          正在回复成员 {{ replyTo.authorMemberId }}
          <a-button type="link" size="small" @click="replyTo = null">取消</a-button>
        </div>
        <a-textarea
          v-model:value="body"
          :rows="3"
          :maxlength="4000"
          show-count
          placeholder="输入纯文本评论"
        />
        <a-select
          v-if="mentionTeam"
          v-model:value="mentionedMemberIds"
          class="mention-select"
          mode="multiple"
          :options="mentionOptions"
          :loading="mentionLoading"
          :max-tag-count="4"
          placeholder="@ 提及记录团队成员（可选，最多 20 人）"
        />
        <small v-else-if="!mentionLoading" class="mention-hint">
          初始化记录团队后可以在评论中 @ 提及成员。
        </small>
        <a-button
          type="primary"
          :loading="mutation === 'create'"
          :disabled="!body.trim() || Boolean(mutation)"
          @click="createComment"
        >
          <Send :size="15" />发布评论
        </a-button>
      </div>
    </template>

    <a-modal
      :open="Boolean(editing)"
      title="编辑评论"
      :confirm-loading="mutation.startsWith('edit:')"
      @cancel="editing = null"
      @ok="saveEdit"
    >
      <a-textarea v-model:value="editBody" :rows="5" :maxlength="4000" show-count />
    </a-modal>
  </section>
</template>

<style scoped>
.comment-panel {
  display: grid;
  gap: 14px;
  margin-top: 22px;
  padding-top: 20px;
  border-top: 1px solid #e2e8f0;
}

.comment-heading,
.comment-heading h3,
.comment-meta,
.comment-actions,
.comment-mentions,
.replying {
  display: flex;
  align-items: center;
}

.comment-heading {
  justify-content: space-between;
  gap: 16px;
}

.comment-heading h3 {
  gap: 8px;
  margin: 0;
}

.comment-heading p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
}

.comment-list {
  display: grid;
  gap: 9px;
}

.comment-item {
  padding: 13px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
}

.comment-item.reply {
  margin-left: 32px;
  border-left: 3px solid #94a3b8;
}

.comment-item.deleted {
  background: #f8fafc;
  color: #64748b;
}

.comment-item.focused {
  border-color: #2563eb;
  box-shadow: 0 0 0 2px rgb(37 99 235 / 12%);
}

.comment-meta {
  flex-wrap: wrap;
  gap: 8px 14px;
  color: #64748b;
  font-size: 12px;
}

.comment-meta strong {
  color: #334155;
  font-size: 13px;
}

.comment-item > p {
  margin: 9px 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.comment-actions {
  gap: 3px;
}

.comment-mentions {
  flex-wrap: wrap;
  gap: 5px;
  margin: 8px 0;
  color: #2563eb;
}

.comment-composer {
  display: grid;
  justify-items: end;
  gap: 9px;
}

.comment-composer :deep(.ant-input-textarea-affix-wrapper),
.comment-composer :deep(textarea) {
  width: 100%;
}

.mention-select {
  justify-self: stretch;
  width: 100%;
}

.mention-hint {
  justify-self: stretch;
  color: #64748b;
}

.replying {
  justify-self: stretch;
  color: #64748b;
  font-size: 13px;
}
</style>
