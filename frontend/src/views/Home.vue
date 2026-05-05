<template>
  <div class="landing-page">
    <HomeImmersiveStory @scrollTo="handleScroll" @reveal="handleReveal" />

    <div class="core-entry" :style="coreEntryStyle">
      <HomeCoreSection />
    </div>

    <HomeScenarioCards />
    <HomeFeatureSection />
    <HomeRouteExamples />
    <HomeFooterCTA @scrollTo="handleScroll" />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import Lenis from 'lenis'
import HomeImmersiveStory from '@/components/home/HomeImmersiveStory.vue'
import HomeCoreSection from '@/components/home/HomeCoreSection.vue'
import HomeScenarioCards from '@/components/home/HomeScenarioCards.vue'
import HomeFeatureSection from '@/components/home/HomeFeatureSection.vue'
import HomeRouteExamples from '@/components/home/HomeRouteExamples.vue'
import HomeFooterCTA from '@/components/home/HomeFooterCTA.vue'
import { readHeroRuntimeProfile } from '@/utils/heroPerformance'

let lenis = null
let rafId = null
const coreReveal = ref(0)
const heroRuntime = ref({
  allowSmoothScroll: true,
})

const raf = (time) => {
  if (!lenis) return
  lenis.raf(time)
  rafId = requestAnimationFrame(raf)
}

onMounted(() => {
  heroRuntime.value = readHeroRuntimeProfile()

  if (!heroRuntime.value.allowSmoothScroll) {
    return
  }

  lenis = new Lenis({
    duration: 1.2,
    easing: (t) => Math.min(1, 1.001 - Math.pow(2, -10 * t)),
    smoothWheel: true,
    wheelMultiplier: 1,
    touchMultiplier: 2,
    syncTouch: false,
    infinite: false,
  })

  rafId = requestAnimationFrame(raf)
})

onBeforeUnmount(() => {
  if (rafId) {
    cancelAnimationFrame(rafId)
    rafId = null
  }

  if (lenis) {
    lenis.destroy()
    lenis = null
  }
})

const handleScroll = (target) => {
  const selector = typeof target === 'string' ? target : target?.selector
  if (!selector) return

  const duration = target && typeof target === 'object' && typeof target.duration === 'number'
    ? target.duration
    : undefined

  const el = document.querySelector(selector)
  if (!el) return

  const y = el.getBoundingClientRect().top + window.scrollY - 80
  if (lenis) {
    lenis.scrollTo(y, { duration })
  } else {
    window.scrollTo({ top: y, behavior: 'smooth' })
  }
}

const handleReveal = (value) => {
  const n = Number(value)
  if (Number.isNaN(n)) return
  coreReveal.value = Math.min(Math.max(n, 0), 1)
}

const coreEntryStyle = computed(() => ({
  '--core-reveal': coreReveal.value.toFixed(4),
}))
</script>

<style scoped>
.landing-page {
  width: 100%;
  display: flex;
  flex-direction: column;
}

.core-entry {
  --core-reveal: 0;
  opacity: var(--core-reveal);
  transform: translate3d(0, calc((1 - var(--core-reveal)) * 38px), 0);
  transition: opacity 180ms ease-out, transform 220ms ease-out;
  will-change: opacity, transform;
}
</style>
