<template>
  <div class="result-page">
    <div v-if="itinerary" class="result-shell">
      <section class="hero-card">
        <div class="hero-main">
          <p class="eyebrow">已保存路线快照</p>
          <h1>{{ itinerary.customTitle || activeOption?.title || '你的专属路线' }}</h1>
          <p class="hero-copy">{{ heroSummary }}</p>
          <p v-if="departureSummary" class="hero-location">{{ departureSummary }}</p>
          <div class="hero-pill-row">
            <span v-for="pill in heroPills" :key="pill" class="hero-pill">{{ pill }}</span>
          </div>
        </div>

        <aside class="hero-side">
          <div class="hero-primary-action">
            <el-button
              v-if="resultActionGroups.primary.includes('replan')"
              type="primary"
              round
              size="large"
              class="hero-main-cta"
              :loading="replanning"
              @click="handleReplan"
            >
              换一版路线
            </el-button>
          </div>

          <div v-if="resultActionGroups.secondary.length" class="hero-action-block">
            <span class="hero-action-caption">常用操作</span>
            <div class="hero-secondary-actions">
              <el-button
                v-if="resultActionGroups.secondary.includes('favorite')"
                round
                class="hero-soft-btn"
                :loading="favoriteLoading"
                @click="handleFavorite"
              >
                {{ itinerary.favorited ? '取消收藏' : '收藏行程' }}
              </el-button>
              <el-button
                v-if="resultActionGroups.secondary.includes('publish')"
                round
                class="hero-soft-btn"
                :loading="publicLoading"
                @click="handleTogglePublic"
              >
                {{ itinerary.isPublic ? '撤回社区展示' : '发布路线帖' }}
              </el-button>

              <el-button
                v-if="itinerary?.id && isLoggedIn"
                round
                class="hero-soft-btn"
                @click="editDialogVisible = true"
              >
                编辑路线
              </el-button>
              <el-button
                v-if="resultActionGroups.secondary.includes('communityPost')"
                round
                class="hero-soft-btn"
                @click="openCommunityPost"
              >
                查看社区帖子
              </el-button>
              <el-button
                v-if="resultActionGroups.secondary.includes('poster')"
                round
                class="hero-soft-btn"
                :loading="posterLoading"
                @click="handleGeneratePoster"
              >
                生成分享海报
              </el-button>
            </div>
          </div>

          <div v-if="resultActionGroups.tertiary.length" class="hero-action-block hero-action-block-muted">
            <span class="hero-action-caption">页面跳转</span>
            <div class="hero-tertiary-actions">
              <el-button
                v-if="resultActionGroups.tertiary.includes('home')"
                text
                bg
                class="hero-quiet-btn"
                @click="goBack"
              >
                返回首页
              </el-button>
              <el-button
                v-if="resultActionGroups.tertiary.includes('community')"
                text
                bg
                class="hero-quiet-btn"
                @click="goCommunity"
              >
                社区大厅
              </el-button>
              <el-button
                v-if="resultActionGroups.tertiary.includes('history')"
                text
                bg
                class="hero-quiet-btn"
                @click="goHistory"
              >
                历史行程
              </el-button>
              <el-button
                v-if="resultActionGroups.tertiary.includes('login')"
                text
                bg
                class="hero-quiet-btn"
                @click="goLoginForSavedActions"
              >
                登录后保存与分享
              </el-button>
            </div>
          </div>
        </aside>
      </section>

      <section v-if="!isLoggedIn" class="guest-tip-card">
        <div>
          <p class="eyebrow">游客模式</p>
          <h2>先看路线，喜欢再登录</h2>
          <p class="guest-tip-copy">
            你现在可以直接查看地图、切换天数和导出海报；登录后再解锁收藏、历史记录和社区发布。
          </p>
        </div>
        <div class="guest-tip-actions">
          <el-button type="primary" round @click="goLoginForSavedActions">登录后继续</el-button>
        </div>
      </section>

      <section class="stats-grid">
        <article v-for="item in statItems" :key="item.label" class="stat-card" :class="[`tone-${item.tone}`]">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </article>
      </section>

      <section class="result-focus-grid">
        <div class="result-map-column">
          <div class="map-motion-stage" :class="[mapMotionStage, { switching: isMapSwitching }]">
            <span :key="mapTransitionKey" class="map-motion-token" aria-hidden="true"></span>
            <ItineraryMapCard
              :nodes="displayNodes"
              :departure-point="departurePoint"
              :day-label="activeDayLabel"
              :motion-stage="mapMotionStage"
              :motion-token="mapTransitionKey"
              :active-segment-index="activeTimelineSegmentIndex"
              :pinned-segment-index="pinnedSegmentIndex"
              :community-status-text="shareStatusText"
              class="map-section"
              @segment-hover="handleMapSegmentHover"
              @segment-leave="handleMapSegmentLeave"
              @segment-pin="handleMapSegmentPin"
            />
          </div>

          <section v-if="heroRecommendation" class="map-recommendation-card">
            <p class="map-recommendation-label">AI 推荐说明</p>
            <p class="map-recommendation-copy">{{ heroRecommendation }}</p>
          </section>
        </div>

        <section class="timeline-panel">
          <section v-if="routeWarmTip" class="ai-travel-tip-card">
            <p class="ai-travel-tip-label">AI 出行提示</p>
            <h3>出发前先看这条提示</h3>
            <p class="ai-travel-tip-copy">{{ routeWarmTip }}</p>
          </section>

          <div class="timeline-panel-head">
            <div>
              <p class="eyebrow">路线时间线</p>
              <h2>按顺序确认每一站的节奏</h2>
              <p class="timeline-panel-copy">
                把出发、停留与路上时间放在同一视图里，先确认节奏，再决定是否替换站点。
              </p>
            </div>

            <div v-if="dayPlans.length > 1" class="day-switcher">
              <el-button
                text
                bg
                round
                class="day-switcher-nav"
                :disabled="activeDayIndex <= 0"
                @click="goPrevDay"
              >
                前一天
              </el-button>
              <el-button
                v-for="plan in dayPlans"
                :key="`day-${plan.day}`"
                round
                :type="plan.dayIndex === activeDayIndex ? 'primary' : 'default'"
                class="day-switcher-btn"
                @click="setActiveDay(plan.dayIndex)"
              >
                {{ plan.label }}
              </el-button>
              <el-button
                text
                bg
                round
                class="day-switcher-nav"
                :disabled="activeDayIndex >= dayPlans.length - 1"
                @click="goNextDay"
              >
                后一天
              </el-button>
            </div>
          </div>

          <section v-if="activeAlerts.length" class="alert-strip">
            <span v-for="item in activeAlerts" :key="item" class="alert-chip">{{ item }}</span>
          </section>

          <section v-if="scheduleWarnings.length" class="alert-strip alert-strip-danger">
            <span v-for="item in scheduleWarnings" :key="`warning-${item}`" class="alert-chip alert-chip-danger">{{ item }}</span>
          </section>

          <div class="timeline-wrap">
            <div
              v-for="(node, nodeIndex) in displayNodes"
              :key="buildNodeKey(node)"
              :ref="element => setTimelineNodeRef(element, nodeIndex)"
              class="timeline-item"
              :class="buildTimelineSegmentClass(nodeIndex)"
            >
              <div class="timeline-rail">
                <span class="timeline-dot"></span>
                <span class="timeline-line"></span>
              </div>

              <div class="timeline-content">
                <div class="timeline-time-row">
                  <div class="timeline-time">{{ node.startTime || '--:--' }} - {{ node.endTime || '--:--' }}</div>
                  <span v-if="buildTimelineSegmentBadge(nodeIndex)" class="segment-sync-badge">
                    {{ buildTimelineSegmentBadge(nodeIndex) }}
                  </span>
                </div>
                <article class="stop-card">
                  <div class="stop-head">
                    <div>
                      <p class="stop-index">第 {{ node.stepOrder || 1 }} 站</p>
                      <h3>{{ formatPoiName(node.poiName, '未命名站点') }}</h3>
                    </div>
                    <div class="stop-tags">
                      <el-tag v-if="node.category" size="small" effect="plain">{{ node.category }}</el-tag>
                      <el-tag size="small" type="info" effect="plain">{{ node.district || '城区待定' }}</el-tag>
                      <el-tag
                        v-if="node.sourceType"
                        size="small"
                        :type="node.sourceType === 'external' ? 'warning' : 'success'"
                        effect="plain"
                      >
                        {{ node.sourceType === 'external' ? '实时 POI' : '本地 POI' }}
                      </el-tag>
                    </div>
                  </div>

                  <p class="stop-copy">{{ node.sysReason || '该站点已根据当前路线顺序完成排布。' }}</p>
                  <SegmentRouteGuideCard
                    v-if="node.segmentRouteGuide"
                    :guide="node.segmentRouteGuide"
                    :from-name="buildSegmentGuideFromName(nodeIndex)"
                    :to-name="buildSegmentGuideToName(node)"
                    :linked="activeTimelineSegmentIndex === nodeIndex"
                    :step-order="node.stepOrder || nodeIndex + 1"
                  />
                  <div class="segment-lazy-action">
                    <el-button
                      text
                      type="primary"
                      :loading="isSegmentTravelLoading(node)"
                      @click="handleCalculateSegmentTravel(node)"
                    >
                      {{ node.segmentRouteGuide ? '\u91CD\u65B0\u8BA1\u7B97\u600E\u4E48\u8D70' : '\u600E\u4E48\u8D70' }}
                    </el-button>
                  </div>
                  <div v-if="node.travelNarrative" class="travel-analysis-box">
                    <span class="stop-tip-label">AI 出行分析</span>
                    <p class="travel-analysis-copy">{{ node.travelNarrative }}</p>
                  </div>
                  <div class="meta-row">
                    <span>{{ formatNodeTravelLabel(node) }}</span>
                    <span v-if="formatTravelMode(node)">
                      {{ node.stepOrder === 1 ? '首段方式' : '本段方式' }} {{ formatTravelMode(node) }}
                    </span>
                    <span v-if="formatTravelDistance(node)">距离约 {{ formatTravelDistance(node) }} 公里</span>
                    <span>停留 {{ node.stayDuration || 0 }} 分钟</span>
                    <span>花费 {{ formatCurrency(node.cost ?? 0) }}</span>
                  </div>

                  <div class="actions-row">
                    <el-button round class="stop-action-btn" @click="goToDetail(node)">{{ detailActionText }}</el-button>
                  </div>
                </article>
              </div>
            </div>
          </div>
        </section>
      </section>

      <div ref="posterRef" class="poster-stage">
        <div class="poster-card">
          <div class="poster-header">
            <p class="poster-brand">行城有数 | 分享海报</p>
            <h2>{{ posterTitle }}</h2>
            <p class="poster-subtitle">
              {{ originalReq?.tripDate || '--' }} · {{ activeDayLabel }} · {{ formatDuration(activeOption?.totalDuration || itinerary.totalDuration) }}
            </p>
          </div>

          <div class="poster-summary">
            <div class="poster-metric">
              <span>当前视图点位</span>
              <strong>{{ displayNodes.length }}</strong>
            </div>
            <div class="poster-metric">
              <span>主题亮点</span>
              <strong>{{ posterHighlights.length }}</strong>
            </div>
          </div>

          <div class="poster-section">
            <p class="poster-label">路线亮点</p>
            <div class="poster-tags">
              <span v-for="highlight in posterHighlights" :key="highlight" class="poster-tag">{{ highlight }}</span>
            </div>
          </div>

          <div class="poster-section">
            <p class="poster-label">结构化时间线</p>
            <div class="poster-timeline">
              <div v-for="node in displayNodes" :key="`poster-${buildNodeKey(node)}`" class="poster-node">
                <div class="poster-node-time">{{ node.startTime || '--:--' }} - {{ node.endTime || '--:--' }}</div>
                <div class="poster-node-content">
                  <strong>{{ formatPoiName(node.poiName, '未命名站点') }}</strong>
                  <span>{{ node.category || '主题待补充' }} / {{ node.district || '城区待定' }}</span>
                </div>
              </div>
            </div>
          </div>

          <div class="poster-grid">
            <div class="poster-section">
              <p class="poster-label">模型推荐理由</p>
              <p class="poster-copy">{{ heroRecommendation }}</p>
            </div>
            <div class="poster-section map-preview">
              <p class="poster-label">地图区域预估</p>
              <div class="map-estimate">
                <span v-for="district in posterDistricts" :key="district" class="map-pill">{{ district }}</span>
                <p>{{ posterRoutePreview }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="result-shell">
      <el-empty description="暂时没有找到行程，请先回首页生成。" />
    </div>

    <SegmentTravelModeDialog
      :visible="showSegmentTravelModeDialog"
      :selected-mode="pendingSegmentTravelMode"
      :from-name="pendingSegmentTravelFromName"
      :to-name="pendingSegmentTravelToName"
      @select="handleSelectSegmentTravelMode"
      @confirm="handleConfirmSegmentTravelMode"
      @cancel="handleCancelSegmentTravelMode"
    />

    <ItineraryEditDialog
      v-model="editDialogVisible"
      :itinerary="itinerary"
      @applied="handleItineraryEdited"
    />

    <PublishRouteDialog
      v-model="publishDialogVisible"
      :itinerary="itinerary"
      @published="handlePublished"
    />
  </div>
</template>

<script setup>
import html2canvas from 'html2canvas'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PublishRouteDialog from '@/components/community/PublishRouteDialog.vue'
import ItineraryEditDialog from '@/components/itinerary/ItineraryEditDialog.vue'
import ItineraryMapCard from '@/components/itinerary/ItineraryMapCard.vue'
import SegmentRouteGuideCard from '@/components/itinerary/SegmentRouteGuideCard.vue'
import SegmentTravelModeDialog from '@/components/itinerary/SegmentTravelModeDialog.vue'
import {
  reqCalculateSegmentTravel,
  reqFavoriteItinerary,
  reqGenerateItinerary,
  reqGetItinerary,
  reqGetLatestItinerary,
  reqReplanItinerary,
  reqToggleItineraryPublic,
  reqUnfavoriteItinerary
} from '@/api/itinerary'
import { useAuthState } from '@/store/auth'
import {
  loadItinerarySnapshot,
  localizeItineraryText,
  normalizeItinerarySnapshot,
  saveItinerarySnapshot
} from '@/store/itinerary'
import {
  buildDayPlans,
  buildResultActionGroups,
  buildResultHeroContent,
  buildResultStatItems,
  formatNodeTravelLabel,
  formatTravelDistance,
  formatTravelMode
} from '@/utils/resultUi'
import {
  buildRouteSignature,
  formatCurrency,
  formatPoiName,
  resolveActiveNodes,
  resolveActiveOption,
  resolveOptions,
  sanitizeDisplayText
} from '@/utils/resultOptions'

const router = useRouter()
const route = useRoute()
const authState = useAuthState()
const itinerary = ref(null)
const replanning = ref(false)
const favoriteLoading = ref(false)
const publicLoading = ref(false)
const posterLoading = ref(false)
const posterRef = ref(null)
const publishDialogVisible = ref(false)
const editDialogVisible = ref(false)
const publishAutoOpened = ref(false)
const activeDayIndex = ref(0)
const hoveredSegmentIndex = ref(null)
const pinnedSegmentIndex = ref(null)
const segmentTravelLoading = ref({})
const showSegmentTravelModeDialog = ref(false)
const pendingSegmentTravelMode = ref('')
const pendingSegmentTravelSegmentIndex = ref(-1)
const pendingSegmentTravelFromName = ref('')
const pendingSegmentTravelToName = ref('')
const timelineNodeRefs = ref([])
const mapTransitionKey = ref(0)
const isMapSwitching = ref(false)
const mapMotionStage = ref('steady')
const MAP_SWITCH_OUT_MS = 110
const MAP_SWITCH_RESET_MS = 440
let mapSwitchOutTimer = null
let mapSwitchResetTimer = null
const ITINERARY_UPDATED_EVENT = 'citytrip:itinerary-updated'

const resetPendingSegmentTravelDialogState = () => {
  showSegmentTravelModeDialog.value = false
  pendingSegmentTravelMode.value = ''
  pendingSegmentTravelSegmentIndex.value = -1
  pendingSegmentTravelFromName.value = ''
  pendingSegmentTravelToName.value = ''
}

const originalReq = computed(() => itinerary.value?.originalReq || null)
const isLoggedIn = computed(() => Boolean(authState.user))
const routeItineraryId = computed(() => {
  const raw = Number(route.query.id)
  return Number.isFinite(raw) && raw > 0 ? raw : null
})

const ensureSeenRouteSignatures = snapshot => {
  if (!snapshot) {
    return snapshot
  }

  const currentSignature = buildRouteSignature(resolveActiveNodes(snapshot))
  const seenRouteSignatures = Array.isArray(snapshot.seenRouteSignatures)
    ? snapshot.seenRouteSignatures.filter(item => typeof item === 'string' && item.trim())
    : []

  if (currentSignature && !seenRouteSignatures.includes(currentSignature)) {
    seenRouteSignatures.push(currentSignature)
  }

  return {
    ...snapshot,
    seenRouteSignatures
  }
}

const persistItinerary = snapshot => {
  if (!snapshot) {
    itinerary.value = null
    return null
  }

  const nextSnapshot = ensureSeenRouteSignatures(normalizeItinerarySnapshot(snapshot))
  itinerary.value = nextSnapshot
  saveItinerarySnapshot(nextSnapshot)
  return nextSnapshot
}

const handleExternalItineraryUpdate = event => {
  const snapshot = event?.detail || null
  if (!snapshot) {
    itinerary.value = null
    return
  }

  const nextSnapshot = ensureSeenRouteSignatures(normalizeItinerarySnapshot(snapshot))
  if (routeItineraryId.value && Number(nextSnapshot?.id) !== routeItineraryId.value) {
    return
  }
  itinerary.value = nextSnapshot
}

const loadCurrentItinerary = async () => {
  const snapshot = loadItinerarySnapshot()

  if (routeItineraryId.value) {
    if (snapshot && Number(snapshot.id) === routeItineraryId.value) {
      itinerary.value = ensureSeenRouteSignatures(snapshot)
      return
    }
    try {
      const data = await reqGetItinerary(routeItineraryId.value)
      persistItinerary(data)
    } catch (error) {
      itinerary.value = null
    }
    return
  }

  if (snapshot) {
    itinerary.value = ensureSeenRouteSignatures(snapshot)
    return
  }

  if (isLoggedIn.value) {
    try {
      const latest = await reqGetLatestItinerary()
      if (latest) {
        persistItinerary(latest)
      }
    } catch (error) {
      itinerary.value = null
    }
  } else {
    itinerary.value = null
  }
}

onMounted(() => {
  loadCurrentItinerary()
  if (typeof window !== 'undefined') {
    window.addEventListener(ITINERARY_UPDATED_EVENT, handleExternalItineraryUpdate)
  }
})

watch(() => route.query.id, () => {
  loadCurrentItinerary()
})

watch(isLoggedIn, loggedIn => {
  if (loggedIn && !itinerary.value) {
    loadCurrentItinerary()
  }
})

watch(
  () => `${route.query.id || ''}:${route.query.publish || ''}`,
  () => {
    publishAutoOpened.value = false
  }
)

watch(
  [() => route.query.publish, isLoggedIn, () => itinerary.value?.id, () => itinerary.value?.isPublic],
  ([publish, loggedIn, id, isPublic]) => {
    if (publish === '1' && loggedIn && id && !isPublic && !publishAutoOpened.value) {
      publishAutoOpened.value = true
      publishDialogVisible.value = true
    }
  },
  { immediate: true }
)

const displayOptions = computed(() => resolveOptions(itinerary.value))
const activeOption = computed(() => resolveActiveOption(itinerary.value))
const activeOptionKey = computed(() => activeOption.value?.optionKey || null)
const activeNodes = computed(() => resolveActiveNodes(itinerary.value))
const dayPlans = computed(() => buildDayPlans(activeNodes.value))
const displayNodes = computed(() => {
  if (!dayPlans.value.length) {
    return activeNodes.value
  }
  return dayPlans.value[activeDayIndex.value]?.nodes || dayPlans.value[0]?.nodes || activeNodes.value
})
const departureLabel = computed(() => sanitizeDisplayText(originalReq.value?.departurePlaceName, '当前位置'))
const departurePoint = computed(() => {
  const latitude = Number(originalReq.value?.departureLatitude)
  const longitude = Number(originalReq.value?.departureLongitude)
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
    return null
  }
  return {
    latitude,
    longitude,
    label: departureLabel.value
  }
})
const activeDayLabel = computed(() => dayPlans.value[activeDayIndex.value]?.label || '全程总览')
const defaultTimelineSegmentIndex = computed(() => {
  if (!displayNodes.value.length) {
    return null
  }
  const externalIndex = displayNodes.value.findIndex((node, index) => {
    return node?.sourceType === 'external' || (index > 0 && displayNodes.value[index - 1]?.sourceType === 'external')
  })
  if (externalIndex >= 0) {
    return externalIndex
  }
  return Math.floor((displayNodes.value.length - 1) / 2)
})
const activeTimelineSegmentIndex = computed(() => {
  if (Number.isInteger(pinnedSegmentIndex.value)) {
    return pinnedSegmentIndex.value
  }
  if (Number.isInteger(hoveredSegmentIndex.value)) {
    return hoveredSegmentIndex.value
  }
  return defaultTimelineSegmentIndex.value
})
const detailActionText = computed(() => (isLoggedIn.value ? '查看详情并替换' : '登录后查看详情'))
const activeAlerts = computed(() => {
  if (Array.isArray(activeOption.value?.alerts) && activeOption.value.alerts.length) {
    return activeOption.value.alerts
  }
  return Array.isArray(itinerary.value?.alerts) ? itinerary.value.alerts : []
})
const scheduleWarnings = computed(() => {
  return Array.isArray(itinerary.value?.scheduleWarnings) ? itinerary.value.scheduleWarnings : []
})
const shareStatusText = computed(() => {
  if (!isLoggedIn.value) {
    return '当前是游客查看模式，登录后才能收藏路线、保存到历史并发布到社区。'
  }
  return itinerary.value?.isPublic
    ? '这条路线已发布到社区，社区大厅和帖子详情页都能看到它。'
    : '这条路线目前仅自己可见，点击“发布路线帖”后即可进入社区展示。'
})
const resultActionGroups = computed(() => buildResultActionGroups({
  isLoggedIn: isLoggedIn.value,
  isPublic: Boolean(itinerary.value?.isPublic)
}))
const statItems = computed(() => buildResultStatItems({
  activeOption: activeOption.value,
  activeNodes: activeNodes.value
}))
const heroContent = computed(() => buildResultHeroContent({
  itinerary: itinerary.value,
  activeOption: activeOption.value,
  displayNodes: displayNodes.value,
  dayPlans: dayPlans.value,
  displayOptions: displayOptions.value,
  isLoggedIn: isLoggedIn.value
}))
const heroSummary = computed(() => heroContent.value.summary)
const departureSummary = computed(() => heroContent.value.departureSummary)
const heroPills = computed(() => heroContent.value.pills || [])
const hasAiRecommendation = computed(() => {
  return Boolean(
    itinerary.value?.aiDecorated ||
    itinerary.value?.recommendationSource === 'llm' ||
    activeOption.value?.aiDecorated ||
    activeOption.value?.recommendationSource === 'llm'
  )
})
const heroRecommendation = computed(() => hasAiRecommendation.value ? heroContent.value.recommendation : '')
const isTemplateWarmTip = text => {
  const value = sanitizeDisplayText(text, '')
  if (!value) return false
  return [
    '\u7CFB\u7EDF\u5DF2\u6309\u5F53\u524D\u65F6\u95F4\u7A97',
    '\u53EF\u6267\u884C\u65B9\u6848',
    '\u5F53\u524D\u9ED8\u8BA4\u65B9\u6848',
    '\u5019\u9009\u8DEF\u7EBF',
    '\u51FA\u884C\u65E5\u671F'
  ].some(marker => value.includes(marker))
}

