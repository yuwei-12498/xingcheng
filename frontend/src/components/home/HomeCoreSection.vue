<template>
  <section ref="sectionRef" class="core-section" id="core">
    <div class="core-container">
      <div class="section-header text-center">
        <h2 class="section-title">{{ text.title }}</h2>
        <p class="section-subtitle">{{ text.subtitle }}</p>
      </div>

      <div class="main-card">
        <el-row :gutter="28" class="core-layout">
          <el-col :md="14" :lg="15" class="form-col">
            <div ref="formPaneRef" class="form-pane">
              <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="custom-form">
                <div class="smart-fill-panel">
                  <div class="sub-section-title">{{ text.smartFillTitle }}</div>
                  <p class="smart-fill-copy">{{ text.smartFillCopy }}</p>
                  <el-input
                    v-model="naturalInput"
                    type="textarea"
                    :rows="3"
                    resize="none"
                    :placeholder="text.smartFillPlaceholder"
                    class="smart-fill-input"
                  />
                  <div class="smart-fill-actions">
                    <el-button round :loading="smartFillLoading" @click="fillFormFromText">{{ text.smartFillAction }}</el-button>
                    <span class="smart-fill-tip">{{ text.smartFillTip }}</span>
                  </div>
                  <div v-if="parsedSummary.length" class="smart-fill-summary">
                    <span class="smart-fill-summary-label">{{ text.smartFillSummary }}</span>
                    <el-tag
                      v-for="item in parsedSummary"
                      :key="item"
                      size="small"
                      effect="plain"
                    >
                      {{ item }}
                    </el-tag>
                  </div>
                </div>

                <el-divider border-style="dashed" />

                <div class="form-section">
                  <div class="sub-section-title">{{ text.sectionBasic }}</div>
                  <el-row :gutter="24">
                    <el-col :xs="24" :sm="12">
                      <el-form-item :label="text.city" prop="cityCode">
                        <el-select v-model="form.cityCode" :placeholder="text.cityPlaceholder" style="width: 100%" @change="handleCityChange">
                          <el-option
                            v-for="option in cityOptions"
                            :key="option.code"
                            :label="option.label"
                            :value="option.code"
                          />
                        </el-select>
                      </el-form-item>
                    </el-col>
                    <el-col :xs="24" :sm="12">
                      <el-form-item :label="text.departurePlace">
                        <el-input
                          v-model="form.departurePlaceName"
                          clearable
                          :placeholder="text.departurePlacePlaceholder"
                        />
                      </el-form-item>
                    </el-col>
                  </el-row>

                  <el-row :gutter="24">
                    <el-col :xs="24" :sm="12">
                      <el-form-item :label="text.tripMode" prop="tripDays">
                        <el-radio-group v-model="form.tripDays" class="full-width-radio">
                          <el-radio-button
                            v-for="option in tripDayOptions"
                            :key="option.value"
                            :label="option.value"
                          >
                            {{ option.label }}
                          </el-radio-button>
                        </el-radio-group>
                      </el-form-item>
                    </el-col>
                    <el-col :xs="24" :sm="12">
                      <el-form-item :label="text.tripDate" prop="tripDate">
                        <el-date-picker
                          v-model="form.tripDate"
                          type="date"
                          value-format="YYYY-MM-DD"
                          :placeholder="text.tripDatePlaceholder"
                          style="width: 100%"
                        />
                      </el-form-item>
                    </el-col>
                  </el-row>

                  <el-row :gutter="24">
                    <el-col :xs="24" :sm="12">
                      <el-form-item :label="text.startTime" prop="startTime">
                        <el-time-select
                          v-model="form.startTime"
                          start="06:00"
                          step="00:30"
                          end="14:00"
                          :placeholder="text.startTime"
                          style="width: 100%"
                        />
                      </el-form-item>
                    </el-col>
                    <el-col :xs="24" :sm="12">
                      <el-form-item :label="text.endTime" prop="endTime">
                        <el-time-select
                          v-model="form.endTime"
                          :min-time="form.startTime"
                          start="11:00"
                          step="00:30"
                          end="23:30"
                          :placeholder="text.endTime"
                          style="width: 100%"
                        />
                      </el-form-item>
                    </el-col>
                  </el-row>

                  <p v-if="form.tripDays > 1" class="trip-mode-hint">
                    {{ text.tripModeHint }}
                  </p>
                </div>

                <el-divider border-style="dashed" />

                <div class="form-section">
                  <div class="sub-section-title">{{ text.sectionPreference }}</div>

                  <el-form-item :label="text.themes" prop="themes">
                    <el-checkbox-group v-model="form.themes" class="theme-checkbox-group">
                      <el-checkbox-button
                        v-for="option in themeOptions"
                        :key="option.value"
                        :label="option.value"
                      >
                        {{ option.label }}
                      </el-checkbox-button>
                    </el-checkbox-group>
                  </el-form-item>
                </div>

                <div class="advanced-toggle-row">
                  <el-button text class="advanced-toggle-btn" @click="showAdvanced = !showAdvanced">
                    {{ showAdvanced ? text.hideAdvanced : text.showAdvanced }}
                  </el-button>
                  <span class="advanced-toggle-tip">{{ text.advancedTip }}</span>
                </div>

                <template v-if="showAdvanced">
                  <el-divider border-style="dashed" />

                  <div class="form-section">
                    <div class="sub-section-title">{{ text.sectionAdvanced }}</div>

                    <el-row :gutter="24">
                      <el-col :xs="24" :sm="12">
                        <el-form-item :label="text.budget" prop="budgetLevel">
                          <el-select v-model="form.budgetLevel" :placeholder="text.budgetPlaceholder" style="width: 100%">
                            <el-option
                              v-for="option in budgetOptions"
                              :key="option.value"
                              :label="option.label"
                              :value="option.value"
                            />
                          </el-select>
                        </el-form-item>
                      </el-col>
                    </el-row>

                    <el-row :gutter="24" class="compact-row">
                      <el-col :xs="24" :sm="12">
                        <el-form-item :label="text.companion" prop="companionType">
                          <el-radio-group v-model="form.companionType">
                            <el-radio
                              v-for="option in companionOptions"
                              :key="option.value"
                              :label="option.value"
                              border
                            >
                              {{ option.label }}
                            </el-radio>
                          </el-radio-group>
                        </el-form-item>
                      </el-col>
                      <el-col :xs="24" :sm="12">
                        <el-form-item :label="text.walking" prop="walkingLevel">
                          <el-radio-group v-model="form.walkingLevel">
                            <el-radio
                              v-for="option in walkingOptions"
                              :key="option.value"
                              :label="option.value"
                            >
                              {{ option.label }}
                            </el-radio>
                          </el-radio-group>
                        </el-form-item>
                      </el-col>
                    </el-row>

                    <el-row :gutter="24">
                      <el-col :xs="24" :sm="12">
                        <el-form-item :label="text.rainy" class="switch-item">
                          <el-switch v-model="form.isRainy" :active-text="text.rainyOn" :inactive-text="text.rainyOff" />
                        </el-form-item>
                      </el-col>
                      <el-col :xs="24" :sm="12">
                        <el-form-item :label="text.night" class="switch-item">
                          <el-switch v-model="form.isNight" :active-text="text.nightOn" :inactive-text="text.nightOff" />
                        </el-form-item>
                      </el-col>
                    </el-row>
                  </div>
                </template>

                <div class="form-actions">
                  <div v-if="!authState.user" class="login-reminder">{{ text.guestReminder }}</div>
                  <el-button
                    type="primary"
                    size="large"
                    class="submit-btn"
                    @click="onSubmit"
                    :loading="loading"
                  >
                    {{ text.submit }}
                  </el-button>
                </div>
              </el-form>
            </div>
          </el-col>

          <el-col :md="10" :lg="9" class="ai-panel-col">
            <component
              :is="AsyncHomeAiPanel"
              v-if="aiPanelVisible"
              :currentForm="form"
              :style="aiPanelStyle"
            />
            <div v-else class="ai-panel-placeholder" :style="aiPanelStyle">
              <div class="ai-placeholder-header">
                <span class="ai-placeholder-dot"></span>
                <span class="ai-placeholder-dot"></span>
                <span class="ai-placeholder-dot"></span>
              </div>
              <div class="ai-placeholder-body">
                <span class="ai-placeholder-line w-80"></span>
                <span class="ai-placeholder-line w-66"></span>
                <span class="ai-placeholder-line w-92"></span>
                <span class="ai-placeholder-line w-58"></span>
              </div>
            </div>
          </el-col>
        </el-row>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { reqGenerateItinerary, reqSmartFill, reqGetLatestItinerary } from '@/api/itinerary'
