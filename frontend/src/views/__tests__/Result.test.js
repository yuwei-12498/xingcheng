import { describe, expect, it } from 'vitest'
import resultSource from '../Result.vue?raw'

describe('Result view advanced layout', () => {
  it('keeps the cockpit hero layout and focus grid instead of the fallback stacked cards', () => {
    expect(resultSource).toContain('<aside class="hero-side">')
    expect(resultSource).toContain('class="hero-pill-row"')
    expect(resultSource).toContain('class="result-focus-grid"')
    expect(resultSource).toContain('class="timeline-panel"')
  })

  it('does not render the route option comparison panel on the result page', () => {
    expect(resultSource).not.toContain('class="option-panel"')
    expect(resultSource).not.toContain('class="option-grid"')
    expect(resultSource).not.toContain('class="option-card"')
    expect(resultSource).not.toContain('showOptionPanel')
    expect(resultSource).not.toContain('handleSelectOption')
  })

  it('moves recommendation out of the hero side and into the left map column filler area', () => {
    expect(resultSource).not.toContain('<p class="hero-side-copy">{{ heroRecommendation }}</p>')
    expect(resultSource).not.toContain('<p class="hero-side-label">推荐理由</p>')
    expect(resultSource).toContain('class="result-map-column"')
    expect(resultSource).toContain('class="map-recommendation-card"')
    expect(resultSource).toContain('{{ heroRecommendation }}')
    expect(resultSource).toContain(':community-status-text="shareStatusText"')
    expect(resultSource).not.toContain('heroFootnote')
    expect(resultSource).not.toContain('<section class="share-status-panel">')
    expect(resultSource).not.toContain('shareStatusBadgeText')
  })

  it('removes fake poi warm tips and nearby services from stop cards', () => {
    expect(resultSource).not.toContain('class="stop-tip-box"')
    expect(resultSource).not.toContain('AI 温馨提示')
    expect(resultSource).not.toContain('class="nearby-box"')
    expect(resultSource).not.toContain('class="nearby-row"')
  })

  it('supports multi-day navigation so users can switch displayed route fragments', () => {
    expect(resultSource).toContain('class="day-switcher"')
    expect(resultSource).toContain('v-if="dayPlans.length > 1"')
    expect(resultSource).toContain('@click="goNextDay"')
    expect(resultSource).toContain('v-for="plan in dayPlans"')
    expect(resultSource).toContain('displayNodes')
    expect(resultSource).toContain('buildDayPlans')
  })

  it('builds the hero copy from shared result utilities instead of hard-coded strings', () => {
    expect(resultSource).toContain('buildResultHeroContent')
    expect(resultSource).toContain('resultActionGroups')
    expect(resultSource).toContain('statItems')
  })

  it('animates the map stage when users switch itinerary days', () => {
    expect(resultSource).toContain('class="map-motion-stage"')
    expect(resultSource).toContain(':key="mapTransitionKey"')
    expect(resultSource).toContain('isMapSwitching')
    expect(resultSource).toContain('mapMotionStage')
    expect(resultSource).toContain('watch(activeDayIndex')
    expect(resultSource).toContain('requestAnimationFrame')
    expect(resultSource).toContain('setTimeout(')
  })

  it('links map segment hover and click states back to the timeline panel', () => {
    expect(resultSource).toContain('@segment-hover="handleMapSegmentHover"')
    expect(resultSource).toContain('@segment-leave="handleMapSegmentLeave"')
    expect(resultSource).toContain('@segment-pin="handleMapSegmentPin"')
    expect(resultSource).toContain('activeTimelineSegmentIndex')
    expect(resultSource).toContain('buildTimelineSegmentClass')
    expect(resultSource).toContain('timelineNodeRefs')
    expect(resultSource).toContain('scrollIntoView')
  })

  it('passes departure coordinates into the map card and exposes model travel analysis inside stop cards', () => {
    expect(resultSource).toContain(':departure-point="departurePoint"')
    expect(resultSource).toContain('const departurePoint = computed(() =>')
    expect(resultSource).toContain('node.travelNarrative')
    expect(resultSource).toContain('class="travel-analysis-box"')
  })

  it('treats segment 0 as current-location to first-stop and shifts later segments to previous/current nodes', () => {
    expect(resultSource).toContain('return defaultTimelineSegmentIndex.value')
    expect(resultSource).toContain('if (segmentIndex === 0)')
    expect(resultSource).toContain('return nodeIndex === 0')
    expect(resultSource).toContain('return nodeIndex === segmentIndex - 1 || nodeIndex === segmentIndex')
  })

  it('keeps per-stop segment summaries but removes inline how-to navigation toggles', () => {
    expect(resultSource).toContain("import SegmentRouteGuideCard from '@/components/itinerary/SegmentRouteGuideCard.vue'")
    expect(resultSource).toContain('<SegmentRouteGuideCard')
    expect(resultSource).toContain(':guide="node.segmentRouteGuide"')
    expect(resultSource).toContain('buildSegmentGuideFromName(nodeIndex)')
    expect(resultSource).toContain(':linked="activeTimelineSegmentIndex === nodeIndex"')
    expect(resultSource).not.toContain('expandedRouteGuideIndex')
    expect(resultSource).not.toContain('@toggle="handleToggleRouteGuide"')
    expect(resultSource).not.toContain('@focus-segment="handleFocusRouteGuideSegment"')
    expect(resultSource).not.toContain('handleToggleRouteGuide')
    expect(resultSource).not.toContain('handleFocusRouteGuideSegment')
  })

  it('syncs the result page when chat replacement updates the itinerary snapshot', () => {
    expect(resultSource).toContain('citytrip:itinerary-updated')
    expect(resultSource).toContain('window.addEventListener')
    expect(resultSource).toContain('window.removeEventListener')
  })

  it('mounts the itinerary edit dialog and exposes an edit entry for result-page adjustments', () => {
    expect(resultSource).toContain("import ItineraryEditDialog from '@/components/itinerary/ItineraryEditDialog.vue'")
    expect(resultSource).toContain('编辑路线')
    expect(resultSource).toContain('<ItineraryEditDialog')
    expect(resultSource).toContain('@applied="handleItineraryEdited"')
  })
})
