<template>
  <div class="history-page">
    <div class="history-shell">
      <section class="hero-card">
        <div>
          <p class="eyebrow">历史行程与收藏</p>
          <h1>把你满意的路线沉淀下来</h1>
          <p class="hero-copy">这里会展示你生成过的路线记录，也可以把喜欢的路线标记为收藏，方便后续继续调整。</p>
        </div>
        <div class="hero-actions">
          <el-button round @click="router.push('/')">继续规划新路线</el-button>
        </div>
      </section>

      <section class="stats-grid">
        <el-card shadow="never" class="stat-card">
          <span>历史行程</span>
          <strong>{{ allCount }}</strong>
        </el-card>
        <el-card shadow="never" class="stat-card">
          <span>收藏路线</span>
          <strong>{{ favoriteCount }}</strong>
        </el-card>
        <el-card shadow="never" class="stat-card">
          <span>最近更新</span>
          <strong>{{ latestLabel }}</strong>
        </el-card>
      </section>

      <section class="filter-bar">
        <button
          class="filter-pill"
          :class="{ active: activeFilter === 'all' }"
          @click="activeFilter = 'all'"
        >
          全部行程
        </button>
        <button
          class="filter-pill"
          :class="{ active: activeFilter === 'favorite' }"
          @click="activeFilter = 'favorite'"
        >
          我的收藏
        </button>
      </section>

      <section v-loading="loading" class="history-list">
        <template v-if="displayList.length > 0">
          <el-card
            v-for="item in displayList"
            :key="item.id"
            shadow="never"
            class="history-card"
          >
            <div class="card-top">
              <div>
                <p class="card-kicker">{{ item.tripDate || '--' }} / {{ item.startTime || '--' }} - {{ item.endTime || '--' }}</p>
                <h2>{{ item.title }}</h2>
                <p class="card-route">
                  {{ item.firstPoiName || '未生成起点' }}
                  <span class="route-divider">→</span>
                  {{ item.lastPoiName || '未生成终点' }}
                </p>
              </div>

              <button
                class="favorite-btn"
                :class="{ active: item.favorited }"
                :disabled="favoritePendingIds.includes(item.id)"
                @click="toggleFavorite(item)"
              >
                <el-icon><component :is="item.favorited ? StarFilled : Star" /></el-icon>
                <span>
                  {{
                    favoritePendingIds.includes(item.id)
                      ? (item.favorited ? '取消中...' : '收藏中...')
                      : (item.favorited ? '已收藏' : '收藏')
                  }}
                </span>
              </button>
            </div>

            <div class="tag-row">
              <el-tag v-if="item.isPublic" size="small" type="danger" effect="dark">
                社区展示中
              </el-tag>
              <el-tag
                v-for="theme in item.themes || []"
                :key="theme"
                size="small"
                effect="plain"
              >
                {{ theme }}
              </el-tag>
              <el-tag v-if="item.companionType" size="small" type="info" effect="plain">
                {{ item.companionType }}
              </el-tag>
              <el-tag v-if="item.budgetLevel" size="small" type="success" effect="plain">
                {{ item.budgetLevel }}预算
              </el-tag>
              <el-tag v-if="item.rainy" size="small" type="warning" effect="plain">
                雨天场景
              </el-tag>
              <el-tag v-if="item.night" size="small" type="danger" effect="plain">
                夜游
              </el-tag>
            </div>

            <div class="meta-row">
              <span>推荐点位：{{ item.nodeCount || 0 }}</span>
              <span>总时长：{{ formatDuration(item.totalDuration) }}</span>
              <span>预算：¥{{ item.totalCost ?? '--' }}</span>
              <span>更新时间：{{ formatDateTime(item.updatedAt) }}</span>
            </div>

            <div class="actions-row">
              <el-button round @click="openItinerary(item.id)">查看路线</el-button>
              <el-button
                v-if="item.isPublic"
                round
                @click="openCommunityPost(item.id)"
              >
                查看帖子
              </el-button>
              <el-button
                v-else
                round
                @click="publishRoutePost(item.id)"
              >
                发布路线帖
              </el-button>
              <el-button type="primary" round @click="openItinerary(item.id)">继续调整</el-button>
            </div>
          </el-card>
        </template>

        <el-empty
          v-else
          :description="activeFilter === 'favorite' ? '你还没有收藏路线。' : '还没有历史行程，先去生成一条吧。'"
        />
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Star, StarFilled } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { reqFavoriteItinerary, reqListItineraries, reqUnfavoriteItinerary } from '@/api/itinerary'

const router = useRouter()
const loading = ref(false)
const activeFilter = ref('all')
const allList = ref([])
const favoriteList = ref([])
const favoritePendingIds = ref([])

const allCount = computed(() => allList.value.length)
const favoriteCount = computed(() => favoriteList.value.length)
const displayList = computed(() => activeFilter.value === 'favorite' ? favoriteList.value : allList.value)
const latestLabel = computed(() => {
  const latest = allList.value[0]?.updatedAt
  return latest ? formatDateTime(latest) : '--'
})

const loadLists = async () => {
  loading.value = true
  try {
    const [all, favorites] = await Promise.all([
      reqListItineraries(),
      reqListItineraries({ favorite: true })
    ])

    allList.value = Array.isArray(all) ? all : []
    favoriteList.value = Array.isArray(favorites) ? favorites : []
  } finally {
    loading.value = false
  }
}