import { useAuthState } from '@/store/auth'
import { getDefaultTripDate, saveItinerarySnapshot } from '@/store/itinerary'
import { persistDepartureLocation, resolveCurrentLocation } from '@/utils/location'

const AsyncHomeAiPanel = defineAsyncComponent(() => import('@/components/HomeAiPanel.vue'))

const text = {
  title: '\u5B9A\u5236\u4E13\u5C5E\u884C\u7A0B',
  subtitle: '\u544A\u8BC9\u6211\u4EEC\u4F60\u7684\u65F6\u95F4\u3001\u9884\u7B97\u548C\u504F\u597D\uFF0C\u7CFB\u7EDF\u4F1A\u5E2E\u4F60\u6574\u7406\u4E00\u6761\u66F4\u987A\u8DEF\u3001\u66F4\u7701\u5FC3\u7684\u6210\u90FD\u73A9\u6CD5\u3002',
  smartFillTitle: '\u4E00\u53E5\u8BDD\u5FEB\u901F\u586B\u5199',
  smartFillCopy: '\u76F4\u63A5\u8BF4\u4F60\u60F3\u600E\u4E48\u73A9\uFF0C\u7CFB\u7EDF\u4F1A\u5148\u5E2E\u4F60\u63D0\u53D6\u5173\u952E\u4FE1\u606F\u5E76\u56DE\u586B\u8868\u5355\u3002',
  smartFillPlaceholder: '\u6BD4\u5982\uFF1A\u5468\u516D\u5728\u6210\u90FD\u73A9\u4E00\u5929\uFF0C\u9884\u7B97 300\uff0c\u548C\u5973\u670B\u53CB\u4E00\u8D77\uff0c\u522B\u592A\u7D2F\uff0c\u665A\u4E0A\u60F3\u901B\u591C\u5E02',
  smartFillAction: '\u667A\u80FD\u586B\u5199',
  smartFillTip: '\u5148\u5E2E\u4F60\u586B\u597D\u8868\u5355\uFF0C\u4E0D\u4F1A\u76F4\u63A5\u63D0\u4EA4',
  smartFillSummary: '\u5DF2\u8BC6\u522B\uFF1A',
  sectionBasic: '\u57FA\u7840\u65F6\u95F4\u5B89\u6392',
  sectionPreference: '\u504F\u597D\u8BBE\u7F6E',
  sectionAdvanced: '\u66F4\u591A\u504F\u597D',
  city: '\u76EE\u7684\u5730\u57CE\u5E02',
  cityPlaceholder: '\u9009\u62E9\u8981\u89C4\u5212\u7684\u57CE\u5E02',
  departurePlace: '\u51FA\u53D1\u5730\uFF08\u53EF\u9009\uFF09',
  departurePlacePlaceholder: '\u5982\uFF1A\u6625\u7199\u8DEF / \u4F4F\u5B85 / \u9152\u5E97\u540D\u79F0',
  tripMode: '\u51FA\u884C\u6A21\u5F0F',
  tripDate: '\u51FA\u884C\u65E5\u671F',
  tripDatePlaceholder: '\u9009\u62E9\u51FA\u884C\u65E5\u671F',
  startTime: '\u51FA\u53D1\u65F6\u95F4',
  endTime: '\u7ED3\u675F\u65F6\u95F4',
  budget: '\u9884\u7B97\u7B49\u7EA7',
  budgetPlaceholder: '\u9009\u62E9\u9884\u7B97\u7B49\u7EA7',
  themes: '\u4E3B\u9898\u504F\u597D\uFF08\u53EF\u591A\u9009\uFF09',
  companion: '\u540C\u884C\u7C7B\u578B',
  walking: '\u6B65\u884C\u5F3A\u5EA6',
  rainy: '\u662F\u5426\u9047\u5230\u96E8\u5929',
  rainyOn: '\u4F18\u5148\u5B89\u6392\u5BA4\u5185',
  rainyOff: '\u6674\u5929\u51FA\u884C',
  night: '\u662F\u5426\u4F53\u9A8C\u591C\u6E38',
  nightOn: '\u9700\u8981\u591C\u666F / \u591C\u5E02',
  nightOff: '\u508D\u665A\u7ED3\u675F',
  guestReminder: '\u4E0D\u767B\u5F55\u4E5F\u80FD\u5148\u770B\u8DEF\u7EBF\u7ED3\u679C\uFF0C\u767B\u5F55\u540E\u518D\u4FDD\u5B58\u3001\u6536\u85CF\u6216\u516C\u5F00\u5206\u4EAB\u3002',
  submit: '\u5148\u770B\u884C\u7A0B\u7ED3\u679C',
  showAdvanced: '\u5C55\u5F00\u66F4\u591A\u504F\u597D',
  hideAdvanced: '\u6536\u8D77\u66F4\u591A\u504F\u597D',
  advancedTip: '\u9884\u7B97\u3001\u540C\u884C\u4EBA\u3001\u6B65\u884C\u5F3A\u5EA6\u548C\u96E8\u5929/\u591C\u6E38\u90FD\u53EF\u4EE5\u540E\u8865',
  smartFillEmpty: '\u5148\u8F93\u5165\u4E00\u6BB5\u63CF\u8FF0\uFF0C\u6211\u518D\u5E2E\u4F60\u63D0\u53D6',
  smartFillSuccess: '\u5DF2\u6839\u636E\u63CF\u8FF0\u56DE\u586B\u8868\u5355',
  smartFillNoMatch: '\u8FD9\u6BB5\u63CF\u8FF0\u91CC\u6682\u65F6\u6CA1\u6293\u5230\u660E\u786E\u504F\u597D\uFF0C\u4F60\u53EF\u4EE5\u8BF4\u5F97\u518D\u5177\u4F53\u4E00\u70B9',
  smartFillFailed: '\u8FD9\u6BB5\u63CF\u8FF0\u6682\u65F6\u6CA1\u6709\u89E3\u6790\u6210\u529F\uFF0C\u8BF7\u7A0D\u540E\u518D\u8BD5',
  tripModeHint: '\u4E24\u65E5\u6A21\u5F0F\u4F1A\u63D0\u9AD8\u5019\u9009\u70B9\u4F4D\u4E0E\u6253\u5361\u5BC6\u5EA6\uFF0C\u5F53\u524D\u4ECD\u4EE5\u9996\u65E5\u7684\u51FA\u53D1\u65F6\u95F4\u7A97\u53E3\u751F\u6210\u4E3B\u8DEF\u7EBF\u3002',
  cityRequired: '\u8BF7\u9009\u62E9\u51FA\u53D1\u57CE\u5E02',
  dateRequired: '\u8BF7\u9009\u62E9\u51FA\u884C\u65E5\u671F',
  startRequired: '\u8BF7\u9009\u62E9\u51FA\u53D1\u65F6\u95F4',
  endRequired: '\u8BF7\u9009\u62E9\u7ED3\u675F\u65F6\u95F4',
  timeoutError: '\u884C\u7A0B\u751F\u6210\u8D85\u65F6\uFF0C\u8BF7\u7A0D\u540E\u91CD\u8BD5\u3002',
  authExpired: '\u767B\u5F55\u72B6\u6001\u5DF2\u5931\u6548\uFF0C\u8BF7\u91CD\u65B0\u767B\u5F55\u540E\u518D\u7EE7\u7EED\u89C4\u5212\u3002',
  generateFailed: '\u8DEF\u7EBF\u8FD8\u6CA1\u51C6\u5907\u597D\uFF0C\u8BF7\u7A0D\u540E\u518D\u8BD5\u3002',
  locationRequired: '\u8BF7\u5141\u8BB8\u5B9A\u4F4D\u6743\u9650\uFF0C\u6211\u4EEC\u9700\u8981\u4ECE\u4F60\u7684\u5F53\u524D\u4F4D\u7F6E\u8D77\u7B97\u9996\u6BB5\u901A\u884C\u65F6\u957F\u3002',
  locationUnavailable: '\u5B9A\u4F4D\u5931\u8D25\uFF0C\u8BF7\u68C0\u67E5\u7F51\u7EDC\u4E0E\u5B9A\u4F4D\u670D\u52A1\u540E\u91CD\u8BD5\u3002'
}

