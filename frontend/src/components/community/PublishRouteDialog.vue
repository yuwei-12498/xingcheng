<template>
  <el-dialog
    :model-value="modelValue"
    width="920px"
    destroy-on-close
    class="publish-dialog"
    @close="handleClose"
  >
    <template #header>
      <div class="dialog-header">
        <p class="dialog-kicker">PUBLISH ROUTE POST</p>
        <h2>发布路线帖</h2>
        <span>从“公开”升级为真正可阅读、可浏览、可互动的社区分享帖。</span>
      </div>
    </template>

    <el-steps :active="activeStep" finish-status="success" class="step-bar">
      <el-step title="确认路线版本" />
      <el-step title="补充分享内容" />
      <el-step title="发布预览" />
    </el-steps>

    <section v-if="activeStep === 0" class="step-panel version-panel">
      <template v-if="displayOptions.length > 1">
        <div
          v-for="option in displayOptions"
          :key="option.optionKey"
          class="version-card"
          :class="{ active: form.selectedOptionKey === option.optionKey }"
          @click="form.selectedOptionKey = option.optionKey"
        >
          <div>
            <p class="version-label">{{ option.title }}</p>
            <h3>{{ option.subtitle }}</h3>
          </div>
          <div class="version-meta">
            <span>{{ formatDuration(option.totalDuration) }}</span>
            <span>¥{{ option.totalCost ?? 0 }}</span>
            <span>{{ option.stopCount }} 站</span>
          </div>
          <p class="version-copy">{{ option.summary || option.recommendReason || '选一条最适合被分享出去的路线版本。' }}</p>
        </div>
      </template>

      <article v-else class="version-card version-card-single active">
        <div>
          <p class="version-label">{{ previewOption.title || '当前路线' }}</p>
          <h3>{{ previewOption.subtitle || '当前结果仅保留 1 条主路线' }}</h3>
        </div>
        <div class="version-meta">
          <span>{{ formatDuration(previewOption.totalDuration) }}</span>
          <span>¥{{ previewOption.totalCost ?? 0 }}</span>
          <span>{{ previewOption.stopCount }} 站</span>
        </div>
        <p class="version-copy">{{ previewOption.summary || previewOption.recommendReason || '如果想换一种走法，建议先回结果页点击“换一版路线”。' }}</p>
      </article>
    </section>

    <section v-else-if="activeStep === 1" class="step-panel form-panel">
      <el-form label-position="top">
        <el-form-item label="帖子标题">
          <el-input v-model="form.title" maxlength="60" show-word-limit placeholder="例如：周末武汉江滩慢游路线" />
        </el-form-item>
        <el-form-item label="分享语">
          <el-input
            v-model="form.shareNote"
            type="textarea"
            :rows="4"
            maxlength="300"
            show-word-limit
            placeholder="这条路线适合谁、什么时候去、最推荐的亮点是什么？"
          />
        </el-form-item>
        <el-form-item label="主题标签（最多 3 个）">
          <el-select
            v-model="form.themes"
            multiple
            allow-create
            filterable
            default-first-option
            collapse-tags
            collapse-tags-tooltip
            placeholder="例如：Citywalk、拍照、夜游"
            @change="limitThemes"
          >
            <el-option v-for="theme in themeSuggestions" :key="theme" :label="theme" :value="theme" />
          </el-select>
        </el-form-item>
        <el-form-item label="封面图">
          <div class="cover-picker">
            <div class="cover-picker__preview">
              <img :src="coverImage" :alt="form.title || previewOption.title" @error="applyCommunityCoverFallback">
              <span>当前封面</span>
            </div>
            <div class="cover-picker__main">
              <div class="cover-preset-grid">
                <button
                  v-for="preset in COMMUNITY_COVER_PRESETS"
                  :key="preset"
                  type="button"
                  class="cover-preset"
                  :class="{ active: form.coverImageUrl === preset }"
                  @click="form.coverImageUrl = preset"
                >
                  <img :src="preset" alt="系统封面">
                </button>
              </div>
              <div class="cover-upload-row">
                <input
                  ref="coverFileInput"
                  type="file"
                  accept="image/png,image/jpeg,image/webp,image/svg+xml"
                  class="cover-file-input"
                  @change="handleCoverUpload"
                >
                <el-button round @click="openCoverFileInput">上传自己的封面</el-button>
                <el-button round @click="form.coverImageUrl = pickCommunityCoverPreset(coverSeed)">随机换一张</el-button>
                <small>支持 PNG/JPG/WebP/SVG，最大 300KB；不改数据库表结构，直接跟随帖子保存。</small>
              </div>
            </div>
          </div>
        </el-form-item>
      </el-form>
    </section>

    <section v-else class="step-panel preview-panel">
      <div class="preview-grid">
        <article class="preview-card hero-preview">
          <img :src="coverImage" :alt="form.title || previewOption.title" class="preview-image" @error="applyCommunityCoverFallback">
          <div class="preview-overlay">
            <span class="preview-badge">路线分享帖</span>
            <h3>{{ form.title || previewOption.title }}</h3>
            <p>{{ form.shareNote || previewOption.summary || '把路线写成别人愿意点开的故事。' }}</p>
          </div>
        </article>

        <article class="preview-card info-preview">
          <p class="mini-kicker">首页卡片预览</p>
          <h4>{{ form.title || previewOption.title }}</h4>
          <p class="preview-copy">{{ form.shareNote || previewOption.summary || '把路线写成别人愿意点开的故事。' }}</p>
          <div class="chip-row">
            <span v-for="theme in form.themes" :key="theme" class="preview-chip">{{ theme }}</span>
          </div>
          <div class="preview-metrics">
            <span>{{ formatDuration(previewOption.totalDuration) }}</span>
            <span>¥{{ previewOption.totalCost ?? 0 }}</span>
            <span>{{ previewOption.stopCount }} 站</span>
          </div>
        </article>
      </div>
    </section>

    <template #footer>
      <div class="dialog-actions">
        <el-button round @click="handleClose">取消</el-button>
        <el-button v-if="activeStep > 0" round @click="activeStep -= 1">上一步</el-button>
        <el-button v-if="activeStep < 2" type="primary" round @click="goNext">下一步</el-button>
        <el-button v-else type="primary" round :loading="submitting" @click="submitPublish">确认发布</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { reqToggleItineraryPublic } from '@/api/itinerary'