const buildRouteSpecificFallbackTip = nodes => {
  const list = Array.isArray(nodes) ? nodes.filter(Boolean) : []
  const names = list.map(node => sanitizeDisplayText(node?.poiName, '')).filter(Boolean)
  if (!names.length) return ''
  const joined = names.slice(0, 2).join('\u3001')
  const hasMountain = names.some(name => /\u9752\u57CE\u5C71|\u5C71|\u5CF0|\u5CAD/.test(name))
  const hasZooOrPanda = names.some(name => /\u52A8\u7269\u56ED|\u718A\u732B|\u7E41\u80B2/.test(name))
  const hasMuseum = names.some(name => /\u535A\u7269\u9986|\u7F8E\u672F\u9986|\u827A\u672F/.test(name))
  if (hasMountain) return `\u53BB${joined}\u6CE8\u610F\u9632\u6ED1\uFF0C\u6162\u6162\u8D70\u66F4\u5B89\u5168\u3002`
  if (hasZooOrPanda) return `\u53BB${joined}\u770B\u52A8\u7269\u653E\u677E\u770B\uFF0C\u522B\u8D76\u592A\u6025\u3002`
  if (hasMuseum) return `${joined}\u9002\u5408\u6162\u770B\uFF0C\u8BB0\u5F97\u7559\u70B9\u4F11\u606F\u65F6\u95F4\u3002`
  return `${joined}\u987A\u8DEF\u6162\u901B\uFF0C\u4E2D\u9014\u7559\u70B9\u4F11\u606F\u65F6\u95F4\u3002`
}