const tripDayOptions = [
  { label: '\u534A\u5929\u95F2\u901B', value: 0.5 },
  { label: '\u5168\u5929\u6E38\u73A9', value: 1.0 },
  { label: '\u4E24\u65E5\u6DF1\u5EA6', value: 2.0 }
]

const cityOptions = [
  { code: 'CD', name: '成都', label: '成都' },
  { code: 'CQ', name: '重庆', label: '重庆' },
  { code: 'BJ', name: '北京', label: '北京' },
  { code: 'SH', name: '上海', label: '上海' },
  { code: 'GZ', name: '广州', label: '广州' },
  { code: 'SZ', name: '深圳', label: '深圳' }
]

const budgetOptions = [
  { label: '\u4F4E\u9884\u7B97\uFF080~100\u5143/\u4EBA\uFF09', value: '\u4F4E' },
  { label: '\u4E2D\u9884\u7B97\uFF08100~300\u5143/\u4EBA\uFF09', value: '\u4E2D' },
  { label: '\u9AD8\u9884\u7B97\uFF08300\u5143\u4EE5\u4E0A/\u4EBA\uFF09', value: '\u9AD8' }
]

const themeOptions = [
  { label: '\u5386\u53F2\u6587\u5316', value: '\u6587\u5316' },
  { label: '\u7279\u8272\u7F8E\u98DF', value: '\u7F8E\u98DF' },
  { label: '\u81EA\u7136\u98CE\u5149', value: '\u81EA\u7136' },
  { label: '\u5546\u4E1A\u8D2D\u7269', value: '\u8D2D\u7269' },
  { label: '\u7F51\u7EA2\u6253\u5361', value: '\u7F51\u7EA2' },
  { label: '\u4F11\u95F2\u653E\u677E', value: '\u4F11\u95F2' }
]

