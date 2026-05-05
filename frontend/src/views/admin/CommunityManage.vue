<template>
  <div class="community-manage">
    <el-card shadow="never" class="manage-card hero-card">
      <div class="hero-content">
        <div>
          <p class="hero-kicker">COMMUNITY GOVERNANCE</p>
          <h2>社区帖子治理中心</h2>
          <p class="hero-copy">
            统一处理首页轮播精选、删帖与内容巡检，帮助社区首页保持精品感与阅读秩序。
          </p>
        </div>
        <div class="hero-stats">
          <div class="stat-pill">
            <span>当前页帖子</span>
            <strong>{{ tableData.length }}</strong>
          </div>
          <div class="stat-pill danger">
            <span>轮播精选</span>
            <strong>{{ pinnedCount }}</strong>
          </div>
          <div class="stat-pill muted">
            <span>已删除</span>
            <strong>{{ deletedCount }}</strong>
          </div>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="manage-card">
      <template #header>
        <div class="card-header">
          <div>
            <div class="card-title">帖子列表</div>
            <p class="card-subtitle">支持按关键词、轮播精选状态和删除状态筛选，并在这里完成轻治理操作。</p>
          </div>
          <div class="header-actions">
            <el-button :icon="RefreshRight" @click="fetchData">刷新</el-button>
            <el-button @click="resetFilters">重置筛选</el-button>
          </div>
        </div>
      </template>

      <div class="filter-grid">
        <el-input
          v-model="searchQuery"
          placeholder="搜索标题、作者或分享语"
          clearable
          @clear="handleSearch"
          @keyup.enter="handleSearch"
        >
          <template #append>
            <el-button :icon="Search" @click="handleSearch" />
          </template>
        </el-input>

        <el-select v-model="pinnedFilter" placeholder="轮播状态" clearable @change="handleSearch">
          <el-option label="全部轮播状态" value="" />
          <el-option label="仅看轮播精选" value="1" />
          <el-option label="仅看未入选轮播" value="0" />
        </el-select>

        <el-select v-model="deletedFilter" placeholder="删除状态" clearable @change="handleSearch">
          <el-option label="全部删除状态" value="" />
          <el-option label="仅看正常帖子" value="0" />
          <el-option label="仅看已删除帖子" value="1" />
        </el-select>
      </div>

      <el-table
        :data="tableData"
        style="width: 100%"
        v-loading="loading"
        border
        empty-text="暂无符合条件的社区帖子"
      >
        <el-table-column label="帖子" min-width="320">
          <template #default="{ row }">
            <div class="post-cell">
              <img :src="resolveCommunityCover(row.coverImageUrl, row.id || row.title)" :alt="row.title" class="cover-thumb" @error="applyCommunityCoverFallback">
              <div class="post-main">
                <div class="post-title-row">
                  <strong>{{ row.title || '未命名路线帖' }}</strong>
                  <el-tag v-if="row.globalPinned" type="danger" size="small" effect="dark">首页轮播</el-tag>
                  <el-tag v-if="row.deleted" type="info" size="small">已删除</el-tag>
                </div>
                <p class="post-note">{{ row.shareNote || '暂无分享语' }}</p>
                <div class="chip-row">
                  <el-tag
                    v-for="theme in (row.themes || []).slice(0, 3)"
                    :key="`${row.id}-${theme}`"
                    size="small"
                    effect="plain"
                  >
                    {{ theme }}
                  </el-tag>
                </div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="作者" width="140">
          <template #default="{ row }">
            <div class="author-cell">
              <strong>{{ row.authorLabel || '匿名旅人' }}</strong>
              <small>ID {{ row.userId }}</small>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="数据概览" min-width="240">
          <template #default="{ row }">
            <div class="metric-grid">
              <span>时长：{{ formatDuration(row.totalDuration) }}</span>
              <span>预算：{{ formatCurrency(row.totalCost) }}</span>
              <span>站点：{{ row.nodeCount || 0 }}</span>
              <span>点赞：{{ row.likeCount || 0 }}</span>
              <span>评论：{{ row.commentCount || 0 }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="时间" width="180">
          <template #default="{ row }">
            <div class="time-cell">
              <span>更新于 {{ formatDateTime(row.updatedAt) }}</span>
              <small v-if="row.globalPinnedAt">入选轮播于 {{ formatDateTime(row.globalPinnedAt) }}</small>
              <small v-else>未入选轮播</small>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="240" align="center" fixed="right">
          <template #default="{ row }">
            <div class="action-group">
              <el-button
                link
                type="primary"
                :disabled="row.deleted"
                @click="openPost(row)"
              >
                查看帖子
              </el-button>
              <el-button
                link
                :type="row.globalPinned ? 'warning' : 'danger'"
                :disabled="row.deleted"
                @click="togglePin(row)"
              >
                {{ row.globalPinned ? '移出轮播' : '加入轮播' }}
              </el-button>
              <el-button
                link
                type="danger"
                :disabled="row.deleted"
                @click="deletePost(row)"
              >
                删除帖子
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { RefreshRight, Search } from '@element-plus/icons-vue'
import {
  reqAdminCommunityDelete,
  reqAdminCommunityPin,
  reqAdminCommunityPosts
} from '@/api/adminCommunity'
import { applyCommunityCoverFallback, resolveCommunityCover } from '@/utils/communityCover'

const router = useRouter()
const loading = ref(false)
const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const searchQuery = ref('')
const pinnedFilter = ref('')
const deletedFilter = ref('')
const pinnedCount = computed(() => tableData.value.filter(item => item.globalPinned).length)
const deletedCount = computed(() => tableData.value.filter(item => item.deleted).length)

const normalizeFlag = value => {
  if (value === '' || value === null || value === undefined) {
    return undefined
  }
  return Number(value)
}

const handleSearch = () => {
  currentPage.value = 1
  fetchData()
}

const resetFilters = () => {
  searchQuery.value = ''
  pinnedFilter.value = ''
  deletedFilter.value = ''
  currentPage.value = 1
  fetchData()
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await reqAdminCommunityPosts({
      page: currentPage.value,
      size: pageSize.value,
      keyword: searchQuery.value || undefined,
      pinned: normalizeFlag(pinnedFilter.value),
      deleted: normalizeFlag(deletedFilter.value)
    })
    tableData.value = Array.isArray(res.records) ? res.records : []
    total.value = Number(res.total || 0)
  } finally {
    loading.value = false
  }
}