const routeWarmTip = computed(() => {
  const tip = sanitizeDisplayText(itinerary.value?.tips, '')
  if (tip && !isTemplateWarmTip(tip)) {
    return tip.length > 40 ? tip.slice(0, 40) : tip
  }
  return buildRouteSpecificFallbackTip(activeNodes.value)
})

const posterTitle = computed(() => itinerary.value?.customTitle || activeOption.value?.title || '城市漫游路线')
const posterHighlights = computed(() => {
  if (Array.isArray(activeOption.value?.highlights) && activeOption.value.highlights.length) {
    return activeOption.value.highlights.slice(0, 4)
  }
  return displayNodes.value.map(node => node.poiName).filter(Boolean).slice(0, 4)
})
const posterDistricts = computed(() => {
  const districts = [...new Set(displayNodes.value.map(node => node.district).filter(Boolean))]
  return districts.length ? districts.slice(0, 4) : ['城市核心区']
})
const posterRoutePreview = computed(() => {
  const names = displayNodes.value
    .map(node => formatPoiName(node?.poiName, ''))
    .filter(Boolean)
  if (!names.length) {
    return '当前路线会根据点位坐标和城区分布自动预估海报中的地图区域。'
  }
  return `覆盖区域：${posterDistricts.value.join(' / ')}；路线顺序：${names.join(' → ')}`
})