const companionOptions = [
  { label: '\u72EC\u81EA\u6F2B\u6E38', value: '\u72EC\u81EA' },
  { label: '\u4E09\u4E94\u597D\u53CB', value: '\u670B\u53CB' },
  { label: '\u60C5\u4FA3\u7EA6\u4F1A', value: '\u60C5\u4FA3' },
  { label: '\u5BB6\u5EAD\u4EB2\u5B50', value: '\u4EB2\u5B50' }
]

const walkingOptions = [
  { label: '\u4F4E\uFF08\u5C11\u8D70\u8DEF\u3001\u591A\u4F11\u606F\uFF09', value: '\u4F4E' },
  { label: '\u4E2D\uFF08\u6B63\u5E38\u6563\u6B65\u6E38\u89C8\uFF09', value: '\u4E2D' },
  { label: '\u9AD8\uFF08\u80FD\u8D70\u3001\u63A5\u53D7\u722C\u5C71\uFF09', value: '\u9AD8' }
]

const route = useRoute()
const router = useRouter()
const authState = useAuthState()
const sectionRef = ref(null)
const formRef = ref()
const formPaneRef = ref(null)
const loading = ref(false)
const smartFillLoading = ref(false)
const panelHeight = ref(0)
const aiPanelVisible = ref(false)
const showAdvanced = ref(false)
const naturalInput = ref('')
const parsedSummary = ref([])
let formPaneObserver = null
let sectionObserver = null

