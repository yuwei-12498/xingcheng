<template>
  <section v-if="items.length" class="pinned-section">
    <div class="section-head">
      <div>
        <p class="section-kicker">PINNED PICKS</p>
        <h2>本周精选路线</h2>
      </div>
      <span>管理员精选，不与下方动态流重复</span>
    </div>

    <div class="pinned-grid">
      <article
        v-for="item in items"
        :key="item.id"
        class="pinned-card"
        @click="$emit('open', item.id)"
      >
        <img :src="getCoverImage(item)" :alt="item.title" class="cover-image" @error="applyCommunityCoverFallback">
        <div class="card-overlay">
          <span class="card-badge">精选推荐</span>
          <h3>{{ item.title }}</h3>
          <p>{{ item.shareNote || item.routeSummary || '这条路线适合被慢慢读完。' }}</p>
          <div class="card-meta">
            <span>{{ item.authorLabel }}</span>
            <span>{{ formatDuration(item.totalDuration) }}</span>
            <span>评论 {{ item.commentCount || 0 }}</span>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup>
import { applyCommunityCoverFallback, resolveCommunityCover } from '@/utils/communityCover'

defineProps({
  items: {
    type: Array,
    default: () => []
  }
})

defineEmits(['open'])

const getCoverImage = item => resolveCommunityCover(item?.coverImageUrl, item?.id || item?.title || item?.routeSummary)

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
.pinned-section {
  margin-top: 0;
}

.section-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: end;
  margin-bottom: 20px;
}

.section-kicker {
  margin: 0 0 8px;
  color: var(--brand-600);
  letter-spacing: 0.16em;
  font-size: 12px;
}

.section-head h2 {
  margin: 0;
  color: var(--text-strong);
  font-size: 30px;
  font-family: var(--font-display);
}

.section-head span {
  padding: 8px 12px;
  border-radius: 999px;
  border: 1px solid rgba(188, 214, 255, 0.78);
  background: rgba(246, 250, 255, 0.94);
  color: var(--text-soft);
  font-size: 13px;
}

.pinned-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.pinned-card {
  position: relative;
  min-height: 344px;
  border-radius: var(--radius-panel);
  overflow: hidden;
  cursor: pointer;
  border: 1px solid rgba(188, 214, 255, 0.84);
  background: rgba(255, 255, 255, 0.88);
  box-shadow: var(--shadow-soft);
}

.pinned-card::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(27, 61, 114, 0.02), rgba(36, 82, 149, 0.14));
}

.cover-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s ease;
}

.pinned-card:hover .cover-image {
  transform: scale(1.04);
}

.card-overlay {
  position: absolute;
  left: 16px;
  right: 16px;
  bottom: 16px;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  padding: 20px;
  border-radius: 24px;
  border: 1px solid rgba(214, 229, 251, 0.9);
  background: rgba(255, 255, 255, 0.74);
  backdrop-filter: blur(14px);
  color: var(--text-strong);
  z-index: 1;
}

.card-badge {
  width: fit-content;
  margin-bottom: 12px;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(246, 250, 255, 0.94);
  border: 1px solid rgba(188, 214, 255, 0.84);
  color: var(--brand-600);
  font-size: 12px;
  font-weight: 700;
}

.card-overlay h3 {
  margin: 0;
  font-size: 24px;
  font-family: var(--font-display);
}

.card-overlay p {
  margin: 12px 0 0;
  line-height: 1.75;
  color: var(--text-body);
}

.card-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 14px;
  font-size: 12px;
  color: var(--text-soft);
}

@media (max-width: 1100px) {
  .pinned-grid {
    grid-template-columns: 1fr;
  }
}
</style>
