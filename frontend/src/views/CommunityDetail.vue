<template>
  <div class="detail-page">
    <div v-if="detail" class="detail-shell">
      <section class="detail-hero">
        <img :src="detailCoverImage" :alt="detail.title" class="hero-cover" @error="applyCommunityCoverFallback">
        <div class="hero-overlay">
          <div class="hero-content">
            <p class="hero-kicker">ROUTE STORY</p>
            <h1>{{ detail.title || '未命名路线帖' }}</h1>
            <p class="hero-subtitle">{{ detail.shareNote || detail.routeSummary || '这条路线值得被认真读完。' }}</p>
            <div class="hero-meta">
              <span>{{ detail.authorLabel || '匿名旅人' }}</span>
              <span>{{ formatDateTime(detail.updatedAt) }}</span>
              <span>{{ formatDuration(detail.totalDuration) }}</span>
            </div>
          </div>

          <div class="hero-actions">
            <el-button round :type="detail.liked ? 'danger' : 'default'" :loading="likeSubmitting" @click="toggleLike">
              {{ detail.liked ? '已赞' : '点赞' }} {{ detail.likeCount || 0 }}
            </el-button>
            <el-button round @click="goBack">返回社区</el-button>
            <el-button v-if="detail.canDelete" round @click="deletePost">删除帖子</el-button>
            <el-button v-if="detail.canManage" round @click="toggleGlobalPin">
              {{ detail.globalPinned ? '取消全站置顶' : '设为全站置顶' }}
            </el-button>
          </div>
        </div>
      </section>

      <div class="detail-grid">
        <main class="detail-main">
          <section class="story-card">
            <div class="section-head">
              <div>
                <p class="section-kicker">STORY</p>
                <h2>路线摘要</h2>
              </div>
              <div class="chip-row">
                <span v-for="theme in detail.themes || []" :key="theme" class="theme-chip">{{ theme }}</span>
              </div>
            </div>
            <p class="story-copy">{{ detail.shareNote || detail.routeSummary || '暂无分享语。' }}</p>
          </section>

          <section v-if="pinnedComment" class="pinned-banner">
            <div>
              <p class="section-kicker">AUTHOR PICK</p>
              <h3>作者置顶评论</h3>
            </div>
            <p>{{ pinnedComment.content }}</p>
            <small>{{ pinnedComment.authorLabel }} · {{ formatDateTime(pinnedComment.createTime) }}</small>
          </section>

          <section class="timeline-section">
            <div class="section-head compact">
              <div>
                <p class="section-kicker">TIMELINE</p>
                <h2>路线时间线</h2>
              </div>
            </div>

            <div class="timeline-list">
              <article v-for="node in detail.nodes || []" :key="`${node.poiId}-${node.stepOrder}`" class="timeline-card">
                <div class="timeline-time">{{ node.startTime }} - {{ node.endTime }}</div>
                <div class="timeline-body">
                  <p class="timeline-index">第 {{ node.stepOrder }} 站</p>
                  <h3>{{ node.poiName }}</h3>
                  <p class="timeline-copy">{{ node.sysReason }}</p>
                  <div class="timeline-meta">
                    <span>{{ node.category }}</span>
                    <span>{{ node.district || '城区待定' }}</span>
                    <span>停留 {{ node.stayDuration || 0 }} 分钟</span>
                    <span>花费 ¥{{ node.cost || 0 }}</span>
                  </div>
                </div>
              </article>
            </div>
          </section>

          <section class="comment-section">
            <div class="section-head compact">
              <div>
                <p class="section-kicker">COMMENTS</p>
                <h2>路线讨论区</h2>
              </div>
              <span>{{ detail.commentCount || comments.length }} 条评论</span>
            </div>

            <CommentComposer
              v-model="commentDraft"
              :logged-in="Boolean(authState.user)"
              :loading="commentSubmitting"
              :replying-to="replyingTo"
              @submit="submitComment"
              @cancel-reply="setReplyTarget(null)"
              @login="goLogin"
            />

            <div v-if="comments.length" class="comment-list">
              <article v-for="comment in comments" :key="comment.id" class="comment-card">
                <div class="comment-head">
                  <div>
                    <div class="comment-title-row">
                      <strong>{{ comment.authorLabel }}</strong>
                      <span v-if="comment.pinned" class="pinned-pill">作者置顶</span>
                    </div>
                    <small>{{ formatDateTime(comment.createTime) }}</small>
                  </div>
                  <div class="comment-actions">
                    <el-button link type="primary" @click="setReplyTarget(comment)">回复</el-button>
                    <el-button v-if="comment.canPin" link type="primary" @click="pinComment(comment)">置顶评论</el-button>
                  </div>
                </div>
                <p>{{ comment.content }}</p>

                <div v-if="comment.replies?.length" class="reply-list">
                  <div v-for="reply in comment.replies" :key="reply.id" class="reply-card">
                    <div class="comment-head">
                      <div>
                        <strong>{{ reply.authorLabel }}</strong>
                        <small>{{ formatDateTime(reply.createTime) }}</small>
                      </div>
                    </div>
                    <p>{{ reply.content }}</p>
                  </div>
                </div>
              </article>
            </div>
            <el-empty v-else description="还没有评论，来留下第一条分享吧。" class="empty-card" />
          </section>
        </main>

        <aside class="detail-side">
          <section class="side-card metric-card">
            <p class="section-kicker">OVERVIEW</p>
            <h3>路线信息</h3>
            <div class="metric-list">
              <div>
                <span>时长</span>
                <strong>{{ formatDuration(detail.totalDuration) }}</strong>
              </div>
              <div>
                <span>预算</span>
                <strong>{{ formatCurrency(detail.totalCost) }}</strong>
              </div>
              <div>
                <span>站点数</span>
                <strong>{{ detail.nodeCount || 0 }}</strong>
              </div>
              <div>
                <span>点赞</span>
                <strong>{{ detail.likeCount || 0 }}</strong>
              </div>
            </div>
          </section>

          <section class="side-card">
            <p class="section-kicker">ACTIONS</p>
            <h3>下一步</h3>
            <div class="side-actions">
              <el-button round @click="goBack">继续逛社区</el-button>
              <el-button round @click="goHome">返回首页</el-button>
            </div>
          </section>
        </aside>
      </div>
    </div>

    <div v-else-if="loadError" class="detail-shell">
      <section class="side-card error-card">
        <el-result icon="error" title="路线帖加载失败" sub-title="它可能被删除、撤回，或者暂时不可访问。">
          <template #extra>
            <el-button type="primary" round @click="loadPage">重新加载</el-button>
          </template>
        </el-result>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import CommentComposer from '@/components/community/CommentComposer.vue'
