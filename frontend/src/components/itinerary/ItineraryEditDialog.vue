<template>
  <el-dialog
    :model-value="modelValue"
    title="编辑路线"
    width="980px"
    top="4vh"
    destroy-on-close
    @close="handleClose"
  >
    <div class="edit-dialog">
      <section class="edit-section">
        <div class="section-head">
          <div>
            <p class="section-label">每日时间</p>
            <h3>开始/结束都能改</h3>
          </div>
        </div>
        <div class="window-grid">
          <article v-for="window in dayWindows" :key="`window-${window.dayNo}`" class="window-card">
            <strong>第 {{ window.dayNo }} 天</strong>
            <div class="window-fields">
              <el-input v-model="window.startTime" placeholder="开始时间，如 09:00" />
              <el-input v-model="window.endTime" placeholder="结束时间，如 21:30" />
            </div>
          </article>
        </div>
      </section>

      <section class="edit-section">
        <div class="section-head">
          <div>
            <p class="section-label">现有节点</p>
            <h3>改停留、跨天移动、删除节点</h3>
          </div>
        </div>
        <div class="stop-list">
          <article v-for="stop in stops" :key="stop.nodeKey" class="stop-row">
            <div class="stop-title">
              <strong>{{ stop.poiName }}</strong>
              <span>{{ stop.nodeKey }}</span>
            </div>
            <div class="stop-fields">
              <el-input-number v-model="stop.dayNo" :min="1" :max="maxDayNo" controls-position="right" />
              <el-input-number v-model="stop.order" :min="1" controls-position="right" />
              <el-input-number v-model="stop.stayDuration" :min="0" :step="10" controls-position="right" />
              <el-switch v-model="stop.removed" active-text="删除" inactive-text="保留" />
            </div>
          </article>
        </div>
      </section>

      <section class="edit-section">
        <div class="section-head">
          <div>
            <p class="section-label">新增地点</p>
            <h3>支持新建自定义 POI，也能复用“我的地点”</h3>
          </div>
          <div class="section-actions">
            <el-button round @click="addCustomRow('inline')">新增自定义地点</el-button>
            <el-button round @click="addCustomRow('existing')">选择我的地点</el-button>
          </div>
        </div>
        <div v-if="customRows.length" class="custom-list">
          <article v-for="(row, index) in customRows" :key="`custom-${index}`" class="custom-row">
            <div class="custom-top">
              <el-select v-model="row.mode" class="mode-select">
                <el-option label="新建地点" value="inline" />
                <el-option label="我的地点" value="existing" />
              </el-select>
              <el-input-number v-model="row.dayNo" :min="1" :max="maxDayNo" controls-position="right" />
              <el-input-number v-model="row.order" :min="1" controls-position="right" />
              <el-input-number v-model="row.stayDuration" :min="0" :step="10" controls-position="right" />
              <el-button text type="danger" @click="removeCustomRow(index)">删除</el-button>
            </div>

            <div v-if="row.mode === 'existing'" class="custom-existing">
              <el-select v-model="row.customPoiId" placeholder="选择我的自定义地点" filterable>
                <el-option
                  v-for="item in customPoiOptions"
                  :key="item.id"
                  :label="`${item.name}｜${item.roughLocation || item.address || '未命名位置'}`"
                  :value="item.id"
                />
              </el-select>
            </div>

            <div v-else class="custom-inline-grid">
              <el-input v-model="row.name" placeholder="名称（必填）" />
              <el-input v-model="row.roughLocation" placeholder="粗略地点（必填，如某区某街道）" />
              <el-input v-model="row.category" placeholder="景点类型（可选）" />
              <el-input v-model="row.reason" placeholder="填写理由（可选）" />
            </div>
          </article>
        </div>
        <el-empty v-else description="还没有新增地点" />
      </section>

      <section v-if="versions.length" class="edit-section">
        <div class="section-head">
          <div>
            <p class="section-label">版本回退</p>
            <h3>回退到旧版本</h3>
          </div>
        </div>
        <div class="version-row">
          <el-select v-model="selectedVersionId" placeholder="选择一个历史版本" class="version-select">
            <el-option
              v-for="item in versions"
              :key="item.id"
              :label="`V${item.versionNo}｜${item.summary || '未命名版本'}${item.active ? '（当前）' : ''}`"
              :value="item.id"
            />
          </el-select>
          <el-button :loading="restoring" round @click="handleRestore">回退到所选版本</el-button>
        </div>
      </section>
    </div>

    <template #footer>
      <div class="footer-actions">
        <el-button round @click="handleClose">取消</el-button>
        <el-button type="primary" round :loading="saving" @click="handleApply">应用到当前行程</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  reqApplyItineraryEdits,
  reqListItineraryEditVersions,
  reqRestoreItineraryEditVersion
} from '@/api/itinerary'
import { reqListCustomPois } from '@/api/poi'
import {
  buildItineraryEditPayload,
  deriveEditableDayWindows,
  deriveEditableStops
} from '@/utils/itineraryEdit'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  itinerary: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['update:modelValue', 'applied'])

const saving = ref(false)
const restoring = ref(false)
const dayWindows = ref([])
const originalDayWindows = ref([])
const stops = ref([])
const originalStops = ref([])
const customRows = ref([])
const customPoiOptions = ref([])
const versions = ref([])
const selectedVersionId = ref(null)