import {
  COMMUNITY_COVER_PRESETS,
  applyCommunityCoverFallback,
  pickCommunityCoverPreset,
  resolveCommunityCover
} from '@/utils/communityCover'

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

const emit = defineEmits(['update:modelValue', 'published'])

const activeStep = ref(0)
const submitting = ref(false)
const coverFileInput = ref(null)
const form = reactive({
  selectedOptionKey: '',
  title: '',
  shareNote: '',
  themes: [],
  coverImageUrl: ''
})

const buildRouteSignature = nodes => (nodes || []).map(node => node?.poiId).filter(Boolean).join('-')

const buildFallbackOption = snapshot => {
  const nodes = Array.isArray(snapshot?.nodes) ? snapshot.nodes : []
  return {
    optionKey: snapshot?.selectedOptionKey || 'default',
    title: '当前默认方案',
    subtitle: '从当前保存版本直接发布',
    signature: buildRouteSignature(nodes),
    totalDuration: snapshot?.totalDuration || 0,
    totalCost: snapshot?.totalCost || 0,
    stopCount: nodes.length,
    summary: snapshot?.recommendReason || '',
    recommendReason: snapshot?.recommendReason || '',
    nodes
  }
}

const displayOptions = computed(() => {
  if (Array.isArray(props.itinerary?.options) && props.itinerary.options.length) {
    const selected = props.itinerary.options.find(option => option.optionKey === props.itinerary?.selectedOptionKey) || props.itinerary.options[0]
    return [selected].filter(Boolean).map(option => ({
      ...option,
      title: option.title || '备选路线',
      subtitle: option.subtitle || '适合当前分享的路线版本',
      stopCount: Array.isArray(option.nodes) ? option.nodes.length : Number(option.stopCount || 0),
      summary: option.summary || option.recommendReason || '',
      signature: option.signature || buildRouteSignature(option.nodes)
    }))
  }
  return props.itinerary ? [buildFallbackOption(props.itinerary)] : []
})

const previewOption = computed(() => {
  return displayOptions.value.find(item => item.optionKey === form.selectedOptionKey) || displayOptions.value[0] || buildFallbackOption(props.itinerary)
})

const coverSeed = computed(() => props.itinerary?.id || form.title || previewOption.value?.title || previewOption.value?.signature)

const themeSuggestions = computed(() => {
  const originalThemes = Array.isArray(props.itinerary?.originalReq?.themes) ? props.itinerary.originalReq.themes : []
  const optionHighlights = Array.isArray(previewOption.value?.highlights) ? previewOption.value.highlights : []
  return [...new Set([...originalThemes, ...optionHighlights].filter(Boolean))]
})

