<template>
  <article class="feed-card" @click="$emit('open', item.id)">
    <div class="cover-wrap">
      <img :src="resolvedCover" :alt="item.title" class="cover-image" @error="applyCommunityCoverFallback">
      <span v-if="item.globalPinned" class="cover-pin">置顶</span>
    </div>

    <div class="card-body">
      <div class="card-head">
        <div>
          <p class="card-date">{{ item.tripDate || '随时出发' }}</p>
          <h3>{{ item.title || '未命名路线帖' }}</h3>
        </div>
        <div class="interaction-pills">
          <span class="interaction-pill">❤ {{ item.likeCount || 0 }}</span>
          <span class="interaction-pill">评论 {{ item.commentCount || 0 }}</span>
        </div>
      </div>

      <p class="card-note">{{ item.shareNote || item.routeSummary || '留一点空白，让路线自己说话。' }}</p>

      <div class="theme-row">
        <span v-for="theme in (item.themes || []).slice(0, 3)" :key="theme" class="theme-chip">{{ theme }}</span>
      </div>

      <div class="metric-row">
        <span>{{ formatDuration(item.totalDuration) }}</span>
        <span>{{ formatCurrency(item.totalCost) }}</span>
        <span>{{ item.nodeCount || 0 }} 站</span>
      </div>
    </div>

    <footer class="card-footer">
      <div>
        <strong>{{ item.authorLabel || '匿名旅人' }}</strong>
        <small>{{ item.routeSummary || '城市慢游路线' }}</small>
      </div>
      <span class="footer-link">查看帖子 →</span>
    </footer>
  </article>
</template>

<script setup>
import { computed } from 'vue'
import { applyCommunityCoverFallback, resolveCommunityCover } from '@/utils/communityCover'

const props = defineProps({
  item: {
    type: Object,
    required: true
  }
})

defineEmits(['open'])

const coverSeed = computed(() => props.item?.id || props.item?.title || props.item?.routeSummary)
const resolvedCover = computed(() => resolveCommunityCover(props.item?.coverImageUrl, coverSeed.value))

const formatDuration = minutes => {
  if (!minutes && minutes !== 0) return '--'
  const hour = Math.floor(minutes / 60)
  const minute = minutes % 60
  if (!hour) return `${minute} 分钟`
  if (!minute) return `${hour} 小时`
  return `${hour} 小时 ${minute} 分钟`
}

const formatCurrency = value => {
  if (value === null || value === undefined || value === '') return '--'
  return `¥${value}`
}
</script>

<style scoped>
.feed-card {
  display: flex;
  flex-direction: column;
  min-height: 100%;
  border-radius: var(--radius-panel);
  overflow: hidden;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(188, 214, 255, 0.84);
  box-shadow: var(--shadow-soft);
  cursor: pointer;
  transition: transform 0.24s ease, box-shadow 0.24s ease, border-color 0.24s ease;
}

.feed-card:hover {
  transform: translateY(-6px);
  border-color: rgba(95, 158, 255, 0.52);
  box-shadow: var(--shadow-strong);
}

.cover-wrap {
  position: relative;
  aspect-ratio: 1.22;
  overflow: hidden;
}

.cover-wrap::after {
  content: '';
  position: absolute;
  inset: auto 0 0 0;
  height: 48%;
  background: linear-gradient(180deg, transparent, rgba(27, 63, 118, 0.22));
}

.cover-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s ease;
}

.feed-card:hover .cover-image {
  transform: scale(1.03);
}

.cover-pin {
  position: absolute;
  top: 14px;
  left: 14px;
  z-index: 1;
  padding: 7px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(188, 214, 255, 0.84);
  color: var(--brand-600);
  font-size: 12px;
  font-weight: 700;
  backdrop-filter: blur(10px);
}

.card-body {
  padding: 22px 22px 0;
}

.card-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.card-date {
  width: fit-content;
  margin: 0;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(95, 158, 255, 0.1);
  color: var(--brand-600);
  font-size: 12px;
  letter-spacing: 0.08em;
}

.card-body h3 {
  margin: 12px 0 0;
  color: var(--text-strong);
  font-size: 22px;
  line-height: 1.24;
  font-family: var(--font-display);
}

.card-note {
  margin: 16px 0 0;
  min-height: 60px;
  color: var(--text-body);
  line-height: 1.8;
}

.interaction-pills,
.theme-row,
.metric-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 10px;
}

.interaction-pills {
  justify-content: flex-end;
}

.interaction-pill,
.theme-chip {
  padding: 6px 10px;
  border-radius: 999px;
  border: 1px solid rgba(188, 214, 255, 0.78);
  background: rgba(244, 249, 255, 0.94);
  color: var(--text-body);
  font-size: 12px;
  font-weight: 600;
}

.theme-row {
  margin-top: 18px;
}

.theme-chip {
  color: var(--brand-600);
}

.metric-row {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid rgba(214, 228, 248, 0.88);
  color: var(--text-soft);
  font-size: 13px;
}

.card-footer {
  margin-top: auto;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  padding: 18px 22px 22px;
  border-top: 1px solid rgba(214, 228, 248, 0.88);
  background: rgba(250, 252, 255, 0.72);
}

.card-footer strong {
  display: block;
  color: var(--text-strong);
}

.card-footer small {
  display: block;
  margin-top: 6px;
  color: var(--text-soft);
}

.footer-link {
  color: var(--brand-600);
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

@media (max-width: 760px) {
  .card-head,
  .card-footer {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
