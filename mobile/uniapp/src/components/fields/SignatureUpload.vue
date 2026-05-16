<template>
  <view class="signature-upload">
    <image v-if="previewUrl" :src="previewUrl" mode="aspectFit" class="signature-upload__img" />
    <view v-else class="signature-upload__placeholder">未签名</view>
    <ActionBar>
      <uni-button size="mini" @click="choose">选择/拍摄签名图</uni-button>
      <uni-button size="mini" @click="clear">清除</uni-button>
    </ActionBar>
    <view v-if="fileIdLabel" class="u-subtitle">fileId: {{ fileIdLabel }}</view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import ActionBar from '@/ui/ActionBar.vue'
import { pickSingleFilePath, uploadOneFile } from '@/api/upload'

const props = defineProps<{ modelValue?: number | string | null }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: number | null): void }>()

const previewUrl = ref('')
const localPath = ref('')

const fileIdLabel = computed(() => {
  const v = props.modelValue
  if (v == null || v === '') return ''
  return String(v)
})

watch(
  () => props.modelValue,
  () => {
    if (!props.modelValue) previewUrl.value = localPath.value || ''
  }
)

function clear() {
  previewUrl.value = ''
  localPath.value = ''
  emit('update:modelValue', null)
}

async function choose() {
  try {
    const path = await pickSingleFilePath()
    localPath.value = path
    previewUrl.value = path
    const r = await uploadOneFile(path)
    const id = r.data?.fileId
    if (id) {
      emit('update:modelValue', id)
      uni.showToast({ title: '签名已上传', icon: 'success' })
    }
  } catch (e: any) {
    uni.showToast({ title: e?.message || '上传失败', icon: 'none' })
  }
}
</script>

<style scoped>
.signature-upload__img {
  width: 100%;
  max-height: 160px;
  background: #fafafa;
  border: 1px dashed #ddd;
  border-radius: 8px;
}
.signature-upload__placeholder {
  padding: 24px;
  text-align: center;
  color: #888;
  border: 1px dashed #ddd;
  border-radius: 8px;
}
</style>