const clearTimelineSegmentState = () => {
  hoveredSegmentIndex.value = null
  pinnedSegmentIndex.value = null
}

const setTimelineNodeRef = (element, nodeIndex) => {
  if (element) {
    timelineNodeRefs.value[nodeIndex] = element
    return
  }
  timelineNodeRefs.value[nodeIndex] = null
}

const isNodeLinkedToSegment = (nodeIndex, segmentIndex = activeTimelineSegmentIndex.value) => {
  if (!Number.isInteger(segmentIndex)) {
    return false
  }
  if (segmentIndex === 0) {
    return nodeIndex === 0
  }
  return nodeIndex === segmentIndex - 1 || nodeIndex === segmentIndex
}

const buildSegmentGuideFromName = nodeIndex => {
  if (nodeIndex === 0) {
    return departureLabel.value
  }
  return formatPoiName(displayNodes.value[nodeIndex - 1]?.poiName, '上一站')
}

const buildSegmentGuideToName = node => formatPoiName(node?.poiName, '当前站')

const buildTimelineSegmentClass = nodeIndex => {
  if (!isNodeLinkedToSegment(nodeIndex)) {
    return ''
  }
  if (activeTimelineSegmentIndex.value === 0) {
    return 'segment-entry linked'
  }
  return nodeIndex === activeTimelineSegmentIndex.value - 1 ? 'segment-entry linked' : 'segment-exit linked'
}

const buildTimelineSegmentBadge = nodeIndex => {
  if (!isNodeLinkedToSegment(nodeIndex)) {
    return ''
  }
  if (activeTimelineSegmentIndex.value === 0) {
    return '地图联动首站'
  }
  return nodeIndex === activeTimelineSegmentIndex.value - 1 ? '地图联动起点' : '地图联动终点'
}

const scrollTimelineSegmentIntoView = segmentIndex => {
  const targetIndex = Math.max(Number(segmentIndex) || 0, 0)
  const target = timelineNodeRefs.value[targetIndex]
  target?.scrollIntoView({
    behavior: 'smooth',
    block: 'center',
    inline: 'nearest'
  })
}

const handleMapSegmentHover = segmentIndex => {
  hoveredSegmentIndex.value = segmentIndex
}

const handleMapSegmentLeave = () => {
  hoveredSegmentIndex.value = null
}

const handleMapSegmentPin = segmentIndex => {
  if (pinnedSegmentIndex.value === segmentIndex) {
    pinnedSegmentIndex.value = null
    hoveredSegmentIndex.value = null
    return
  }
  pinnedSegmentIndex.value = segmentIndex
  hoveredSegmentIndex.value = segmentIndex
  scrollTimelineSegmentIntoView(segmentIndex)
}

const clearMapSwitchTimers = () => {
  if (mapSwitchOutTimer) {
    clearTimeout(mapSwitchOutTimer)
    mapSwitchOutTimer = null
  }
  if (mapSwitchResetTimer) {
    clearTimeout(mapSwitchResetTimer)
    mapSwitchResetTimer = null
  }
}

const prepareMapDaySwitch = () => {
  clearMapSwitchTimers()
  isMapSwitching.value = true
  mapMotionStage.value = 'switching-out'
}