const toggleFavorite = async (item) => {
  if (favoritePendingIds.value.includes(item.id)) {
    return
  }

  favoritePendingIds.value = [...favoritePendingIds.value, item.id]
  try {
    if (item.favorited) {
      await reqUnfavoriteItinerary(item.id)
      ElMessage.success('已取消收藏')
    } else {
      const { value } = await ElMessageBox.prompt(
        '给这条路线起个名字，后面回顾会更方便。',
        '收藏当前路线',
        {
          confirmButtonText: '收藏',
          cancelButtonText: '取消',
          inputValue: item.title || '',
          inputPlaceholder: '例如：周末春熙路轻松逛',
          inputValidator: (input) => {
            if (!input || !input.trim()) {
              return '请输入路线名称'
            }
            if (input.trim().length > 60) {
              return '路线名称不能超过 60 个字'
            }
            return true
          }
        }
      )
      await reqFavoriteItinerary(item.id, { title: value.trim() })
      ElMessage.success('已加入收藏')
    }
    await loadLists()
  } catch (err) {
  } finally {
    favoritePendingIds.value = favoritePendingIds.value.filter(id => id !== item.id)
  }
}

const openItinerary = (id) => {
  router.push({
    path: '/result',
    query: { id }
  })
}

const openCommunityPost = (id) => {
  router.push(`/community/${id}`)
}

const publishRoutePost = (id) => {
  router.push({
    path: '/result',
    query: {
      id,
      publish: '1'
    }
  })
}

const formatDuration = (minutes) => {
  if (!minutes && minutes !== 0) return '--'
  const hour = Math.floor(minutes / 60)
  const minute = minutes % 60
  if (hour === 0) return `${minute}分钟`
  if (minute === 0) return `${hour}小时`
  return `${hour}小时 ${minute}分钟`
}

const formatDateTime = (value) => {
  if (!value) return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '--'
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${month}-${day} ${hour}:${minute}`
}

onMounted(() => {
  loadLists()
})
</script>

<style scoped>
.history-page {
  min-height: calc(100vh - 64px);
  padding: 32px 20px 48px;
  background:
    radial-gradient(circle at top left, rgba(64, 158, 255, 0.12), transparent 28%),
    linear-gradient(180deg, #f6f9fe 0%, #f7f8fa 100%);
}

.history-shell {
  max-width: 1080px;
  margin: 0 auto;
}

.hero-card,
.stat-card,
.history-card {
  border-radius: 24px;
  border: 1px solid rgba(223, 232, 244, 0.95);
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 18px 40px rgba(31, 45, 61, 0.06);
  transition:
    transform 0.22s ease,
    box-shadow 0.22s ease,
    border-color 0.22s ease;
}

.hero-card:hover,
.stat-card:hover,
.history-card:hover {
  transform: translateY(-6px) scale(1.01);
  border-color: rgba(108, 176, 255, 0.58);
  box-shadow: 0 24px 48px rgba(31, 45, 61, 0.1);
}

.hero-card {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  padding: 28px 30px;
  margin-bottom: 24px;
}

.eyebrow {
  margin: 0 0 8px;
  color: #2d79c7;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.hero-card h1 {
  margin: 0 0 10px;
  color: #1f2d3d;
  font-size: 34px;
}

.hero-copy {
  margin: 0;
  max-width: 640px;
  color: #607185;
  line-height: 1.8;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
  margin-bottom: 18px;
}

.stat-card {
  padding: 22px;
}

.stat-card span {
  display: block;
  color: #7a8da3;
  margin-bottom: 10px;
}

.stat-card strong {
  font-size: 28px;
  color: #1f2d3d;
}

.filter-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 18px;
}

.filter-pill {
  min-height: 42px;
  padding: 0 18px;
  border-radius: 999px;
  border: 1px solid rgba(214, 226, 242, 0.95);
  background: rgba(255, 255, 255, 0.9);
  color: #586a7f;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.22s ease;
}

.filter-pill.active {
  border-color: rgba(64, 158, 255, 0.48);
  background: linear-gradient(135deg, rgba(64, 158, 255, 0.12), rgba(102, 177, 255, 0.18));
  color: #2d79c7;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.history-card {
  padding: 24px;
}

.card-top {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: flex-start;
}

.card-kicker {
  margin: 0 0 8px;
  color: #7a8da3;
  font-size: 13px;
}

.card-top h2 {
  margin: 0 0 10px;
  color: #1f2d3d;
  font-size: 26px;
}

.card-route {
  margin: 0;
  color: #4f6278;
  line-height: 1.7;
}

.route-divider {
  color: #409eff;
  margin: 0 6px;
  font-weight: 700;
}

.favorite-btn {
  min-height: 42px;
  padding: 0 16px;
  border-radius: 999px;
  border: 1px solid rgba(214, 226, 242, 0.95);
  background: #fff;
  color: #6c7d90;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  transition: all 0.22s ease;
}

.favorite-btn:disabled {
  cursor: not-allowed;
  opacity: 0.72;
}

.favorite-btn.active {
  color: #d48806;
  border-color: rgba(230, 162, 60, 0.3);
  background: linear-gradient(135deg, rgba(230, 162, 60, 0.12), rgba(255, 228, 180, 0.2));
}

.tag-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin: 16px 0 14px;
}

.meta-row {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  color: #738397;
}

.actions-row {
  margin-top: 18px;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  flex-wrap: wrap;
}

@media (max-width: 900px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }

  .hero-card,
  .card-top {
    flex-direction: column;
  }

  .actions-row {
    justify-content: flex-start;
  }
}
</style>
