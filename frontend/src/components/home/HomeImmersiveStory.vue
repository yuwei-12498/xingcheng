<template>
  <section id="hero" ref="storyRef" class="immersive-story" :style="storyStyleVars">
    <div class="story-sticky">
      <div class="story-stage">
        <img
          class="story-poster"
          :class="{ 'is-dimmed': animationReady }"
          :src="HERO_POSTER_SRC"
          alt=""
          fetchpriority="high"
          decoding="async"
          aria-hidden="true"
        >
        <div
          ref="lottieRef"
          class="story-lottie"
          :class="{
            'is-ready': animationReady,
            'is-disabled': !heroRuntime.allowLottie,
          }"
          aria-hidden="true"
        ></div>

        <div class="intro-mask-layer">
          <div class="story-overlay"></div>

          <div class="story-shell">
            <div class="story-copy">
              <p class="story-kicker">SCROLL TO ENTER</p>
              <transition name="fade-slide" mode="out-in">
                <article :key="activeStory.title" class="story-card">
                  <h1 class="story-title">{{ activeStory.title }}</h1>
                  <p class="story-desc">{{ activeStory.desc }}</p>
                </article>
              </transition>

              <div class="story-actions">
                <button class="story-btn primary" @click="emit('scrollTo', '#core')">开始智能规划</button>
                <button class="story-btn ghost" @click="emit('scrollTo', '#examples')">先看路线示例</button>
              </div>

              <div class="story-progress">
                <span
                  v-for="(item, idx) in stories"
                  :key="item.title"
                  class="dot"
                  :class="{ active: idx === activeIndex }"
                />
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { readHeroRuntimeProfile, scheduleIdleTask } from '@/utils/heroPerformance'

const HERO_DATA_URL = '/animations/travelnextlvl-door/animations/data.json'
const HERO_IMAGE_BASE = '/animations/travelnextlvl-door/images/'
const HERO_POSTER_SRC = '/animations/travelnextlvl-door/images/image_0.webp'

let lottieModulePromise = null
let animationDataPromise = null

const cloneAnimationData = (source) => {
  if (typeof structuredClone === 'function') {
    return structuredClone(source)
  }
  return JSON.parse(JSON.stringify(source))
}

const loadLottieModule = async () => {
  if (!lottieModulePromise) {
    lottieModulePromise = import('lottie-web/build/player/lottie_light_canvas')
  }
  const mod = await lottieModulePromise
  return mod.default || mod
}

const loadAnimationData = async () => {
  if (!animationDataPromise) {
    animationDataPromise = fetch(HERO_DATA_URL)
      .then((res) => {
        if (!res.ok) {
          throw new Error(`Animation request failed: ${res.status}`)
        }
        return res.json()
      })
      .then((data) => ({
        ...data,
        assets: Array.isArray(data.assets)
          ? data.assets.map((asset) => (asset.u ? { ...asset, u: HERO_IMAGE_BASE } : asset))
          : [],
      }))
  }

  return cloneAnimationData(await animationDataPromise)
}

const emit = defineEmits(['scrollTo', 'reveal'])

const storyRef = ref(null)
const lottieRef = ref(null)
const progress = ref(0)
const sectionProgress = ref(0)
const animationReady = ref(false)
const heroRuntime = ref({
  posterOnly: false,
  allowLottie: true,
})

// 开门起飞动画帧映射区间
const ANIMATION_START = 0.02
const ANIMATION_END = 0.97

// 临近尾声时开始把表单区渐进显示出来（提前一点，效果更明显）
const FORM_REVEAL_START = 0.72

const stories = [
  {
    title: '欢迎来到「行城有数」',
    desc: '这是一个 AI 行程规划助手：把你的时间、预算和偏好，转成真正可执行的城市路线。',
  },
  {
    title: '你只用说“想怎么玩”',
    desc: '一句话输入你的需求，我们会自动提取关键信息，补全表单并匹配更合适的玩法组合。',
  },
  {
    title: '几秒生成专属路线',
    desc: '飞机飞离后将进入规划区：直接开始填写并生成你的专属行程，不再反复查攻略。',
  },
]

const clamp = (value, min = 0, max = 1) => Math.min(Math.max(value, min), max)
const easeOutCubic = (t) => 1 - Math.pow(1 - t, 3)

const activeIndex = computed(() => {
  if (progress.value < 0.34) return 0
  if (progress.value < 0.68) return 1
  return 2
})

const activeStory = computed(() => stories[activeIndex.value])