const runMapDaySwitchAnimation = () => {
  if (mapMotionStage.value !== 'switching-out') {
    prepareMapDaySwitch()
  }

  const scheduleFrame = typeof window !== 'undefined' && typeof window.requestAnimationFrame === 'function'
    ? window.requestAnimationFrame.bind(window)
    : callback => callback()

  scheduleFrame(() => {
    mapSwitchOutTimer = setTimeout(() => {
      mapTransitionKey.value += 1
      mapMotionStage.value = 'switching-in'
    }, MAP_SWITCH_OUT_MS)

    mapSwitchResetTimer = setTimeout(() => {
      isMapSwitching.value = false
      mapMotionStage.value = 'steady'
    }, MAP_SWITCH_RESET_MS)
  })
}

watch(activeOptionKey, () => {
  activeDayIndex.value = 0
  clearTimelineSegmentState()
  timelineNodeRefs.value = []
  clearMapSwitchTimers()
  isMapSwitching.value = false
  mapMotionStage.value = 'steady'
  mapTransitionKey.value += 1
})

watch(activeDayIndex, (dayIndex, previousDayIndex) => {
  if (previousDayIndex === undefined || dayIndex === previousDayIndex || dayPlans.value.length <= 1) {
    return
  }
  clearTimelineSegmentState()
  timelineNodeRefs.value = []
  runMapDaySwitchAnimation()
})

watch(dayPlans, plans => {
  if (!plans.length) {
    activeDayIndex.value = 0
    clearTimelineSegmentState()
    timelineNodeRefs.value = []
    return
  }
  if (activeDayIndex.value > plans.length - 1) {
    activeDayIndex.value = 0
  }
}, { immediate: true })

watch(displayNodes, () => {
  timelineNodeRefs.value = []
  if (Number.isInteger(pinnedSegmentIndex.value) && pinnedSegmentIndex.value > displayNodes.value.length - 1) {
    pinnedSegmentIndex.value = null
  }
  if (Number.isInteger(hoveredSegmentIndex.value) && hoveredSegmentIndex.value > displayNodes.value.length - 1) {
    hoveredSegmentIndex.value = null
  }
}, { deep: true })

onBeforeUnmount(() => {
  clearMapSwitchTimers()
  if (typeof window !== 'undefined') {
    window.removeEventListener(ITINERARY_UPDATED_EVENT, handleExternalItineraryUpdate)
  }
})

const formatDuration = minutes => {
  const safeMinutes = Number(minutes || 0)
  if (!Number.isFinite(safeMinutes) || safeMinutes <= 0) return '--'
  const hour = Math.floor(safeMinutes / 60)
  const minute = safeMinutes % 60
  if (hour === 0) return `${minute} 分钟`
  if (minute === 0) return `${hour} 小时`
  return `${hour} 小时 ${minute} 分钟`
}

const setActiveDay = dayIndex => {
  if (!dayPlans.value.length) {
    activeDayIndex.value = 0
    return
  }
  const maxIndex = dayPlans.value.length - 1
  const nextIndex = Math.max(0, Math.min(dayIndex, maxIndex))
  if (nextIndex === activeDayIndex.value) {
    return
  }
  if (dayPlans.value.length > 1) {
    prepareMapDaySwitch()
  }
  activeDayIndex.value = nextIndex
}

const goPrevDay = () => {
  setActiveDay(activeDayIndex.value - 1)
}

const goNextDay = () => {
  setActiveDay(activeDayIndex.value + 1)
}

const buildNodeKey = node => {
  return [node?.dayNo || 1, node?.stepOrder || 0, node?.poiId || 'poi', node?.startTime || 'time'].join('-')
}

const resolveGlobalNodeIndex = node => {
  const directIndex = activeNodes.value.indexOf(node)
  if (directIndex >= 0) {
    return directIndex
  }
  const key = buildNodeKey(node)
  return activeNodes.value.findIndex(item => buildNodeKey(item) === key)
}

const resolveDisplayNodeIndex = node => {
  const directIndex = displayNodes.value.indexOf(node)
  if (directIndex >= 0) {
    return directIndex
  }
  const key = buildNodeKey(node)
  return displayNodes.value.findIndex(item => buildNodeKey(item) === key)
}

const isSegmentTravelLoading = node => {
  const index = resolveGlobalNodeIndex(node)
  return index >= 0 && Boolean(segmentTravelLoading.value[index])
}

const handleCalculateSegmentTravel = node => {
  if (!ensureLogin('计算通行方式')) return
  if (!itinerary.value?.id) {
    ElMessage.warning('当前路线尚未保存，暂时无法计算通行方式')
    return
  }

  const segmentIndex = resolveGlobalNodeIndex(node)
  if (segmentIndex < 0) {
    ElMessage.warning('未找到该路线段，请刷新后重试')
    return
  }

  const displayNodeIndex = resolveDisplayNodeIndex(node)
  pendingSegmentTravelSegmentIndex.value = segmentIndex
  pendingSegmentTravelMode.value = ''
  pendingSegmentTravelFromName.value = buildSegmentGuideFromName(displayNodeIndex >= 0 ? displayNodeIndex : 0)
  pendingSegmentTravelToName.value = buildSegmentGuideToName(node)
  showSegmentTravelModeDialog.value = true
}

const handleSelectSegmentTravelMode = mode => {
  pendingSegmentTravelMode.value = mode
}

const handleCancelSegmentTravelMode = () => {
  resetPendingSegmentTravelDialogState()
}

const handleConfirmSegmentTravelMode = async () => {
  if (!pendingSegmentTravelMode.value) {
    return
  }
  if (!itinerary.value?.id || pendingSegmentTravelSegmentIndex.value < 0) {
    resetPendingSegmentTravelDialogState()
    return
  }

  const segmentIndex = pendingSegmentTravelSegmentIndex.value

  segmentTravelLoading.value = {
    ...segmentTravelLoading.value,
    [segmentIndex]: true
  }

  try {
    const nextItinerary = await reqCalculateSegmentTravel(itinerary.value.id, pendingSegmentTravelSegmentIndex.value, pendingSegmentTravelMode.value)
    persistItinerary(nextItinerary)
    ElMessage.success('已按所选方式更新本段路线')
    resetPendingSegmentTravelDialogState()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '通行方式计算失败，请稍后重试')
  } finally {
    const nextLoading = { ...segmentTravelLoading.value }
    delete nextLoading[segmentIndex]
    segmentTravelLoading.value = nextLoading
  }
}


const goBack = () => {
  router.push('/')
}

const goCommunity = () => {
  router.push('/community')
}

const goHistory = () => {
  if (!ensureLogin('查看历史行程')) return
  router.push('/history')
}

const openCommunityPost = () => {
  if (!itinerary.value?.id) return
  router.push(`/community/${itinerary.value.id}`)
}

const goLoginForSavedActions = () => {
  router.push({
    path: '/auth',
    query: {
      redirect: route.fullPath
    }
  })
}

const goToDetail = node => {
  if (!ensureLogin('查看景点详情')) return
  if (node?.sourceType === 'user_custom' || !node?.poiId || Number(node.poiId) <= 0) {
    ElMessage.info('这个自定义地点暂不支持详情页，你可以在编辑路线里继续调整它。')
    return
  }
  router.push(`/detail/${node.poiId}`)
}

const handleItineraryEdited = payload => {
  persistItinerary(payload)
}

const ensureLogin = (actionText = '继续操作') => {
  if (isLoggedIn.value) {
    return true
  }
  ElMessage.warning(`${actionText}需要先登录`)
  router.push({
    path: '/auth',
    query: {
      redirect: route.fullPath
    }
  })
  return false
}

