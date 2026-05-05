<template>
  <section class="segment-guide-card" :class="{ linked }">
    <div class="guide-summary-row">
      <div class="guide-summary-main">
        <p class="guide-kicker">本段通行</p>
        <strong class="guide-title">{{ title }}</strong>
        <p class="guide-summary">{{ summary }}</p>
        <p v-if="localEstimateNote" class="guide-provider-note">{{ localEstimateNote }}</p>
      </div>

      <div class="guide-pill-stack">
        <span v-if="sourceLabel" class="guide-source-pill">{{ sourceLabel }}</span>
        <span v-if="modeLabel" class="guide-mode-pill">{{ modeLabel }}</span>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { buildSegmentGuideTitle, formatSegmentGuideSummary } from '@/utils/resultUi'

const props = defineProps({
  guide: { type: Object, default: null },
  fromName: { type: String, default: '' },
  toName: { type: String, default: '' },
  linked: { type: Boolean, default: false },
  stepOrder: { type: Number, default: 1 }
})

const title = computed(() => buildSegmentGuideTitle({
  stepOrder: props.stepOrder,
  fromName: props.fromName,
  toName: props.toName
}))

const summary = computed(() => formatSegmentGuideSummary(props.guide))
const modeLabel = computed(() => {
  const raw = props.guide?.transportMode
  return typeof raw === 'string' && raw.trim() ? raw.trim() : ''
})
const isProviderBacked = computed(() => {
  const source = props.guide?.source
  return typeof source === 'string' && source.trim()
})
const sourceLabel = computed(() => isProviderBacked.value ? '高德路线' : '')
const localEstimateNote = computed(() => {
  if (sourceLabel.value) return ''
  return props.guide?.detailAvailable === false ? '???????????????????' : ''
})
</script>

<style scoped>
.segment-guide-card {
  margin-top: 16px;
  padding: 16px 18px;
  border-radius: 20px;
  border: 1px solid rgba(188, 214, 255, 0.82);
  background: linear-gradient(180deg, rgba(248, 252, 255, 0.92), rgba(242, 248, 255, 0.88));
  transition: border-color 0.24s ease, box-shadow 0.24s ease, transform 0.24s ease;
}

.segment-guide-card.linked {
  border-color: rgba(95, 158, 255, 0.92);
  box-shadow: 0 14px 30px rgba(95, 158, 255, 0.12);
  transform: translateY(-1px);
}

.guide-summary-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 14px;
}

.guide-summary-main {
  min-width: 0;
}

.guide-kicker {
  margin: 0 0 8px;
  color: #2d79c7;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.06em;
}

.guide-title {
  display: block;
  color: #1f2d3d;
  line-height: 1.5;
}

.guide-summary {
  margin: 10px 0 0;
  color: #55687d;
  line-height: 1.8;
}

.guide-step-list {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}

.guide-step-item {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  gap: 8px;
  align-items: flex-start;
  padding: 8px 10px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.66);
  border: 1px solid rgba(188, 214, 255, 0.58);
}

.guide-step-item span {
  width: 22px;
  height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: rgba(64, 158, 255, 0.14);
  color: #2d79c7;
  font-size: 12px;
  font-weight: 800;
}

.guide-step-item p,
.guide-provider-note {
  margin: 0;
  color: #55687d;
  line-height: 1.65;
}

.guide-provider-note {
  margin-top: 12px;
  color: #8a6b25;
}

.guide-pill-stack {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.guide-mode-pill,
.guide-source-pill {
  padding: 5px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.guide-mode-pill {
  background: rgba(64, 158, 255, 0.12);
  color: #2d79c7;
}

.guide-source-pill {
  background: rgba(36, 192, 166, 0.12);
  color: #178f7d;
}

.guide-source-pill.muted {
  background: rgba(144, 147, 153, 0.12);
  color: #7b8794;
}

@media (max-width: 900px) {
  .guide-summary-row {
    flex-direction: column;
  }

  .guide-pill-stack {
    align-items: flex-start;
  }
}
</style>