const coverImage = computed(() => resolveCommunityCover(
  form.coverImageUrl || props.itinerary?.coverImageUrl || props.itinerary?.coverImage || previewOption.value?.coverImageUrl,
  coverSeed.value
))

const initForm = () => {
  activeStep.value = 0
  const options = displayOptions.value
  const selectedOptionKey = props.itinerary?.selectedOptionKey || options[0]?.optionKey || 'default'
  form.selectedOptionKey = selectedOptionKey
  form.title = props.itinerary?.customTitle || options[0]?.title || ''
  form.shareNote = props.itinerary?.shareNote || ''
  form.themes = Array.isArray(props.itinerary?.originalReq?.themes) ? [...props.itinerary.originalReq.themes].slice(0, 3) : []
  form.coverImageUrl = props.itinerary?.coverImageUrl
    || props.itinerary?.coverImage
    || options[0]?.coverImageUrl
    || pickCommunityCoverPreset(props.itinerary?.id || selectedOptionKey || options[0]?.signature)
}

watch(() => props.modelValue, value => {
  if (value) {
    initForm()
  }
}, { immediate: true })

const limitThemes = values => {
  form.themes = (values || []).map(item => `${item}`.trim()).filter(Boolean).slice(0, 3)
}

const openCoverFileInput = () => {
  coverFileInput.value?.click()
}

const handleCoverUpload = event => {
  const file = event?.target?.files?.[0]
  if (!file) return
  const allowedTypes = ['image/png', 'image/jpeg', 'image/webp', 'image/svg+xml']
  if (!allowedTypes.includes(file.type)) {
    ElMessage.warning('请上传 PNG、JPG、WebP 或 SVG 图片')
    event.target.value = ''
    return
  }
  if (file.size > 300 * 1024) {
    ElMessage.warning('封面图片不能超过 300KB')
    event.target.value = ''
    return
  }
  const reader = new FileReader()
  reader.onload = () => {
    form.coverImageUrl = typeof reader.result === 'string' ? reader.result : ''
  }
  reader.onerror = () => {
    ElMessage.error('封面读取失败，请换一张图片重试')
  }
  reader.readAsDataURL(file)
  event.target.value = ''
}

const goNext = () => {
  if (activeStep.value === 0 && !form.selectedOptionKey) {
    ElMessage.warning('请先选择要发布的路线版本')
    return
  }
  if (activeStep.value === 1 && !form.title.trim()) {
    ElMessage.warning('请先填写帖子标题')
    return
  }
  activeStep.value += 1
}

const submitPublish = async () => {
  if (!props.itinerary?.id) return
  submitting.value = true
  try {
    const result = await reqToggleItineraryPublic(props.itinerary.id, {
      isPublic: true,
      title: form.title.trim(),
      shareNote: form.shareNote.trim(),
      selectedOptionKey: form.selectedOptionKey,
      themes: form.themes,
      coverImageUrl: form.coverImageUrl
    })
    emit('published', {
      ...result,
      originalReq: {
        ...(props.itinerary?.originalReq || {}),
        themes: form.themes
      }
    })
    emit('update:modelValue', false)
  } finally {
    submitting.value = false
  }
}

const handleClose = () => {
  emit('update:modelValue', false)
}

const formatDuration = minutes => {
  if (!minutes && minutes !== 0) return '--'
  const hour = Math.floor(minutes / 60)
  const minute = minutes % 60
  if (!hour) return `${minute} 分钟`
  if (!minute) return `${hour} 小时`
  return `${hour} 小时 ${minute} 分钟`
}
</script>

<style scoped>
:deep(.publish-dialog .el-dialog) {
  border-radius: 30px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(244, 249, 255, 0.96));
  box-shadow: 0 28px 72px rgba(81, 120, 177, 0.18);
}

:deep(.publish-dialog .el-dialog__header) {
  margin-right: 0;
  padding: 26px 28px 12px;
}

:deep(.publish-dialog .el-dialog__body) {
  padding: 0 28px 24px;
}

:deep(.publish-dialog .el-dialog__footer) {
  padding: 0 28px 28px;
}

.dialog-header h2 {
  margin: 8px 0 0;
  color: var(--text-strong);
  font-size: 30px;
  font-family: var(--font-display);
}

.dialog-header span,
.dialog-kicker {
  color: var(--text-soft);
}

.dialog-kicker {
  margin: 0;
  font-size: 12px;
  letter-spacing: 0.18em;
}

.step-bar {
  margin-bottom: 24px;
}

