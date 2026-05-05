import { describe, expect, it } from 'vitest'
import appSource from '../../App.vue?raw'
import navbarSource from '../../components/layout/AppNavbar.vue?raw'
import chatWidgetSource from '../../components/ChatWidget.vue?raw'
import itineraryMapSource from '../../components/itinerary/ItineraryMapCard.vue?raw'
import resultSource from '../Result.vue?raw'

describe('mobile responsive style coverage', () => {
  it('adds global mobile guardrails without changing desktop defaults', () => {
    expect(appSource).toContain('@media (max-width: 768px)')
    expect(appSource).toContain('overflow-x: hidden')
    expect(appSource).toContain('.el-dialog')
    expect(appSource).toContain('calc(100vw - 24px)')
  })

  it('compresses the navbar hit area and branding on phones', () => {
    expect(navbarSource).toContain('@media (max-width: 768px)')
    expect(navbarSource).toContain('height: 60px !important')
    expect(navbarSource).toContain('.logo-badge')
    expect(navbarSource).toContain('width: 36px')
    expect(navbarSource).toContain('.user-entry')
    expect(navbarSource).toContain('min-height: 36px')
  })

  it('stacks the result page cockpit, cards, map, and timeline on phones', () => {
    expect(resultSource).toContain('@media (max-width: 768px)')
    expect(resultSource).toContain('.result-focus-grid')
    expect(resultSource).toContain('grid-template-columns: 1fr')
    expect(resultSource).toContain('.day-switcher')
    expect(resultSource).toContain('overflow-x: auto')
    expect(resultSource).toContain('@media (max-width: 480px)')
  })

  it('keeps itinerary maps usable at phone viewport sizes', () => {
    expect(itineraryMapSource).toContain('@media (max-width: 768px)')
    expect(itineraryMapSource).toContain('height: 38vh')
    expect(itineraryMapSource).toContain('.segment-strip')
    expect(itineraryMapSource).toContain('overflow-x: auto')
    expect(itineraryMapSource).toContain('map-empty')
    expect(itineraryMapSource).toContain('itinerary-map-marker')
  })

  it('turns the floating chat panel into a near-full-width phone panel', () => {
    expect(chatWidgetSource).toContain('@media (max-width: 768px)')
    expect(chatWidgetSource).toContain('left: 12px')
    expect(chatWidgetSource).toContain('right: 12px')
    expect(chatWidgetSource).toContain('height: min(70vh, 560px)')
    expect(chatWidgetSource).toContain('.msg-bubble')
  })
})