const maxDayNo = computed(() => {
  const days = dayWindows.value.map(item => Number(item.dayNo) || 1)
  const customDays = customRows.value.map(item => Number(item.dayNo) || 1)
  return Math.max(1, ...days, ...customDays)
})

const resetForm = snapshot => {
  originalDayWindows.value = deriveEditableDayWindows(snapshot)
  dayWindows.value = originalDayWindows.value.map(item => ({ ...item }))
  originalStops.value = deriveEditableStops(snapshot)
  stops.value = originalStops.value.map(item => ({ ...item }))
  customRows.value = []
}

const loadAuxiliaryData = async () => {
  if (!props.itinerary?.id) {
    versions.value = []
    customPoiOptions.value = []
    return
  }
  try {
    const [versionData, customPoiData] = await Promise.all([
      reqListItineraryEditVersions(props.itinerary.id),
      reqListCustomPois()
    ])
    versions.value = Array.isArray(versionData) ? versionData : []
    customPoiOptions.value = Array.isArray(customPoiData) ? customPoiData : []
    selectedVersionId.value = versions.value.find(item => item.active)?.id || versions.value[0]?.id || null
  } catch (error) {
    versions.value = []
    customPoiOptions.value = []
  }
}

watch(
  () => props.modelValue,
  async visible => {
    if (!visible) return
    resetForm(props.itinerary)
    await loadAuxiliaryData()
  },
  { immediate: true }
)

watch(
  () => props.itinerary,
  snapshot => {
    if (!props.modelValue) return
    resetForm(snapshot)
  }
)

const addCustomRow = mode => {
  customRows.value.push({
    mode,
    dayNo: maxDayNo.value,
    order: 1,
    stayDuration: 60,
    customPoiId: null,
    name: '',
    roughLocation: '',
    category: '',
    reason: ''
  })
}

const removeCustomRow = index => {
  customRows.value.splice(index, 1)
}

const handleClose = () => {
  emit('update:modelValue', false)
}

const validateCustomRows = () => {
  for (const row of customRows.value) {
    if (row.mode === 'existing' && !row.customPoiId) {
      ElMessage.warning('请选择一个“我的地点”后再提交')
      return false
    }
    if (row.mode !== 'existing' && (!row.name?.trim() || !row.roughLocation?.trim())) {
      ElMessage.warning('新增自定义地点时，名称和粗略地点都必填')
      return false
    }
  }
  return true
}

const handleApply = async () => {
  if (!props.itinerary?.id) {
    ElMessage.warning('当前行程还没有保存，暂时不能编辑')
    return
  }
  if (!validateCustomRows()) return

  const payload = buildItineraryEditPayload({
    originalDayWindows: originalDayWindows.value,
    dayWindows: dayWindows.value,
    originalStops: originalStops.value,
    stops: stops.value,
    customRows: customRows.value
  })

  if (!payload.operations.length) {
    ElMessage.info('你还没有填写任何修改')
    return
  }

  saving.value = true
  try {
    const nextItinerary = await reqApplyItineraryEdits({
      itineraryId: props.itinerary.id,
      source: 'form',
      ...payload
    })
    emit('applied', nextItinerary)
    ElMessage.success('路线已生成新版本，并更新到当前行程')
    handleClose()
  } finally {
    saving.value = false
  }
}

const handleRestore = async () => {
  if (!props.itinerary?.id || !selectedVersionId.value) {
    ElMessage.warning('请先选择一个版本')
    return
  }
  restoring.value = true
  try {
    const nextItinerary = await reqRestoreItineraryEditVersion({
      itineraryId: props.itinerary.id,
      versionId: selectedVersionId.value
    })
    emit('applied', nextItinerary)
    ElMessage.success('已回退到所选版本')
    handleClose()
  } finally {
    restoring.value = false
  }
}
</script>

<style scoped>
.edit-dialog {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.edit-section {
  border: 1px solid rgba(223, 232, 244, 0.95);
  border-radius: 20px;
  padding: 20px;
  background: rgba(248, 252, 255, 0.9);
}

.section-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 16px;
}

.section-label {
  margin: 0 0 6px;
  color: #2d79c7;
  font-size: 12px;
  font-weight: 700;
}

.section-head h3 {
  margin: 0;
  color: #1f2d3d;
}

.window-grid,
.stop-list,
.custom-list {
  display: grid;
  gap: 14px;
}

.window-card,
.stop-row,
.custom-row {
  border-radius: 16px;
  border: 1px solid rgba(220, 230, 242, 0.92);
  background: #fff;
  padding: 16px;
}

.window-fields,
.stop-fields,
.custom-top,
.custom-inline-grid,
.version-row {
  display: grid;
  gap: 12px;
}

.window-fields,
.version-row {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.stop-fields {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.custom-inline-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-top: 12px;
}

.custom-top {
  grid-template-columns: 180px repeat(3, minmax(0, 1fr)) auto;
}

.section-actions,
.footer-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.stop-title {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  color: #627386;
}

.stop-title strong {
  color: #1f2d3d;
}

.mode-select,
.custom-existing :deep(.el-select),
.version-select {
  width: 100%;
}

@media (max-width: 900px) {
  .window-fields,
  .stop-fields,
  .custom-top,
  .custom-inline-grid,
  .version-row {
    grid-template-columns: 1fr;
  }
}
</style>