const form = reactive({
  cityCode: 'CD',
  cityName: '成都',
  tripDays: 1.0,
  tripDate: getDefaultTripDate(),
  startTime: '09:00',
  endTime: '18:00',
  budgetLevel: '\u4E2D',
  themes: [],
  isRainy: false,
  isNight: true,
  walkingLevel: '\u4E2D',
  companionType: '\u670B\u53CB',
  mustVisitPoiNames: [],
  departurePlaceName: '',
  departureLatitude: null,
  departureLongitude: null
})

const rules = {
  cityCode: [{ required: true, message: text.cityRequired, trigger: 'change' }],
  tripDate: [{ required: true, message: text.dateRequired, trigger: 'change' }],
  startTime: [{ required: true, message: text.startRequired, trigger: 'change' }],
  endTime: [{ required: true, message: text.endRequired, trigger: 'change' }]
}

const syncPanelHeight = () => {
  if (typeof window === 'undefined') return
  if (window.innerWidth < 992 || !formPaneRef.value) {
    panelHeight.value = 0
    return
  }
  panelHeight.value = Math.ceil(formPaneRef.value.getBoundingClientRect().height)
}

const aiPanelStyle = computed(() => {
  return panelHeight.value ? { height: `${panelHeight.value}px` } : {}
})