const openPost = row => {
  if (row.deleted) {
    ElMessage.warning('已删除的帖子无法打开')
    return
  }
  router.push(`/community/${row.id}`)
}

const togglePin = async row => {
  const nextPinned = !row.globalPinned
  const actionLabel = nextPinned ? '加入首页轮播精选' : '移出首页轮播精选'

  try {
    await ElMessageBox.confirm(
      `确定要将 “${row.title || '未命名路线帖'}” ${actionLabel}吗？`,
      '首页轮播管理',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: nextPinned ? 'warning' : 'info'
      }
    )

    await reqAdminCommunityPin(row.id, nextPinned)
    ElMessage.success(nextPinned ? '已加入首页轮播精选' : '已移出首页轮播精选')
    await fetchData()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error?.response?.data?.message || '轮播精选状态更新失败')
    }
  }
}

const deletePost = async row => {
  try {
    await ElMessageBox.confirm(
      `删除后，帖子 “${row.title || '未命名路线帖'}” 会从社区流中移除，确定继续吗？`,
      '删除社区帖子',
      {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await reqAdminCommunityDelete(row.id)
    ElMessage.success('帖子已删除')
    await fetchData()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error?.response?.data?.message || '删除帖子失败')
    }
  }
}

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

const formatDateTime = value => {
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
  fetchData()
})
</script>

<style scoped>
.community-manage {
  width: 100%;
}

.manage-card {
  border-radius: 16px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.06);
}

.hero-card {
  margin-bottom: 20px;
  background:
    radial-gradient(circle at top right, rgba(251, 191, 36, 0.18), transparent 28%),
    linear-gradient(135deg, #0f172a, #1e293b 48%, #172554 100%);
  border: none;
}

.hero-content {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: center;
  color: #f8fafc;
}

.hero-kicker {
  margin: 0 0 8px;
  font-size: 12px;
  letter-spacing: 0.18em;
  color: rgba(255, 255, 255, 0.7);
}

.hero-content h2 {
  margin: 0;
  font-size: 30px;
}

.hero-copy {
  margin: 10px 0 0;
  max-width: 720px;
  color: rgba(226, 232, 240, 0.9);
  line-height: 1.8;
}

.hero-stats {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.stat-pill {
  min-width: 132px;
  padding: 16px 18px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(8px);
}

.stat-pill span {
  display: block;
  font-size: 12px;
  color: rgba(226, 232, 240, 0.78);
}

.stat-pill strong {
  display: block;
  margin-top: 10px;
  font-size: 28px;
  color: #fff;
}

.stat-pill.danger {
  background: rgba(239, 68, 68, 0.18);
}

.stat-pill.muted {
  background: rgba(148, 163, 184, 0.18);
}

.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.card-title {
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
}

.card-subtitle {
  margin: 6px 0 0;
  color: #64748b;
  line-height: 1.7;
}

.header-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.filter-grid {
  display: grid;
  grid-template-columns: minmax(280px, 1.4fr) minmax(180px, 220px) minmax(180px, 220px);
  gap: 14px;
  margin-bottom: 20px;
}

.post-cell {
  display: flex;
  gap: 14px;
  align-items: flex-start;
}

.cover-thumb {
  width: 96px;
  height: 72px;
  border-radius: 14px;
  object-fit: cover;
  background: #e2e8f0;
  flex-shrink: 0;
}

.post-main {
  min-width: 0;
}

.post-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.post-title-row strong {
  font-size: 16px;
  color: #0f172a;
}

.post-note {
  margin: 0;
  color: #475569;
  line-height: 1.7;
}

.chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.author-cell,
.time-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.author-cell strong,
.time-cell span {
  color: #0f172a;
}

.author-cell small,
.time-cell small {
  color: #64748b;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 16px;
  color: #334155;
}

.action-group {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 1120px) {
  .filter-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .hero-content {
    flex-direction: column;
    align-items: flex-start;
  }

  .hero-stats {
    justify-content: flex-start;
  }
}

@media (max-width: 768px) {
  .post-cell {
    flex-direction: column;
  }

  .cover-thumb {
    width: 100%;
    height: 180px;
  }

  .metric-grid {
    grid-template-columns: 1fr;
  }
}
</style>