const formRevealProgress = computed(() => {
  const raw = clamp((sectionProgress.value - FORM_REVEAL_START) / (1 - FORM_REVEAL_START))
  return easeOutCubic(raw)
})

const storyStyleVars = computed(() => ({
  '--intro-fade': formRevealProgress.value.toFixed(4),
}))

let animation = null
let rafId = null
let scrollTarget = window
let visibilityObserver = null
let cancelIdleBoot = null
let isDisposed = false

const getViewportHeight = () => {
  if (scrollTarget === window) return window.innerHeight
  return scrollTarget.clientHeight || window.innerHeight
}

const resolveScrollTarget = () => {
  const main = document.querySelector('.app-main')
  if (main instanceof HTMLElement) {
    const overflowY = window.getComputedStyle(main).overflowY
    const canScroll = /(auto|scroll|overlay)/.test(overflowY) && main.scrollHeight > main.clientHeight + 1
    if (canScroll) return main
  }
  return window
}

const syncFrame = () => {
  if (!animation) return
  const totalFrames = animation.getDuration(true) || 1
  const percent = 1 + progress.value * 98
  const frame = (percent / 100) * (totalFrames - 1)
  animation.goToAndStop(frame, true)
}

const computeProgress = () => {
  if (!storyRef.value) return

  const rect = storyRef.value.getBoundingClientRect()
  const total = storyRef.value.offsetHeight - getViewportHeight()
  if (total <= 0) {
    progress.value = 0
    sectionProgress.value = 0
    emit('reveal', 0)
    syncFrame()
    return
  }

  const scrolled = clamp(-rect.top, 0, total)
  sectionProgress.value = scrolled / total

  const normalized = (sectionProgress.value - ANIMATION_START) / (ANIMATION_END - ANIMATION_START)
  progress.value = clamp(normalized)

  emit('reveal', formRevealProgress.value)
  syncFrame()
}

const onScroll = () => {
  if (rafId) return
  rafId = requestAnimationFrame(() => {
    computeProgress()
    rafId = null
  })
}

const addScrollListeners = () => {
  scrollTarget = resolveScrollTarget()
  scrollTarget.addEventListener('scroll', onScroll, { passive: true })
  window.addEventListener('resize', onScroll, { passive: true })
}

const removeScrollListeners = () => {
  scrollTarget.removeEventListener('scroll', onScroll)
  window.removeEventListener('resize', onScroll)
}

const bootAnimation = async () => {
  if (!heroRuntime.value.allowLottie || animation || !lottieRef.value || isDisposed) return

  try {
    const [lottie, animationData] = await Promise.all([
      loadLottieModule(),
      loadAnimationData(),
    ])

    if (isDisposed || !lottieRef.value) return

    animation = lottie.loadAnimation({
      container: lottieRef.value,
      renderer: 'canvas',
      loop: false,
      autoplay: false,
      animationData,
      rendererSettings: {
        preserveAspectRatio: 'xMidYMid slice',
        clearCanvas: true,
      },
    })

    animation.addEventListener('DOMLoaded', () => {
      if (isDisposed) return
      animationReady.value = true
      computeProgress()
    })

    computeProgress()
  } catch (error) {
    console.error('Lottie load failed:', error)
  }
}

const scheduleAnimationBoot = () => {
  if (!heroRuntime.value.allowLottie || cancelIdleBoot || animation || isDisposed) return

  cancelIdleBoot = scheduleIdleTask(() => {
    cancelIdleBoot = null
    bootAnimation()
  }, { timeout: 900 })
}

const observeHeroVisibility = () => {
  if (!heroRuntime.value.allowLottie || !storyRef.value || isDisposed) return

  if (typeof IntersectionObserver === 'undefined') {
    scheduleAnimationBoot()
    return
  }

  visibilityObserver = new IntersectionObserver((entries) => {
    if (!entries.some(entry => entry.isIntersecting)) return

    visibilityObserver?.disconnect()
    visibilityObserver = null
    scheduleAnimationBoot()
  }, {
    root: scrollTarget === window ? null : scrollTarget,
    rootMargin: '220px 0px',
    threshold: 0.08,
  })

  visibilityObserver.observe(storyRef.value)
}

onMounted(async () => {
  isDisposed = false
  await nextTick()
  heroRuntime.value = readHeroRuntimeProfile()
  addScrollListeners()
  computeProgress()
  observeHeroVisibility()
})