const revealAiPanel = () => {
  aiPanelVisible.value = true
  if (sectionObserver) {
    sectionObserver.disconnect()
    sectionObserver = null
  }
}

const observeAiPanelEntry = () => {
  if (typeof IntersectionObserver === 'undefined' || !sectionRef.value) {
    revealAiPanel()
    return
  }

  sectionObserver = new IntersectionObserver((entries) => {
    if (!entries.some(entry => entry.isIntersecting)) return
    revealAiPanel()
  }, {
    rootMargin: '320px 0px',
    threshold: 0.05,
  })

  sectionObserver.observe(sectionRef.value)
}

watch(() => form.tripDays, (newVal) => {
  if (newVal === 0.5) {
    form.startTime = '09:00'
    form.endTime = '13:00'
  } else {
    form.startTime = '09:00'
    form.endTime = '18:00'
  }
})

watch(() => form.cityCode, (newVal) => {
  if (!newVal) return
  const matched = resolveCityByCode(newVal)
  if (form.cityName !== matched.name) {
    form.cityName = matched.name
  }
})

onMounted(() => {
  nextTick(() => {
    syncPanelHeight()
    observeAiPanelEntry()
  })
  if (typeof window !== 'undefined') {
    window.addEventListener('resize', syncPanelHeight)
  }
  if (typeof ResizeObserver !== 'undefined' && formPaneRef.value) {
    formPaneObserver = new ResizeObserver(syncPanelHeight)
    formPaneObserver.observe(formPaneRef.value)
  }
})

onBeforeUnmount(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('resize', syncPanelHeight)
  }
  if (formPaneObserver) {
    formPaneObserver.disconnect()
    formPaneObserver = null
  }
  if (sectionObserver) {
    sectionObserver.disconnect()
    sectionObserver = null
  }
})

const resolveCityByCode = (code) => {
  return cityOptions.find(option => option.code === code) || cityOptions[0]
}

const resolveCityByName = (name) => {
  if (!name) return null
  return cityOptions.find(option => option.name === name)
}

const handleCityChange = (code) => {
  const matched = resolveCityByCode(code)
  form.cityCode = matched.code
  form.cityName = matched.name
}

const onSubmit = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const selectedCity = resolveCityByCode(form.cityCode)
    const payload = {
      ...form,
      cityCode: selectedCity.code,
      cityName: selectedCity.name,
      mustVisitPoiNames: Array.isArray(form.mustVisitPoiNames) ? [...form.mustVisitPoiNames] : [],
      naturalLanguageRequirement: naturalInput.value.trim()
    }

    const currentLocation = await resolveCurrentLocation()
    if (!currentLocation) {
      ElMessage.error(text.locationRequired)
      return
    }
    payload.departureLatitude = currentLocation.latitude
    payload.departureLongitude = currentLocation.longitude
    if (!payload.departurePlaceName) {
      payload.departurePlaceName = 'CURRENT_LOCATION'
    }
    persistDepartureLocation(currentLocation, payload.departurePlaceName)

    const responseData = await reqGenerateItinerary(payload)
    saveItinerarySnapshot(responseData)
    router.push('/result')
  } catch (err) {
    if (err && err.code === 'ECONNABORTED') {
      ElMessage.warning(text.timeoutError)
      try {
        const latest = await reqGetLatestItinerary()
        if (latest) {
          saveItinerarySnapshot(latest)
          router.push('/result')
          return
        }
      } catch (_) {}
      ElMessage.error(text.timeoutError)
    } else if (err && err.code === 401) {
      ElMessage.warning(text.authExpired)
      router.push({
        path: '/auth',
        query: { redirect: route.fullPath }
      })
    } else {
      ElMessage.error(text.generateFailed)
    }
  } finally {
    loading.value = false
  }
}

