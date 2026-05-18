<template>
  <view class="region-address">
    <picker v-if="useRegionPicker" mode="region" :value="regionArr" @change="onRegionPick">
      <view class="region-address__picker">
        {{ regionLabel || '选择省/市/区' }}
      </view>
    </picker>
    <uni-easyinput
      v-else
      v-model="regionText"
      placeholder="省市区（可手动输入）"
      @blur="syncOut"
    />
    <uni-easyinput
      v-if="showDetail"
      v-model="detailText"
      type="textarea"
      :autoHeight="true"
      placeholder="详细地址"
      @blur="syncOut"
    />
    <uni-button v-if="mapEnabled" size="mini" @click="pickMap">地图选点</uni-button>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { configFromMeta, isAddressMapEnabled } from '@/utils/fieldTypes'
import type { ModuleField } from '@/api/meta'

const props = defineProps<{ field: ModuleField; modelValue: unknown }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: string): void }>()

const cfg = computed(() => configFromMeta(props.field))
const useRegionPicker = computed(() => cfg.value.regionStyle !== 'text')
const showDetail = computed(() => cfg.value.detailMode !== 'none')
const mapEnabled = computed(() => isAddressMapEnabled(props.field))

const regionArr = ref<string[]>([])
const regionText = ref('')
const detailText = ref('')
const lat = ref<number | null>(null)
const lng = ref<number | null>(null)

const regionLabel = computed(() => {
  if (regionArr.value.length) return regionArr.value.join(' / ')
  return regionText.value
})

function parseIn(raw: unknown) {
  regionArr.value = []
  regionText.value = ''
  detailText.value = ''
  lat.value = null
  lng.value = null
  if (raw == null || raw === '') return
  let o: any = raw
  if (typeof raw === 'string') {
    const t = raw.trim()
    if (!t) return
    try {
      o = JSON.parse(t)
    } catch {
      regionText.value = t
      return
    }
  }
  if (o && typeof o === 'object') {
    const r = String(o.region || '')
    if (r.includes('/')) regionArr.value = r.split('/').map((x) => x.trim()).filter(Boolean)
    else regionText.value = r
    detailText.value = String(o.detail || '')
    if (o.lat != null) lat.value = Number(o.lat)
    if (o.lng != null) lng.value = Number(o.lng)
  }
}

function syncOut() {
  const region =
    regionArr.value.length > 0 ? regionArr.value.join('/') : (regionText.value || '').trim()
  const payload: Record<string, unknown> = {
    region,
    detail: detailText.value || ''
  }
  if (lat.value != null && lng.value != null) {
    payload.lat = lat.value
    payload.lng = lng.value
  }
  emit('update:modelValue', JSON.stringify(payload))
}

function onRegionPick(e: any) {
  regionArr.value = e?.detail?.value || []
  syncOut()
}

function pickMap() {
  uni.chooseLocation({
    success: (res) => {
      detailText.value = res.address || res.name || detailText.value
      lat.value = res.latitude
      lng.value = res.longitude
      syncOut()
      uni.showToast({ title: '已选点', icon: 'success' })
    },
    fail: () => uni.showToast({ title: '选点取消', icon: 'none' })
  })
}

watch(
  () => props.modelValue,
  (v) => parseIn(v),
  { immediate: true }
)
</script>

<style scoped>
.region-address {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.region-address__picker {
  padding: 10px 12px;
  border: 1px solid #e5e5e5;
  border-radius: 6px;
  color: #333;
}
</style>