const handleFavorite = async () => {
  if (!ensureLogin('收藏这条路线')) return
  if (!itinerary.value?.id) return

  favoriteLoading.value = true
  try {
    if (itinerary.value.favorited) {
      await reqUnfavoriteItinerary(itinerary.value.id)
      itinerary.value = {
        ...itinerary.value,
        favorited: false,
        favoriteTime: null
      }
      saveItinerarySnapshot(itinerary.value)
      ElMessage.success('已取消收藏')
      return
    }

    const suggestedTitle = itinerary.value.customTitle || activeOption.value?.title || `${originalReq.value?.tripDate || '本次'}路线`
    const promptResult = await ElMessageBox.prompt(
      '给这条路线起个名字，后面回顾会更方便。',
      '收藏当前路线',
      {
        confirmButtonText: '收藏',
        cancelButtonText: '取消',
        inputValue: suggestedTitle,
        inputPlaceholder: '例如：周末春熙路轻松逛',
        inputValidator: value => {
          if (!value || !value.trim()) return '请输入路线名称'
          if (value.trim().length > 60) return '路线名称不能超过 60 个字'
          return true
        }
      }
    )

    const nextItinerary = await reqFavoriteItinerary(itinerary.value.id, {
      selectedOptionKey: activeOptionKey.value,
      title: promptResult.value.trim()
    })
    persistItinerary(nextItinerary)
    ElMessage.success('已加入收藏')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error?.response?.data?.message || '收藏失败，请稍后重试')
    }
  } finally {
    favoriteLoading.value = false
  }
}

const clearPublishQuery = () => {
  if (route.query.publish !== '1') return
  const nextQuery = { ...route.query }
  delete nextQuery.publish
  router.replace({ path: route.path, query: nextQuery })
}

const handlePublished = async payload => {
  const nextSnapshot = persistItinerary(payload)
  publishDialogVisible.value = false
  clearPublishQuery()
  ElMessage.success('路线帖已发布到社区')

  if (!nextSnapshot?.id) {
    router.push('/community')
    return
  }

  try {
    await ElMessageBox.confirm(
      '路线帖已经发布完成，现在去查看帖子详情吗？',
      '发布成功',
      {
        confirmButtonText: '查看帖子',
        cancelButtonText: '回社区大厅',
        type: 'success',
        distinguishCancelAndClose: true
      }
    )
    openCommunityPost()
  } catch (action) {
    if (action === 'cancel' || action === 'close') {
      router.push('/community')
    }
  }
}