import {
  reqCreateCommunityComment,
  reqDeleteCommunityPost,
  reqGetCommunityItinerary,
  reqLikeCommunityItinerary,
  reqListCommunityComments,
  reqPinCommunityComment,
  reqUnlikeCommunityItinerary
} from '@/api/itinerary'
import { reqAdminCommunityDelete, reqAdminCommunityPin } from '@/api/adminCommunity'
import { initAuthState, useAuthState } from '@/store/auth'
import { applyCommunityCoverFallback, resolveCommunityCover } from '@/utils/communityCover'

const route = useRoute()
const router = useRouter()
const authState = useAuthState()
const detail = ref(null)
const comments = ref([])
const loadError = ref(false)
const commentDraft = ref('')
const commentSubmitting = ref(false)
const likeSubmitting = ref(false)
const replyingTo = ref(null)

const itineraryId = computed(() => Number(route.params.id))
const detailCoverImage = computed(() => resolveCommunityCover(
  detail.value?.coverImageUrl,
  detail.value?.id || detail.value?.title || detail.value?.routeSummary
))

const pinnedComment = computed(() => {
  if (detail.value?.pinnedComment) return detail.value.pinnedComment
  return comments.value.find(item => item.pinned) || null
})

const loadPage = async () => {
  loadError.value = false
  try {
    const [detailRes, commentRes] = await Promise.all([
      reqGetCommunityItinerary(itineraryId.value),
      reqListCommunityComments(itineraryId.value)
    ])
    detail.value = detailRes
    comments.value = Array.isArray(commentRes) ? commentRes : []
  } catch (error) {
    detail.value = null
    comments.value = []
    loadError.value = true
  }
}