:deep(.step-bar .el-step__title) {
  color: var(--text-body);
}

:deep(.step-bar .is-process .el-step__icon),
:deep(.step-bar .is-finish .el-step__icon) {
  background: linear-gradient(135deg, var(--brand-500), #8ac2ff);
  border-color: transparent;
}

.step-panel {
  min-height: 360px;
}

.version-panel {
  display: grid;
  gap: 14px;
}

.version-card,
.preview-card {
  border-radius: 24px;
  border: 1px solid rgba(188, 214, 255, 0.84);
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 14px 32px rgba(81, 120, 177, 0.08);
}

.version-card {
  padding: 20px;
  cursor: pointer;
  transition: all 0.22s ease;
}

.version-card.active {
  border-color: rgba(95, 158, 255, 0.5);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(240, 247, 255, 0.94));
  box-shadow: 0 18px 36px rgba(81, 120, 177, 0.12);
}

.version-label,
.mini-kicker {
  margin: 0;
  color: var(--brand-600);
  font-size: 12px;
  letter-spacing: 0.14em;
}

.version-card h3,
.info-preview h4 {
  margin: 10px 0 0;
  color: var(--text-strong);
  font-size: 22px;
  font-family: var(--font-display);
}

.chip-row {
  display: flex;
  gap: 10px 12px;
  flex-wrap: wrap;
}

.cover-picker {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 16px;
  width: 100%;
}

.cover-picker__preview {
  position: relative;
  min-height: 122px;
  overflow: hidden;
  border-radius: 20px;
  border: 1px solid rgba(188, 214, 255, 0.84);
  background: rgba(244, 249, 255, 0.94);
}

.cover-picker__preview img,
.cover-preset img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.cover-picker__preview img {
  height: 122px;
}

.cover-picker__preview span {
  position: absolute;
  left: 10px;
  bottom: 10px;
  padding: 5px 9px;
  border-radius: 999px;
  color: #fff;
  font-size: 12px;
  background: rgba(20, 45, 86, 0.62);
}

.cover-picker__main {
  display: grid;
  gap: 12px;
}

.cover-preset-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 8px;
}

.cover-preset {
  height: 66px;
  padding: 0;
  overflow: hidden;
  border-radius: 14px;
  border: 2px solid transparent;
  background: transparent;
  cursor: pointer;
}

.cover-preset.active {
  border-color: var(--brand-500);
  box-shadow: 0 8px 18px rgba(81, 120, 177, 0.16);
}

.cover-upload-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.cover-upload-row small {
  color: var(--text-soft);
}

.cover-file-input {
  display: none;
}

.version-meta,
.preview-metrics {
  margin-top: 12px;
  color: var(--text-soft);
  font-size: 13px;
}

.version-copy,
.preview-copy {
  margin: 12px 0 0;
  color: var(--text-body);
  line-height: 1.75;
}

.preview-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(280px, 0.85fr);
  gap: 18px;
}

.preview-card {
  overflow: hidden;
}

.hero-preview {
  position: relative;
  min-height: 360px;
}

.preview-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.preview-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  padding: 24px;
  color: #f6fbff;
  background: linear-gradient(180deg, rgba(18, 42, 79, 0.08), rgba(18, 42, 79, 0.82));
}

.preview-badge,
.preview-chip {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  padding: 7px 12px;
  border-radius: 999px;
  font-size: 12px;
}

.preview-badge {
  margin-bottom: 12px;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(228, 239, 255, 0.24);
  color: #dcecff;
}

.preview-overlay h3 {
  margin: 0;
  font-size: 28px;
  font-family: var(--font-display);
}

.preview-overlay p {
  margin: 12px 0 0;
  line-height: 1.8;
  color: rgba(236, 245, 255, 0.88);
}

.info-preview {
  padding: 22px;
}

.chip-row {
  margin-top: 14px;
}

.preview-chip {
  background: rgba(244, 249, 255, 0.94);
  border: 1px solid rgba(188, 214, 255, 0.84);
  color: var(--brand-600);
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

:deep(.form-panel .el-input__wrapper),
:deep(.form-panel .el-textarea__inner),
:deep(.form-panel .el-select__wrapper) {
  border-radius: 18px;
  background: rgba(248, 252, 255, 0.96);
  box-shadow: 0 0 0 1px rgba(188, 214, 255, 0.84) inset;
}

@media (max-width: 900px) {
  .preview-grid {
    grid-template-columns: 1fr;
  }

  .cover-picker {
    grid-template-columns: 1fr;
  }

  .cover-preset-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>
