<template>
  <div class="detail-page">
    <div class="detail-shell" v-if="poiDetail">
      <section class="hero-card">
        <el-button round @click="goBack">{{ text.back }}</el-button>
        <h1>{{ poiDetail.name }}</h1>
        <p class="hero-copy">{{ poiDetail.description }}</p>
        <div class="tag-row">
          <el-tag size="small" effect="plain">{{ poiDetail.category }}</el-tag>
          <el-tag size="small" type="info" effect="plain">{{ poiDetail.district }}</el-tag>
        </div>
      </section>

      <section class="detail-grid">
        <el-card shadow="never" class="info-card">
          <p class="eyebrow">{{ text.infoTitle }}</p>
          <div class="info-grid">
            <div class="info-item">
              <span>{{ text.stay }}</span>
              <strong>{{ poiDetail.stayDuration }} {{ text.minute }}</strong>
            </div>
            <div class="info-item">
              <span>{{ text.cost }}</span>
              <strong>{{ text.currency }}{{ poiDetail.avgCost }}</strong>
            </div>
            <div class="info-item">
              <span>{{ text.hours }}</span>
              <strong>{{ poiDetail.openTime || '--' }} - {{ poiDetail.closeTime || '--' }}</strong>
            </div>
            <div class="info-item">
              <span>{{ text.walking }}</span>
              <strong>{{ poiDetail.walkingLevel }}</strong>
            </div>
          </div>
        </el-card>

        <el-card shadow="never" class="replace-card">
          <p class="eyebrow">{{ text.replaceTitle }}</p>
          <p class="replace-copy">{{ text.replaceCopy }}</p>
          <el-button type="primary" round :loading="replacing" @click="handleReplace">
            {{ text.replaceAction }}
          </el-button>
        </el-card>
      </section>
    </div>

    <div class="detail-shell" v-else>
      <el-empty :description="text.empty" />
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { reqGetLatestItinerary, reqReplacePoi } from '@/api/itinerary'
import { reqGetPoiDetail } from '@/api/poi'
import { loadItinerarySnapshot, localizeItineraryText, normalizeItinerarySnapshot, normalizePoiDetail, saveItinerarySnapshot } from '@/store/itinerary'
import { pushRecentPoiContext } from '@/utils/chatContext'

const text = {
  back: '\u8FD4\u56DE\u884C\u7A0B',
  infoTitle: '\u6E38\u73A9\u4FE1\u606F',
  stay: '\u5EFA\u8BAE\u505C\u7559',
  cost: '\u4EBA\u5747\u6D88\u8D39',
  hours: '\u8425\u4E1A\u65F6\u95F4',
  walking: '\u6B65\u884C\u611F\u53D7',
  replaceTitle: '\u8C03\u6574\u5EFA\u8BAE',
  replaceCopy: '\u5982\u679C\u8FD9\u4E00\u7AD9\u4E0D\u591F\u5BF9\u5473\uFF0C\u53EF\u4EE5\u76F4\u63A5\u66FF\u6362\u4E3A\u76F8\u8FD1\u4E14\u66F4\u9002\u5408\u7684\u70B9\u4F4D\u3002',
  replaceAction: '\u66FF\u6362\u8FD9\u4E00\u7AD9',
  empty: '\u6682\u65F6\u65E0\u6CD5\u52A0\u8F7D\u70B9\u4F4D\u8BE6\u60C5\u3002',
  loadFailed: '\u52A0\u8F7D\u70B9\u4F4D\u8BE6\u60C5\u5931\u8D25\u3002',
  noItinerary: '\u6CA1\u6709\u53EF\u7528\u7684\u884C\u7A0B\u5FEB\u7167\uFF0C\u8BF7\u5148\u751F\u6210\u6216\u6062\u590D\u884C\u7A0B\u3002',
  replaceSuccess: '\u70B9\u4F4D\u5DF2\u66FF\u6362\uFF0C\u884C\u7A0B\u4E5F\u540C\u6B65\u5237\u65B0\u4E86\u3002',
  minute: '\u5206\u949F',
  currency: '\u00A5'
}

