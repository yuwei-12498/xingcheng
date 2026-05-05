<template>
  <section v-if="featuredPosts.length" class="examples-section" id="examples">
    <div class="examples-container">
      <div class="section-header">
        <div>
          <p class="section-kicker">COMMUNITY PICKS</p>
          <h2 class="section-title">社区优秀路线精选</h2>
          <p class="section-subtitle">
            这里展示管理员精选的 3-5 条优质路线贴，先逛灵感，再决定你的下一段城市行程。
          </p>
        </div>
        <div class="section-badge">自动轮播 · 支持手动切换</div>
      </div>

      <el-carousel
        :interval="5000"
        trigger="click"
        arrow="always"
        autoplay
        indicator-position="outside"
        height="420px"
        class="featured-carousel"
      >
        <el-carousel-item v-for="item in featuredPosts" :key="item.id">
          <article class="featured-card" @click="openCommunityDetail(item.id)">
            <img
              :src="item.coverImageUrl || fallbackCover"
              :alt="item.title || '社区精选路线'"
              class="featured-cover"
            >

            <div class="featured-overlay">
              <div class="featured-top-row">
                <span class="featured-badge">{{ item.featuredLabel || '管理员精选' }}</span>
                <span class="featured-author">{{ item.authorLabel || '匿名旅行者' }}</span>
              </div>

              <div class="featured-main">
                <h3>{{ item.title || '未命名路线帖' }}</h3>
                <p>{{ item.shareNote || item.routeSummary || '点击查看这条路线帖的完整安排。' }}</p>
              </div>

              <div class="featured-tags">
                <el-tag
                  v-for="theme in (item.themes || []).slice(0, 4)"
                  :key="`${item.id}-${theme}`"
                  size="small"
                  effect="dark"
                >
                  {{ theme }}
                </el-tag>
              </div>

              <div class="featured-meta">
                <span>{{ formatDuration(item.totalDuration) }}</span>
                <span>预算 {{ formatCurrency(item.totalCost) }}</span>
                <span>评论 {{ item.commentCount || 0 }}</span>
                <span>点赞 {{ item.likeCount || 0 }}</span>
              </div>
            </div>
          </article>
        </el-carousel-item>
      </el-carousel>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { reqListCommunityItineraries } from '@/api/itinerary'
import { buildFeaturedPosts } from '@/utils/communityFeatured'

const router = useRouter()
const pinnedRecords = ref([])
const feedRecords = ref([])
const fallbackCover = '/community-covers/v2/cover-citywalk.svg'

const featuredPosts = computed(() => {
  return buildFeaturedPosts({
    pinnedRecords: pinnedRecords.value,
    records: feedRecords.value
  })
    .slice(0, 5)
})

const loadFeaturedPosts = async () => {
  try {
    const res = await reqListCommunityItineraries({
      page: 1,
      size: 20,
      sort: 'latest'
    })
    pinnedRecords.value = Array.isArray(res?.pinnedRecords) ? res.pinnedRecords : []
    feedRecords.value = Array.isArray(res?.records) ? res.records : []
  } catch (error) {
    pinnedRecords.value = []
    feedRecords.value = []
  }
}

const openCommunityDetail = id => {
  if (!id) return
  router.push(`/community/${id}`)
}

const formatDuration = minutes => {
  const value = Number(minutes)
  if (!Number.isFinite(value) || value < 0) return '--'
  const hour = Math.floor(value / 60)
  const minute = value % 60
  if (!hour) return `${minute} 分钟`
  if (!minute) return `${hour} 小时`
  return `${hour} 小时 ${minute} 分钟`
}

const formatCurrency = value => {
  if (value === null || value === undefined || value === '') return '--'
  return `¥${value}`
}

onMounted(() => {
  loadFeaturedPosts()
})
</script>

<style scoped>
.examples-section {
  padding: 96px 24px;
  background:
    radial-gradient(circle at top left, rgba(124, 182, 255, 0.12), transparent 24%),
    linear-gradient(180deg, #f7f9fc 0%, #f3f7fd 100%);
}

.examples-container {
  max-width: 1120px;
  margin: 0 auto;
}

.section-header {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: end;
  margin-bottom: 26px;
}

.section-kicker {
  margin: 0 0 8px;
  color: var(--brand-600);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

.section-title {
  margin: 0;
  color: #1f2d3d;
  font-size: 34px;
  font-weight: 800;
}

.section-subtitle {
  margin: 12px 0 0;
  max-width: 640px;
  color: #607185;
  font-size: 15px;
  line-height: 1.8;
}

.section-badge {
  min-height: 40px;
  padding: 0 16px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  border: 1px solid rgba(188, 214, 255, 0.82);
  background: rgba(255, 255, 255, 0.88);
  color: #5e738c;
  font-size: 13px;
  white-space: nowrap;
}

.featured-carousel {
  border-radius: 28px;
  overflow: hidden;
}

.featured-card {
  position: relative;
  height: 420px;
  border-radius: 28px;
  overflow: hidden;
  cursor: pointer;
  background: #dce9fa;
  border: 1px solid rgba(188, 214, 255, 0.84);
  box-shadow: 0 22px 54px rgba(31, 45, 61, 0.12);
}

.featured-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.35s ease;
}

.featured-card:hover .featured-cover {
  transform: scale(1.04);
}

.featured-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 28px;
  background:
    linear-gradient(180deg, rgba(13, 24, 40, 0.16) 0%, rgba(13, 24, 40, 0.72) 100%);
  color: #fff;
}

.featured-top-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.featured-badge,
.featured-author {
  min-height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  background: rgba(255, 255, 255, 0.16);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.22);
  font-size: 12px;
  font-weight: 700;
}

.featured-main {
  margin-top: auto;
}

.featured-main h3 {
  margin: 0;
  font-size: 30px;
  line-height: 1.16;
}

.featured-main p {
  margin: 14px 0 0;
  max-width: 720px;
  line-height: 1.8;
  color: rgba(255, 255, 255, 0.92);
}

.featured-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.featured-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 20px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.86);
}

:deep(.featured-carousel .el-carousel__button) {
  width: 28px;
  border-radius: 999px;
}

:deep(.featured-carousel .el-carousel__arrow) {
  width: 44px;
  height: 44px;
  background: rgba(15, 28, 46, 0.5);
}

@media (max-width: 900px) {
  .section-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .featured-card,
  :deep(.featured-carousel .el-carousel__container) {
    height: 460px !important;
  }

  .featured-main h3 {
    font-size: 24px;
  }
}
</style>