onBeforeUnmount(() => {
  isDisposed = true
  removeScrollListeners()

  if (rafId) cancelAnimationFrame(rafId)
  if (cancelIdleBoot) cancelIdleBoot()
  visibilityObserver?.disconnect()
  visibilityObserver = null

  if (animation) {
    animation.destroy()
    animation = null
  }
})
</script>

<style scoped>
.immersive-story {
  position: relative;
  height: 165vh;
  background: #e8f1fb;
  isolation: isolate;
  z-index: 1;
  --intro-fade: 0;
}

.story-sticky {
  position: sticky;
  top: 0;
  height: 100vh;
  overflow: hidden;
  background: #d9e6f4;
  z-index: 1;
}

.story-stage {
  position: relative;
  width: 100%;
  height: 100%;
}

.story-poster {
  position: absolute;
  inset: 0;
  z-index: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center center;
  pointer-events: none;
  transition: opacity 220ms ease-out, transform 240ms ease-out;
}

.story-poster.is-dimmed {
  opacity: 0.16;
  transform: scale(1.01);
}

.intro-mask-layer {
  position: absolute;
  inset: 0;
  z-index: 2;
  opacity: calc(1 - var(--intro-fade));
  transform: translate3d(0, calc(var(--intro-fade) * -24px), 0);
  filter: blur(calc(var(--intro-fade) * 2px));
}

.story-lottie {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  z-index: 1;
  pointer-events: none;
  opacity: 0;
  transition: opacity 240ms ease-out;
}

.story-lottie.is-ready {
  opacity: 1;
}

.story-lottie.is-disabled {
  display: none;
}

.story-lottie :deep(canvas),
.story-lottie :deep(svg) {
  width: 100% !important;
  height: 100% !important;
}

.story-lottie :deep(canvas) {
  object-fit: cover;
  object-position: center top;
  transform: scale(1.52) translateY(-11%);
  transform-origin: center center;
}

.story-overlay {
  position: absolute;
  inset: 0;
  z-index: 0;
  background:
    linear-gradient(90deg, rgba(8, 26, 56, 0.38) 0%, rgba(8, 26, 56, 0.18) 42%, rgba(8, 26, 56, 0.06) 100%),
    radial-gradient(circle at 70% 10%, rgba(82, 158, 255, 0.12), transparent 45%);
  pointer-events: none;
}

.story-shell {
  position: relative;
  z-index: 1;
  height: 100%;
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  padding: 0 28px;
  pointer-events: none;
}

.story-copy {
  max-width: 560px;
}

.story-kicker {
  margin: 0 0 16px;
  font-size: 12px;
  letter-spacing: 0.2em;
  color: rgba(255, 255, 255, 0.75);
}

.story-card {
  min-height: 210px;
}

.story-title {
  margin: 0;
  font-size: 56px;
  line-height: 1.12;
  color: #fff;
}

.story-desc {
  margin: 18px 0 0;
  max-width: 52ch;
  font-size: 17px;
  line-height: 1.8;
  color: rgba(255, 255, 255, 0.84);
}

.story-actions {
  margin-top: 34px;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  pointer-events: auto;
}

.story-btn {
  border: 0;
  border-radius: 999px;
  height: 46px;
  padding: 0 22px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}

.story-btn.primary {
  color: #fff;
  background: linear-gradient(90deg, #2b85ff, #52a5ff);
}

.story-btn.ghost {
  color: #fff;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.22);
}

.story-progress {
  margin-top: 24px;
  display: flex;
  gap: 8px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.24);
  transition: all 0.25s ease;
}

.dot.active {
  width: 24px;
  border-radius: 999px;
  background: #5aa8ff;
}

.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.28s ease;
}

.fade-slide-enter-from,
.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(12px);
}

@media (max-width: 991px) {
  .immersive-story {
    height: 150vh;
  }

  .story-title {
    font-size: 40px;
  }

  .story-desc {
    font-size: 15px;
    line-height: 1.7;
  }
}

@media (max-width: 767px) {
  .immersive-story {
    height: 140vh;
  }

  .story-shell {
    align-items: flex-end;
    padding: 0 16px 28px;
  }

  .story-card {
    min-height: 170px;
  }

  .story-title {
    font-size: 30px;
  }

  .story-actions {
    margin-top: 16px;
    width: 100%;
  }

  .story-btn {
    width: calc(50% - 6px);
    min-width: 140px;
  }

  .story-lottie :deep(canvas) {
    transform: scale(1.65) translateY(-14%);
  }
}
</style>
