<template>
  <div class="community-page">
    <div class="community-shell">
      <CommunityHero
        :total="pinnedRecords.length + total"
        :pinned-count="pinnedRecords.length"
        :theme-count="availableThemes.length"
        @publish="handlePublish"
        @refresh="loadCommunityList"
      />

      <CommunityFilterBar
        :model-sort="sort"
        :model-keyword="keyword"
        :model-theme="theme"
        :themes="availableThemes"
        @update:sort="sort = $event"
        @update:keyword="keyword = $event"
        @update:theme="theme = $event"
        @search="applySearch"
      />

      <section v-if="loadError" class="state-card">
        <el-result icon="error" title="社区加载失败" sub-title="这次没能拿到最新路线帖，可以稍后再试。">
          <template #extra>
            <el-button type="primary" round @click="loadCommunityList">重新加载</el-button>
          </template>
        </el-result>
      </section>

      <template v-else>
        <CommunityPinnedSection :items="pinnedRecords" @open="openCommunityDetail" />

        <section v-loading="loading" class="feed-section">
          <div class="section-head">
            <div>
              <p class="section-kicker">DISCOVER</p>
              <h2>路线动态流</h2>
              <p class="section-copy">先看精选，再顺着最新路线往下逛；筛选后，你会更快找到真正值得借鉴的城市走法。</p>
            </div>
            <span>双列阅读流 · {{ total }} 条结果</span>
          </div>

          <div v-if="records.length" class="feed-grid">
            <CommunityFeedCard
              v-for="item in records"
              :key="item.id"
              :item="item"
              @open="openCommunityDetail"
            />
          </div>

          <div v-else-if="!loading && !hasCommunityContent" class="empty-community-card state-card">
            <el-empty description="当前社区里还没有公开路线帖" />
            <div class="empty-community-copy">
              <h3>你的路线还停留在私有草稿区</h3>
              <p>
                现在社区接口返回的是 0 条公开内容。去历史记录里挑一条路线发布，或者先生成一条新路线，再把满意的版本发到社区。
              </p>
            </div>
            <div class="empty-community-actions">
              <el-button type="primary" round @click="handleOpenHistory">去历史记录里发布</el-button>
              <el-button round @click="router.push('/')">先生成一条新路线</el-button>
            </div>
          </div>

          <el-empty
            v-else-if="!loading"
            description="暂时没有符合当前筛选条件的路线帖，换个标签或关键词看看。"
            class="state-card"
          />
        </section>
      </template>

      <div v-if="!loadError && total > size" class="pager-wrap">
        <el-pagination
          background
          layout="prev, pager, next"
          :current-page="page"
          :page-size="size"
          :total="total"
          @current-change="handlePageChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CommunityFeedCard from '@/components/community/CommunityFeedCard.vue'
import CommunityFilterBar from '@/components/community/CommunityFilterBar.vue'
import CommunityHero from '@/components/community/CommunityHero.vue'
import CommunityPinnedSection from '@/components/community/CommunityPinnedSection.vue'
import { reqListCommunityItineraries } from '@/api/itinerary'
import { initAuthState, useAuthState } from '@/store/auth'

const router = useRouter()
const authState = useAuthState()
const loading = ref(false)
const loadError = ref(false)
const page = ref(1)
const size = ref(12)
const total = ref(0)
const sort = ref('latest')
const keyword = ref('')
const theme = ref('')
const records = ref([])
const pinnedRecords = ref([])
const availableThemes = ref([])
const hasCommunityContent = computed(() => records.value.length > 0 || pinnedRecords.value.length > 0)

const loadCommunityList = async () => {
  loading.value = true
  loadError.value = false
  try {
    const res = await reqListCommunityItineraries({
      page: page.value,
      size: size.value,
      sort: sort.value,
      keyword: keyword.value || undefined,
      theme: theme.value || undefined
    })
    records.value = Array.isArray(res?.records) ? res.records : []
    pinnedRecords.value = Array.isArray(res?.pinnedRecords) ? res.pinnedRecords : []
    availableThemes.value = Array.isArray(res?.availableThemes) ? res.availableThemes : []
    total.value = Number(res?.total || 0)
    page.value = Number(res?.page || page.value)
    size.value = Number(res?.size || size.value)
  } catch (error) {
    records.value = []
    pinnedRecords.value = []
    availableThemes.value = []
    total.value = 0
    loadError.value = true
  } finally {
    loading.value = false
  }
}

const applySearch = () => {
  page.value = 1
  loadCommunityList()
}

const handlePageChange = nextPage => {
  page.value = nextPage
  loadCommunityList()
}

const openCommunityDetail = id => {
  router.push(`/community/${id}`)
}

const openHistoryHub = async () => {
  await initAuthState()
  if (!authState.user) {
    router.push({
      path: '/auth',
      query: {
        redirect: '/history'
      }
    })
    return
  }
  router.push('/history')
}

const handlePublish = async () => {
  await openHistoryHub()
}

const handleOpenHistory = async () => {
  await openHistoryHub()
}

onMounted(() => {
  loadCommunityList()
})
</script>

<style scoped>
.community-page {
  min-height: calc(100vh - 64px);
  padding: 34px 20px 58px;
  background:
    radial-gradient(circle at top left, rgba(124, 182, 255, 0.16), transparent 22%),
    radial-gradient(circle at top right, rgba(211, 230, 255, 0.22), transparent 28%),
    linear-gradient(180deg, #f7fbff 0%, #f2f7ff 100%);
}

.community-shell {
  max-width: 1240px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 36px;
}

.feed-section {
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

.section-copy {
  margin: 12px 0 0;
  max-width: 600px;
  color: var(--text-body);
  line-height: 1.8;
}

.section-head span {
  padding: 8px 12px;
  border-radius: 999px;
  border: 1px solid rgba(188, 214, 255, 0.78);
  background: rgba(246, 250, 255, 0.94);
  color: var(--text-soft);
  font-size: 13px;
}

.feed-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
}

.state-card {
  margin-top: 24px;
  border-radius: var(--radius-panel);
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(188, 214, 255, 0.84);
  box-shadow: var(--shadow-soft);
}

.empty-community-card {
  display: grid;
  grid-template-columns: minmax(220px, 0.8fr) minmax(0, 1fr);
  gap: 24px;
  align-items: center;
  padding: 28px 30px;
}

.empty-community-copy h3 {
  margin: 0;
  color: var(--text-strong);
  font-size: 28px;
  font-family: var(--font-display);
}

.empty-community-copy p {
  margin: 14px 0 0;
  color: var(--text-body);
  line-height: 1.85;
}

.empty-community-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.pager-wrap {
  display: flex;
  justify-content: center;
  margin-top: 0;
}

@media (max-width: 900px) {
  .feed-grid {
    grid-template-columns: 1fr;
  }

  .empty-community-card {
    grid-template-columns: 1fr;
  }

  .section-head {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