const handleTogglePublic = async () => {
  if (!ensureLogin('发布这条路线')) return
  if (!itinerary.value?.id) return

  if (!itinerary.value.isPublic) {
    publishDialogVisible.value = true
    return
  }

  publicLoading.value = true
  try {
    await ElMessageBox.confirm(
      '撤回后，这条路线会从社区中隐藏，但你的历史行程和收藏不会丢失。',
      '撤回社区展示',
      {
        confirmButtonText: '确认撤回',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    const nextItinerary = await reqToggleItineraryPublic(itinerary.value.id, { isPublic: false })
    persistItinerary(nextItinerary)
    clearPublishQuery()
    ElMessage.success('已撤回社区展示')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      const message = error?.response?.data?.message || '社区展示状态更新失败，请稍后重试'
      ElMessage.error(message)
    }
  } finally {
    publicLoading.value = false
  }
}

const handleGeneratePoster = async () => {
  if (!posterRef.value || !displayNodes.value.length) {
    ElMessage.warning('当前行程暂无可导出的内容')
    return
  }

  posterLoading.value = true
  try {
    await nextTick()
    const canvas = await html2canvas(posterRef.value, {
      scale: 2,
      useCORS: true,
      backgroundColor: '#f4f8ff'
    })
    const dataUrl = canvas.toDataURL('image/png', 1)
    const link = document.createElement('a')
    link.href = dataUrl
    link.download = `${posterTitle.value || 'itinerary'}-poster.png`
    link.click()
    ElMessage.success('分享海报已生成，并开始下载')
  } catch (error) {
    ElMessage.error('海报生成失败，请稍后重试')
  } finally {
    posterLoading.value = false
  }
}

const handleReplan = async () => {
  if (!itinerary.value || !activeNodes.value.length) return

  replanning.value = true
  try {
    if (!itinerary.value.id) {
      if (!originalReq.value) {
        ElMessage.warning('当前没有可用的行程参数')
        return
      }
      const nextItinerary = await reqGenerateItinerary(originalReq.value)
      persistItinerary(nextItinerary)
      ElMessage.success('已重新生成一版新路线')
      return
    }

    const currentSignature = buildRouteSignature(activeNodes.value)
    const excludedSignatures = Array.isArray(itinerary.value.seenRouteSignatures)
      ? [...new Set([...itinerary.value.seenRouteSignatures, currentSignature].filter(Boolean))]
      : currentSignature ? [currentSignature] : []

    const res = await reqReplanItinerary({
      itineraryId: itinerary.value.id,
      currentNodes: activeNodes.value,
      originalReq: originalReq.value,
      excludedSignatures
    })

    if (res.success && res.changed) {
      const nextItinerary = ensureSeenRouteSignatures(normalizeItinerarySnapshot(res.itinerary))
      const nextSignature = buildRouteSignature(resolveActiveNodes(nextItinerary))
      nextItinerary.seenRouteSignatures = [...new Set([...excludedSignatures, nextSignature].filter(Boolean))]
      itinerary.value = nextItinerary
      saveItinerarySnapshot(itinerary.value)
      ElMessage.success(localizeItineraryText(res.message) || '已为你换出一组新的路线方案。')
      return
    }

    if (res.success) {
      await ElMessageBox.alert(
        localizeItineraryText(res.reason) || '当前条件下没有更优路线了，建议先保留这条路线。',
        '保留当前路线',
        { confirmButtonText: '确定', type: 'info' }
      )
      return
    }

    ElMessage.warning(localizeItineraryText(res.message) || '换线失败，请稍后重试。')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error?.response?.data?.message || '换线失败，请稍后重试。')
    }
  } finally {
    replanning.value = false
  }
}
</script>
<style scoped>
.result-page {
  min-height: calc(100vh - 64px);
  padding: 32px 20px 48px;
  background:
    radial-gradient(circle at top left, rgba(64, 158, 255, 0.12), transparent 28%),
    linear-gradient(180deg, #f6f9fe 0%, #f7f8fa 100%);
}

.result-shell {
  max-width: 1120px;
  margin: 0 auto;
}

.segment-lazy-action {
  margin: 10px 0 6px;
}

.hero-card,
.guest-tip-card,
.stat-card,
.copy-card,
.stop-card {
  border-radius: 24px;
  border: 1px solid rgba(223, 232, 244, 0.95);
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 18px 40px rgba(31, 45, 61, 0.06);
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
  margin: 0 0 8px;
  font-size: 34px;
  color: #1f2d3d;
}

.hero-copy {
  margin: 0;
  color: #627386;
  line-height: 1.8;
}

.hero-actions {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.login-action-btn {
  border-color: #cfe0f5;
}

.guest-tip-card {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: center;
  padding: 22px 24px;
  margin-bottom: 20px;
}

.guest-tip-card h2 {
  margin: 0 0 8px;
  color: #1f2d3d;
  font-size: 26px;
}

.guest-tip-copy {
  margin: 0;
  color: #627386;
  line-height: 1.8;
}

.guest-tip-actions {
  flex-shrink: 0;
}

.stats-grid,
.copy-grid {
  display: grid;
  gap: 18px;
}

.stats-grid,
.copy-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.stop-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.stop-index {
  margin: 0 0 6px;
  color: #2d79c7;
  font-size: 13px;
  font-weight: 700;
}

.stop-head h3 {
  margin: 0;
  color: #1f2d3d;
}

.stop-copy,
.stop-note,
.travel-analysis-copy {
  line-height: 1.8;
  color: #55687d;
}

.meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 16px;
  margin: 16px 0 14px;
  color: #66788c;
  font-size: 13px;
}

.stop-tags,
.alert-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.stop-note {
  color: #738498;
}

.travel-analysis-copy {
  color: #5c4da2;
}

.stat-card,
.copy-card,
.stop-card {
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

.copy-card p:last-child {
  margin: 0;
  color: #4f6278;
  line-height: 1.8;
}

.alert-strip {
  margin: 18px 0;
}

.alert-chip {
  padding: 10px 14px;
  border-radius: 999px;
  background: rgba(255, 247, 230, 0.92);
  border: 1px solid rgba(245, 182, 93, 0.45);
  color: #9a6a1f;
  font-size: 13px;
}

.alert-strip-danger .alert-chip-danger {
  background: rgba(255, 236, 239, 0.94);
  border-color: rgba(230, 86, 106, 0.32);
  color: #c0394b;
}

.map-motion-stage {
  position: relative;
  margin-bottom: 0;
  isolation: isolate;
  display: flex;
  flex: 1;
  transition: transform 0.34s ease, filter 0.34s ease;
}

.map-motion-stage::before {
  content: '';
  position: absolute;
  inset: 12px;
  border-radius: 32px;
  background: linear-gradient(135deg, rgba(95, 158, 255, 0.16), rgba(124, 92, 255, 0.1));
  filter: blur(24px);
  opacity: 0;
  transform: scale(0.97);
  transition: opacity 0.34s ease, transform 0.34s ease;
  pointer-events: none;
  z-index: 0;
}

.map-motion-stage.switching {
  transform: translateY(-2px);
}

.map-motion-stage.switching::before {
  opacity: 1;
  transform: scale(1);
}

.map-motion-stage.switching-out {
  filter: saturate(0.92) brightness(0.985);
}

.map-motion-stage.switching-in {
  animation: dayMapShift 440ms cubic-bezier(0.22, 1, 0.36, 1);
}

.map-motion-token {
  position: absolute;
  width: 0;
  height: 0;
  overflow: hidden;
}

.map-section {
  position: relative;
  z-index: 1;
  margin-bottom: 0;
  flex: 1;
  width: 100%;
}

.timeline-wrap {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.timeline-item {
  display: grid;
  grid-template-columns: 20px minmax(0, 1fr);
  gap: 14px;
  scroll-margin-top: 120px;
}

.timeline-item.linked {
  grid-template-columns: 24px minmax(0, 1fr);
}

.timeline-rail {
  position: relative;
  display: flex;
  justify-content: center;
}

.timeline-dot {
  position: relative;
  z-index: 2;
  width: 12px;
  height: 12px;
  margin-top: 8px;
  border-radius: 50%;
  background: linear-gradient(135deg, #409eff, #66b1ff);
  box-shadow: 0 0 0 6px rgba(64, 158, 255, 0.14);
}

.timeline-line {
  position: absolute;
  top: 24px;
  bottom: -24px;
  width: 2px;
  background: linear-gradient(180deg, rgba(64, 158, 255, 0.28), rgba(64, 158, 255, 0.04));
}

.timeline-item:last-child .timeline-line {
  display: none;
}

.timeline-time-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}

.timeline-time {
  margin-bottom: 0;
  color: #409eff;
  font-weight: 700;
}

.segment-sync-badge {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(95, 158, 255, 0.12);
  color: #2d79c7;
  font-size: 12px;
  font-weight: 700;
}

.timeline-content {
  transition: transform 0.24s ease;
}

.timeline-item.linked .timeline-content {
  transform: translateX(2px);
}

.timeline-item.linked .timeline-dot {
  width: 14px;
  height: 14px;
  box-shadow: 0 0 0 9px rgba(95, 158, 255, 0.12);
}

.timeline-item.segment-entry .timeline-dot {
  background: linear-gradient(135deg, #4f9fff, #7cb8ff);
}

.timeline-item.segment-entry .timeline-line {
  background: linear-gradient(180deg, rgba(95, 158, 255, 0.48), rgba(124, 92, 255, 0.12));
}

.timeline-item.segment-exit .timeline-dot {
  background: linear-gradient(135deg, #7c5cff, #a994ff);
  box-shadow: 0 0 0 9px rgba(124, 92, 255, 0.12);
}

.timeline-item.linked .stop-card {
  border-color: rgba(188, 214, 255, 0.9);
  box-shadow: 0 20px 42px rgba(95, 158, 255, 0.12);
}

.timeline-item.segment-exit .stop-card {
  border-color: rgba(204, 194, 255, 0.92);
  box-shadow: 0 20px 42px rgba(124, 92, 255, 0.12);
}

.stop-copy {
  margin-top: 14px;
}

.travel-analysis-box,
.nearby-box {
  margin-top: 16px;
  padding: 16px 18px;
  border-radius: 18px;
  border: 1px solid rgba(188, 214, 255, 0.78);
  background: rgba(248, 252, 255, 0.86);
}

.travel-analysis-box {
  border-color: rgba(168, 150, 255, 0.34);
  background: linear-gradient(180deg, rgba(247, 243, 255, 0.96), rgba(241, 236, 255, 0.88));
}

.stop-tip-label {
  display: inline-flex;
  margin-bottom: 10px;
  color: #2d79c7;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.06em;
}

.nearby-row + .nearby-row {
  margin-top: 8px;
}

.actions-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
}

.poster-stage {
  position: fixed;
  left: -99999px;
  top: 0;
  width: 960px;
  padding: 24px;
  background: #f4f8ff;
}

.poster-card {
  padding: 28px;
  border-radius: 32px;
  background:
    radial-gradient(circle at top right, rgba(64, 158, 255, 0.18), transparent 26%),
    linear-gradient(180deg, #ffffff 0%, #f7fbff 100%);
  box-shadow: 0 20px 50px rgba(31, 45, 61, 0.12);
}

.poster-brand,
.poster-label {
  margin: 0 0 10px;
  color: #2d79c7;
  font-size: 13px;
  font-weight: 700;
}

.poster-header h2 {
  margin: 0;
  font-size: 34px;
  color: #1f2d3d;
}

.poster-subtitle,
.poster-copy {
  color: #607185;
  line-height: 1.8;
}

.poster-summary,
.poster-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-top: 20px;
}

.poster-metric,
.poster-section {
  padding: 18px;
  border-radius: 20px;
  background: rgba(246, 250, 255, 0.96);
  border: 1px solid rgba(220, 230, 242, 0.9);
}

.poster-metric span {
  display: block;
  color: #7a8da3;
  margin-bottom: 8px;
}

.poster-metric strong {
  color: #1f2d3d;
  font-size: 24px;
}

.poster-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.poster-tag,
.map-pill {
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(64, 158, 255, 0.1);
  color: #2d79c7;
  font-size: 13px;
}

.poster-timeline {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.poster-node {
  display: grid;
  grid-template-columns: 160px 1fr;
  gap: 14px;
  align-items: center;
}

.poster-node-time {
  color: #409eff;
  font-weight: 700;
}

.poster-node-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: #506377;
}

.map-preview {
  grid-column: span 2;
}

.map-estimate {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.map-estimate p {
  width: 100%;
  margin: 8px 0 0;
  color: #607185;
  line-height: 1.8;
}

.hero-card {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(320px, 0.78fr);
  gap: 24px;
  padding: 32px;
}

.hero-main h1,
.timeline-panel-head h2 {
  font-family: var(--font-display);
  line-height: 1.08;
}

.hero-main h1 {
  font-size: 42px;
  margin: 0;
}

.hero-copy {
  margin: 16px 0 10px;
  font-size: 16px;
}

.hero-location {
  margin: 0;
  color: #1f2d3d;
  font-weight: 600;
}

.hero-pill-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 18px 0 20px;
}

.hero-pill {
  display: inline-flex;
  align-items: center;
  min-height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid rgba(126, 183, 255, 0.34);
  background: rgba(255, 255, 255, 0.84);
  color: #2d79c7;
  font-size: 13px;
  font-weight: 700;
}

.hero-footnote {
  margin: 0;
  padding-top: 16px;
  border-top: 1px dashed rgba(188, 214, 255, 0.7);
}

.hero-side {
  padding: 24px;
  border-radius: 22px;
  border: 1px solid rgba(188, 214, 255, 0.82);
  background: rgba(248, 252, 255, 0.84);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.88);
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.hero-side-label,
.hero-action-caption {
  margin: 0;
  color: #7a8da3;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hero-side-copy {
  margin: 0;
  color: #1f2d3d;
  line-height: 1.9;
  font-size: 15px;
}

.hero-main-cta {
  width: 100%;
  min-height: 46px;
}

.hero-action-block {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.hero-secondary-actions,
.hero-tertiary-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.hero-soft-btn,
.hero-quiet-btn,
.day-switcher-nav,
.day-switcher-btn,
.stop-action-btn {
  border-radius: 999px;
}

.hero-action-block-muted {
  margin-top: auto;
  padding-top: 18px;
  border-top: 1px dashed rgba(188, 214, 255, 0.74);
}

.stat-card.tone-primary {
  background:
    radial-gradient(circle at top right, rgba(95, 158, 255, 0.22), transparent 24%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(238, 246, 255, 0.96));
}

.result-focus-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.12fr) minmax(360px, 0.88fr);
  gap: 22px;
  align-items: stretch;
}

.result-map-column {
  display: flex;
  flex-direction: column;
  gap: 22px;
  min-height: 100%;
}

.map-recommendation-card {
  padding: 22px 24px;
  border-radius: 24px;
  border: 1px solid rgba(188, 214, 255, 0.84);
  background:
    radial-gradient(circle at top right, rgba(95, 158, 255, 0.14), transparent 24%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(246, 250, 255, 0.94));
  box-shadow: var(--shadow-soft);
}

.map-recommendation-label {
  margin: 0 0 12px;
  color: #7a8da3;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.map-recommendation-copy {
  margin: 0;
  color: #1f2d3d;
  line-height: 1.9;
}

.ai-travel-tip-card {
  margin-bottom: 20px;
  padding: 20px 22px;
  border-radius: 24px;
  border: 1px solid rgba(168, 150, 255, 0.34);
  background:
    radial-gradient(circle at top right, rgba(124, 92, 255, 0.14), transparent 26%),
    linear-gradient(180deg, rgba(250, 247, 255, 0.98), rgba(244, 240, 255, 0.9));
  box-shadow: 0 16px 34px rgba(124, 92, 255, 0.1);
}

.ai-travel-tip-label {
  display: inline-flex;
  margin: 0 0 8px;
  color: #6d55d8;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
}

.ai-travel-tip-card h3 {
  margin: 0;
  color: var(--text-strong);
  font-size: 22px;
  font-family: var(--font-display);
}

.ai-travel-tip-copy {
  margin: 10px 0 0;
  color: var(--text-body);
  line-height: 1.8;
}

.timeline-panel {
  padding: 24px;
  height: 100%;
}

.timeline-panel-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 18px;
}

.timeline-panel-head h2 {
  margin: 0 0 10px;
  font-size: 32px;
  color: #1f2d3d;
}

.timeline-panel-copy {
  margin: 0;
  max-width: 430px;
  color: #627386;
  line-height: 1.8;
}

.day-switcher {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

@keyframes dayMapShift {
  0% {
    opacity: 0.72;
    transform: translateY(10px) scale(0.985);
  }
  55% {
    opacity: 0.94;
    transform: translateY(-4px) scale(1.01);
  }
  100% {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@media (max-width: 1180px) {
  .hero-card,
  .result-focus-grid,
  .stats-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .hero-card,
  .guest-tip-card,
  .timeline-panel-head,
  .stop-head {
    flex-direction: column;
  }

  .day-switcher {
    justify-content: flex-start;
  }

  .hero-main h1,
  .timeline-panel-head h2 {
    font-size: 30px;
  }

  .poster-summary,
  .poster-grid,
  .poster-node {
    grid-template-columns: 1fr;
  }

  .map-preview {
    grid-column: span 1;
  }
}

@media (max-width: 768px) {
  .result-page {
    padding: 18px 12px 32px;
  }

  .result-shell {
    width: 100%;
  }

  .hero-card,
  .guest-tip-card,
  .timeline-panel,
  .map-recommendation-card {
    border-radius: 20px;
  }

  .hero-card {
    grid-template-columns: 1fr;
    padding: 22px 18px;
    gap: 18px;
  }

  .hero-main h1,
  .timeline-panel-head h2 {
    font-size: 26px;
    line-height: 1.18;
  }

  .hero-copy,
  .timeline-panel-copy {
    font-size: 14px;
    line-height: 1.75;
  }

  .hero-side {
    padding: 18px;
    border-radius: 18px;
  }

  .hero-secondary-actions,
  .hero-tertiary-actions,
  .hero-actions {
    flex-direction: column;
    align-items: stretch;
  }

  .hero-secondary-actions .el-button,
  .hero-tertiary-actions .el-button,
  .hero-main-cta {
    width: 100%;
  }

  .guest-tip-card {
    flex-direction: column;
    align-items: stretch;
    padding: 18px;
  }

  .guest-tip-actions .el-button {
    width: 100%;
  }

  .stats-grid,
  .copy-grid,
  .poster-summary,
  .poster-grid {
    grid-template-columns: 1fr;
  }

  .stat-card,
  .copy-card,
  .stop-card {
    padding: 18px;
    border-radius: 18px;
  }

  .result-focus-grid {
    grid-template-columns: 1fr;
    gap: 18px;
  }

  .result-map-column {
    gap: 18px;
  }

  .timeline-panel {
    padding: 18px;
  }

  .timeline-panel-head {
    flex-direction: column;
  }

  .day-switcher {
    justify-content: flex-start;
    overflow-x: auto;
    flex-wrap: nowrap;
    padding-bottom: 4px;
  }

  .day-switcher-btn {
    flex: 0 0 auto;
  }

  .timeline-item,
  .timeline-item.linked {
    grid-template-columns: 18px minmax(0, 1fr);
    gap: 10px;
  }

  .stop-head {
    flex-direction: column;
    gap: 8px;
  }

  .actions-row {
    justify-content: stretch;
  }

  .actions-row .el-button {
    width: 100%;
  }
}

@media (max-width: 480px) {
  .result-page {
    padding-inline: 10px;
  }

  .hero-card,
  .timeline-panel {
    padding: 16px;
  }

  .hero-main h1,
  .timeline-panel-head h2 {
    font-size: 24px;
  }

  .hero-pill-row,
  .stop-tags,
  .alert-strip {
    gap: 6px;
  }

  .hero-pill,
  .alert-chip,
  .poster-tag,
  .map-pill {
    font-size: 12px;
    padding: 7px 10px;
  }

  .stat-card strong {
    font-size: 24px;
  }
}
</style>