const submitComment = async () => {
  if (!authState.user) {
    goLogin()
    return
  }
  if (!commentDraft.value.trim()) {
    ElMessage.warning('请先输入评论内容')
    return
  }

  commentSubmitting.value = true
  try {
    await reqCreateCommunityComment(itineraryId.value, {
      content: commentDraft.value.trim(),
      parentId: replyingTo.value?.id || null
    })
    commentDraft.value = ''
    replyingTo.value = null
    await loadPage()
    ElMessage.success('评论已发布')
  } finally {
    commentSubmitting.value = false
  }
}

const toggleLike = async () => {
  if (!authState.user) {
    goLogin()
    return
  }

  likeSubmitting.value = true
  try {
    detail.value = detail.value?.liked
      ? await reqUnlikeCommunityItinerary(itineraryId.value)
      : await reqLikeCommunityItinerary(itineraryId.value)
    comments.value = await reqListCommunityComments(itineraryId.value)
  } finally {
    likeSubmitting.value = false
  }
}

const pinComment = async comment => {
  try {
    await reqPinCommunityComment(itineraryId.value, comment.id)
    await loadPage()
    ElMessage.success('已置顶这条评论')
  } catch (error) {}
}

const deletePost = async () => {
  if (!detail.value) return
  try {
    await ElMessageBox.confirm('删除后，这条帖子会从社区流中移除，但你的原始路线仍保留在历史行程中。', '删除帖子', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })

    if (detail.value.canManage && authState.user?.id !== detail.value.authorId) {
      await reqAdminCommunityDelete(detail.value.id)
    } else {
      await reqDeleteCommunityPost(detail.value.id)
    }
    ElMessage.success('帖子已删除')
    router.push('/community')
  } catch (error) {}
}

const toggleGlobalPin = async () => {
  if (!detail.value) return
  const nextPinned = !detail.value.globalPinned
  await reqAdminCommunityPin(detail.value.id, nextPinned)
  await loadPage()
  ElMessage.success(nextPinned ? '已设为全站置顶' : '已取消全站置顶')
}

const setReplyTarget = comment => {
  if (comment && !authState.user) {
    goLogin()
    return
  }
  replyingTo.value = comment
}

const goBack = () => router.push('/community')
const goHome = () => router.push('/')
const goLogin = () => {
  router.push({
    path: '/auth',
    query: {
      redirect: route.fullPath
    }
  })
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

onMounted(async () => {
  await initAuthState()
  await loadPage()
})
</script>

<style scoped>
.detail-page {
  min-height: calc(100vh - 64px);
  padding: 34px 20px 58px;
  background:
    radial-gradient(circle at top left, rgba(124, 182, 255, 0.16), transparent 22%),
    radial-gradient(circle at top right, rgba(211, 230, 255, 0.2), transparent 28%),
    linear-gradient(180deg, #f7fbff 0%, #f2f7ff 100%);
}

.detail-shell {
  max-width: 1240px;
  margin: 0 auto;
}

.detail-hero {
  position: relative;
  min-height: 420px;
  border-radius: 34px;
  overflow: hidden;
  box-shadow: var(--shadow-strong);
}

.hero-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
  position: absolute;
  inset: 0;
}

.hero-overlay {
  position: relative;
  z-index: 1;
  min-height: 420px;
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 30px;
  background: linear-gradient(180deg, rgba(18, 42, 79, 0.18), rgba(18, 42, 79, 0.72));
  color: #f6fbff;
}

.hero-content {
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
}

.hero-kicker,
.section-kicker {
  margin: 0 0 10px;
  color: var(--brand-600);
  font-size: 12px;
  letter-spacing: 0.16em;
}

.hero-kicker {
  color: rgba(222, 237, 255, 0.92);
}

.hero-content h1 {
  margin: 0;
  max-width: 720px;
  font-size: clamp(32px, 4vw, 52px);
  line-height: 1.08;
  font-family: var(--font-display);
}

.hero-subtitle {
  margin: 16px 0 0;
  max-width: 720px;
  color: rgba(236, 245, 255, 0.88);
  line-height: 1.85;
}

.hero-meta,
.timeline-meta,
.metric-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 14px;
}