const route = useRoute()
const router = useRouter()
const poiDetail = ref(null)
const replacing = ref(false)
const itinerary = ref(null)

const targetPoiId = Number(route.params.id)

const resolveActiveNodes = (snapshot) => {
  if (!snapshot) {
    return []
  }
  if (Array.isArray(snapshot.options) && snapshot.options.length) {
    const selected = snapshot.options.find(option => option.optionKey === snapshot.selectedOptionKey) || snapshot.options[0]
    if (Array.isArray(selected?.nodes)) {
      return selected.nodes
    }
  }
  return Array.isArray(snapshot.nodes) ? snapshot.nodes : []
}

onMounted(async () => {
  itinerary.value = loadItinerarySnapshot()
  if (!itinerary.value) {
    try {
      const latest = await reqGetLatestItinerary()
      if (latest) {
        itinerary.value = normalizeItinerarySnapshot(latest)
        saveItinerarySnapshot(itinerary.value)
      }
    } catch (err) {
    }
  }

  try {
    poiDetail.value = normalizePoiDetail(await reqGetPoiDetail(targetPoiId, itinerary.value?.originalReq?.tripDate))
    pushRecentPoiContext(poiDetail.value)
  } catch (err) {
    ElMessage.error(text.loadFailed)
  }
})

const goBack = () => {
  router.back()
}

const handleReplace = async () => {
  const currentNodes = resolveActiveNodes(itinerary.value)

  if (!itinerary.value || !currentNodes.length) {
    ElMessage.warning(text.noItinerary)
    router.push('/result')
    return
  }

  replacing.value = true
  try {
    const res = await reqReplacePoi({
      itineraryId: itinerary.value.id,
      targetPoiId,
      currentNodes,
      originalReq: itinerary.value.originalReq
    })
    itinerary.value = normalizeItinerarySnapshot(res)
    saveItinerarySnapshot(itinerary.value)
    ElMessage.success(localizeItineraryText(text.replaceSuccess))
    router.push('/result')
  } catch (err) {
  } finally {
    replacing.value = false
  }
}
</script>

<style scoped>
.detail-page {
  min-height: calc(100vh - 64px);
  padding: 32px 20px 48px;
  background:
    radial-gradient(circle at top left, rgba(64, 158, 255, 0.12), transparent 28%),
    linear-gradient(180deg, #f6f9fe 0%, #f7f8fa 100%);
}

.detail-shell {
  max-width: 980px;
  margin: 0 auto;
}

.hero-card,
.info-card,
.replace-card {
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
.info-card:hover,
.replace-card:hover,
.info-item:hover {
  transform: translateY(-6px) scale(1.01);
  border-color: rgba(108, 176, 255, 0.58);
  box-shadow: 0 24px 48px rgba(31, 45, 61, 0.1);
}

.hero-card {
  padding: 28px 30px;
  margin-bottom: 22px;
}

.hero-card h1 {
  margin: 18px 0 10px;
  color: #1f2d3d;
  font-size: 34px;
}

.hero-copy {
  margin: 0;
  color: #607185;
  line-height: 1.8;
}

.tag-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 16px;
}

.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(260px, 0.7fr);
  gap: 20px;
}

.info-card,
.replace-card {
  padding: 24px;
}

.eyebrow {
  margin: 0 0 12px;
  color: #2d79c7;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.info-item {
  padding: 18px 20px;
  border-radius: 18px;
  background: linear-gradient(180deg, #f9fbff 0%, #f4f8ff 100%);
  border: 1px solid rgba(220, 230, 242, 0.9);
  transition:
    transform 0.22s ease,
    box-shadow 0.22s ease,
    border-color 0.22s ease;
}

.info-item span {
  display: block;
  color: #7a8da3;
  margin-bottom: 10px;
}

.info-item strong {
  color: #1f2d3d;
  font-size: 22px;
}

.replace-copy {
  margin: 0 0 18px;
  color: #647588;
  line-height: 1.8;
}

@media (max-width: 900px) {
  .detail-grid,
  .info-grid {
    grid-template-columns: 1fr;
  }
}
</style>