const fillFormFromText = async () => {
  if (!naturalInput.value.trim()) {
    ElMessage.warning(text.smartFillEmpty)
    return
  }

  smartFillLoading.value = true
  try {
    const payload = await reqSmartFill({ text: naturalInput.value.trim() })
    const summary = Array.isArray(payload?.summary) ? payload.summary.filter(Boolean) : []
    parsedSummary.value = summary

    const hasAnyField = Boolean(
      payload &&
      (
        payload.tripDays !== undefined ||
        payload.tripDate ||
        payload.startTime ||
        payload.endTime ||
        payload.budgetLevel ||
        (Array.isArray(payload.themes) && payload.themes.length) ||
        payload.companionType ||
        payload.walkingLevel ||
        payload.isRainy !== undefined ||
        payload.isNight !== undefined ||
        (Array.isArray(payload.mustVisitPoiNames) && payload.mustVisitPoiNames.length) ||
        payload.cityName ||
        payload.departureText ||
        typeof payload.departureLatitude === 'number' ||
        typeof payload.departureLongitude === 'number'
      )
    )

    if (!hasAnyField) {
      ElMessage.warning(text.smartFillNoMatch)
      return
    }

    if (payload.tripDays !== undefined) form.tripDays = payload.tripDays
    if (payload.tripDate) form.tripDate = payload.tripDate
    if (payload.startTime) form.startTime = payload.startTime
    if (payload.endTime) form.endTime = payload.endTime
    if (payload.budgetLevel) form.budgetLevel = payload.budgetLevel
    if (Array.isArray(payload.themes)) form.themes = payload.themes
    if (payload.companionType) form.companionType = payload.companionType
    if (payload.walkingLevel) form.walkingLevel = payload.walkingLevel
    if (payload.isRainy !== undefined) form.isRainy = payload.isRainy
    if (payload.isNight !== undefined) form.isNight = payload.isNight
    if (Array.isArray(payload.mustVisitPoiNames)) form.mustVisitPoiNames = payload.mustVisitPoiNames
    if (payload.cityName) {
      const matchedCity = resolveCityByName(payload.cityName)
      if (matchedCity) {
        form.cityCode = matchedCity.code
        form.cityName = matchedCity.name
      } else {
        form.cityName = payload.cityName
      }
    }
    if (payload.departureText) form.departurePlaceName = payload.departureText
    if (typeof payload.departureLatitude === 'number') form.departureLatitude = payload.departureLatitude
    if (typeof payload.departureLongitude === 'number') form.departureLongitude = payload.departureLongitude

    if (
      payload.budgetLevel ||
      payload.companionType ||
      payload.walkingLevel ||
      payload.isRainy !== undefined ||
      payload.isNight !== undefined ||
      (Array.isArray(payload.mustVisitPoiNames) && payload.mustVisitPoiNames.length)
    ) {
      showAdvanced.value = true
    }

    ElMessage.success(text.smartFillSuccess)
  } catch (error) {
    ElMessage.error(text.smartFillFailed)
  } finally {
    smartFillLoading.value = false
  }
}

</script>

<style scoped>
.core-section {
  padding: 28px 20px 36px;
  background-color: #ffffff;
}

.core-container {
  max-width: 1200px;
  margin: 0 auto;
}

.section-header {
  margin-bottom: 18px;
}

.text-center {
  text-align: center;
}

.section-title {
  font-size: 28px;
  font-weight: 800;
  color: #1f2d3d;
  margin: 0 0 10px 0;
}

.section-subtitle {
  font-size: 14px;
  color: #606266;
  margin: 0 auto;
  max-width: 680px;
  line-height: 1.6;
}

.main-card {
  width: 100%;
  background: #ffffff;
  border-radius: 20px;
  box-shadow: 0 18px 40px rgba(31, 45, 61, 0.06);
  border: 1px solid rgba(228, 231, 237, 0.6);
  padding: 22px 24px;
  box-sizing: border-box;
}

.core-layout {
  align-items: stretch;
}

.form-col,
.ai-panel-col {
  display: flex;
  margin-top: 24px;
}

@media (min-width: 992px) {
  .ai-panel-col {
    margin-top: 0;
  }
}

.custom-form,
.form-pane,
.ai-panel-col :deep(.home-ai-panel),
.ai-panel-placeholder {
  width: 100%;
}

.ai-panel-col :deep(.home-ai-panel) {
  min-height: 0;
}