.hero-meta {
  margin-top: 18px;
  color: rgba(236, 245, 255, 0.82);
  font-size: 13px;
}

.hero-actions {
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: flex-end;
  justify-content: flex-start;
}

.hero-actions :deep(.el-button) {
  min-width: 144px;
  border-radius: 999px;
  border-color: rgba(218, 234, 255, 0.34);
  background: rgba(255, 255, 255, 0.14);
  color: #f6fbff;
  backdrop-filter: blur(10px);
}

.hero-actions :deep(.el-button--danger) {
  background: rgba(255, 255, 255, 0.22);
}

.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) 340px;
  gap: 22px;
  margin-top: 26px;
}

.story-card,
.timeline-card,
.comment-card,
.side-card,
.pinned-banner,
.empty-card,
.reply-card,
.comment-section {
  border-radius: var(--radius-panel);
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(188, 214, 255, 0.84);
  box-shadow: var(--shadow-soft);
}

.story-card,
.pinned-banner,
.comment-section {
  padding: 24px;
}

.section-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.section-head h2,
.side-card h3,
.pinned-banner h3,
.timeline-body h3 {
  margin: 0;
  color: var(--text-strong);
  font-family: var(--font-display);
}

.chip-row,
.side-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.theme-chip,
.pinned-pill {
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid rgba(188, 214, 255, 0.78);
  background: rgba(244, 249, 255, 0.94);
  color: var(--brand-600);
  font-size: 12px;
}

.story-copy,
.pinned-banner p,
.timeline-copy,
.comment-card p,
.reply-card p {
  margin: 16px 0 0;
  color: var(--text-body);
  line-height: 1.85;
}

.pinned-banner {
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(240, 247, 255, 0.92));
}

.pinned-banner small,
.comment-head small {
  color: var(--text-soft);
}

.timeline-section,
.comment-section {
  margin-top: 22px;
}

.timeline-list,
.comment-list,
.reply-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.timeline-card {
  display: grid;
  grid-template-columns: 140px minmax(0, 1fr);
  gap: 18px;
  padding: 22px;
}

.timeline-time,
.timeline-index {
  color: var(--brand-600);
  font-size: 13px;
  letter-spacing: 0.08em;
}

.timeline-meta {
  margin-top: 14px;
  color: var(--text-soft);
  font-size: 13px;
}

.comment-section {
  padding: 24px;
}

.comment-list {
  margin-top: 18px;
}

.comment-card,
.reply-card {
  padding: 20px;
}

.comment-head,
.comment-actions,
.comment-title-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.comment-head {
  align-items: flex-start;
}

.reply-card {
  background: rgba(247, 251, 255, 0.94);
  border-radius: 20px;
}

.reply-list {
  margin-top: 14px;
}

.side-card {
  padding: 22px;
}

.metric-list {
  margin-top: 18px;
  flex-direction: column;
  gap: 16px;
}

.metric-list span {
  display: block;
  color: var(--text-soft);
  font-size: 13px;
}

.metric-list strong {
  display: block;
  margin-top: 6px;
  color: var(--text-strong);
  font-size: 24px;
  font-family: var(--font-display);
}

.side-actions {
  margin-top: 18px;
}

.error-card {
  padding: 12px;
}

@media (max-width: 980px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }

  .hero-overlay,
  .timeline-card,
  .section-head,
  .comment-head,
  .comment-actions,
  .comment-title-row {
    grid-template-columns: 1fr;
    flex-direction: column;
    align-items: flex-start;
  }

  .hero-actions {
    align-items: flex-start;
  }
}
</style>
