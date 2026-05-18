<template>
  <div class="region-address">
    <input
      v-model="regionText"
      class="field__input"
      placeholder="省/市/区（用 / 分隔，如 浙江省/杭州市/西湖区）"
      @blur="syncOut"
    />
    <textarea
      v-if="showDetail"
      v-model="detailText"
      class="field__input"
      rows="2"
      placeholder="详细地址"
      @blur="syncOut"
    />
    <p v-if="mapEnabled" class="muted">地图选点请在移动端使用；Web 可手填经纬度（可选）</p>
    <div v-if="mapEnabled" class="region-address__geo">
      <input v-model.number="lat" type="number" step="any" placeholder="lat" class="field__input" @blur="syncOut" />
      <input v-model.number="lng" type="number" step="any" placeholder="lng" class="field__input" @blur="syncOut" />
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { configFromMeta, isAddressMapEnabled } from '../../utils/fieldTypes.js'

const props = defineProps({ field: { type: Object, required: true }, modelValue: { default: null } })
const emit = defineEmits(['update:modelValue'])

const cfg = computed(() => configFromMeta(props.field))
const showDetail = computed(() => cfg.value.detailMode !== 'none')
const mapEnabled = computed(() => isAddressMapEnabled(props.field))

const regionText = ref('')
const detailText = ref('')
const lat = ref(null)
const lng = ref(null)

function parseIn(raw) {
  regionText.value = ''
  detailText.value = ''
  lat.value = null
  lng.value = null
  if (raw == null || raw === '') return
  let o = raw
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
    regionText.value = String(o.region || '')
    detailText.value = String(o.detail || '')
    if (o.lat != null) lat.value = Number(o.lat)
    if (o.lng != null) lng.value = Number(o.lng)
  }
}

function syncOut() {
  const payload = {
    region: (regionText.value || '').trim(),
    detail: detailText.value || ''
  }
  if (lat.value != null && lng.value != null && !Number.isNaN(lat.value) && !Number.isNaN(lng.value)) {
    payload.lat = lat.value
    payload.lng = lng.value
  }
  emit('update:modelValue', JSON.stringify(payload))
}

watch(() => props.modelValue, parseIn, { immediate: true })
</script>

<style scoped>
.region-address {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.region-address__geo {
  display: flex;
  gap: 8px;
}
.field__input {
  width: 100%;
  padding: 0.45rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  box-sizing: border-box;
}
.muted {
  font-size: 0.8rem;
  color: #888;
}
</style>