.ai-panel-placeholder {
  min-height: 100%;
  border-radius: 18px;
  border: 1px solid #e4e7ed;
  background: linear-gradient(180deg, #f8fbff 0%, #ffffff 100%);
  box-shadow: 0 16px 32px rgba(31, 45, 61, 0.04);
  padding: 18px 16px;
  box-sizing: border-box;
}

.ai-placeholder-header {
  display: flex;
  gap: 8px;
  margin-bottom: 18px;
}

.ai-placeholder-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: rgba(64, 158, 255, 0.18);
}

.ai-placeholder-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.ai-placeholder-line {
  display: block;
  height: 12px;
  border-radius: 999px;
  background: linear-gradient(90deg, rgba(64, 158, 255, 0.08), rgba(64, 158, 255, 0.18));
}

.ai-placeholder-line.w-92 { width: 92%; }
.ai-placeholder-line.w-80 { width: 80%; }
.ai-placeholder-line.w-66 { width: 66%; }
.ai-placeholder-line.w-58 { width: 58%; }

.form-section {
  margin-bottom: 10px;
}

.smart-fill-panel {
  margin-bottom: 10px;
}

.smart-fill-copy {
  margin: 0 0 12px;
  color: #627386;
  line-height: 1.7;
  font-size: 13px;
}

.smart-fill-input {
  margin-bottom: 10px;
}

.smart-fill-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.smart-fill-tip {
  color: #8a97a6;
  font-size: 12px;
  text-align: right;
}

.smart-fill-summary {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}

.smart-fill-summary-label {
  color: #6c7d90;
  font-size: 12px;
}

.sub-section-title {
  font-size: 15px;
  font-weight: 600;
  color: #475669;
  margin-bottom: 12px;
  position: relative;
  padding-left: 12px;
}

.sub-section-title::before {
  content: "";
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 4px;
  height: 16px;
  background-color: #409EFF;
  border-radius: 2px;
}

.custom-form :deep(.el-form-item__label) {
  font-weight: 500;
  color: #475669;
  padding-bottom: 4px;
  line-height: 1.35;
}

.custom-form :deep(.el-form-item) {
  margin-bottom: 14px;
}

.custom-form :deep(.el-divider--horizontal) {
  margin: 8px 0 12px;
}

.compact-row {
  margin-top: 10px;
}

.advanced-toggle-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 4px 0 8px;
  padding: 10px 0 2px;
}

.advanced-toggle-btn {
  padding: 0;
  font-weight: 600;
}

.advanced-toggle-tip {
  color: #8a97a6;
  font-size: 12px;
  text-align: right;
}

.full-width-radio {
  display: flex !important;
  width: 100%;
}

.full-width-radio :deep(.el-radio-button) {
  flex: 1;
}

.full-width-radio :deep(.el-radio-button__inner) {
  width: 100%;
}

.theme-checkbox-group :deep(.el-checkbox-button__inner) {
  border-radius: 4px !important;
  border: 1px solid #dcdfe6;
  margin-right: 8px;
  margin-bottom: 8px;
  padding: 8px 14px;
  box-shadow: none !important;
}

.theme-checkbox-group :deep(.el-checkbox-button.is-checked .el-checkbox-button__inner) {
  border-color: #409EFF;
}

.custom-form :deep(.el-radio.is-bordered) {
  margin-right: 8px;
  margin-bottom: 8px;
  margin-left: 0 !important;
  height: 38px;
  padding: 0 12px;
}

.custom-form :deep(.el-radio__label) {
  font-size: 13px;
}

.switch-item :deep(.el-form-item__content) {
  height: 34px;
  display: flex;
  align-items: center;
}

.form-actions {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.login-reminder {
  max-width: 420px;
  text-align: center;
  font-size: 13px;
  line-height: 1.6;
  color: #6c7d90;
}

.trip-mode-hint {
  margin: 6px 0 0;
  color: #6c7d90;
  font-size: 13px;
  line-height: 1.6;
}

.submit-btn {
  width: 260px;
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 24px;
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1);
  box-shadow: 0 6px 16px rgba(64, 158, 255, 0.2);
}

.submit-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(64, 158, 255, 0.3);
}

@media (max-width: 768px) {
  .main-card {
    padding: 20px 16px;
  }

  .smart-fill-actions {
    flex-direction: column;
    align-items: flex-start;
  }

  .smart-fill-tip {
    text-align: left;
  }

  .advanced-toggle-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .advanced-toggle-tip {
    text-align: left;
  }

  .submit-btn {
    width: 100%;
  }
}
</style>
