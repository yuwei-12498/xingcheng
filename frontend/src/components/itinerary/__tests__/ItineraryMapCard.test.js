import { describe, expect, it } from 'vitest'
import mapCardSource from '../ItineraryMapCard.vue?raw'

describe('ItineraryMapCard advanced cockpit', () => {
  it('cleans unknown placeholders and keeps departure copy anchored to current location', () => {
    expect(mapCardSource).toContain('当前位置')
    expect(mapCardSource).not.toContain('????')
    expect(mapCardSource).not.toContain('先从空间分布判断顺路程度')
  })

  it('renders community sharing status as plain text in the lower blank area', () => {
    expect(mapCardSource).toContain('communityStatusText')
    expect(mapCardSource).toContain('class="community-status-copy"')
    expect(mapCardSource).toContain('{{ communityStatusText }}')
    expect(mapCardSource).not.toContain('社区分享状态：')
  })

  it('keeps insight panels below the map while removing floating overview and legend cards', () => {
    expect(mapCardSource).not.toContain('class="overview-panel"')
    expect(mapCardSource).not.toContain('class="legend-panel"')
    expect(mapCardSource).toContain('class="insight-grid"')
    expect(mapCardSource).toContain('mapInsightItems')
    expect(mapCardSource).toContain('mapNarrative')
  })

  it('keeps the route canvas dominant without floating overview or legend overlays', () => {
    expect(mapCardSource).not.toContain('legend-panel floating-panel legend-panel--compact')
    expect(mapCardSource).not.toContain('overview-panel floating-panel overview-panel--compact')
    expect(mapCardSource).not.toContain('class="legend-segment-row"')
    expect(mapCardSource).toContain('height: 560px')
  })

  it('renders enhanced route drawing layers and upgraded markers', () => {
    expect(mapCardSource).toContain('new AMap.Polyline')
    expect(mapCardSource).toContain("strokeColor: '#5f9eff'")
    expect(mapCardSource).toContain('buildAmapPoiMarkerContent')
    expect(mapCardSource).toContain("anchor: 'center'")
  })


  it('segments the route and makes start/end/external POIs visually explicit', () => {
    expect(mapCardSource).toContain('const segmentMeta = computed(() =>')
    expect(mapCardSource).toContain('attachAmapSegmentEvents')
    expect(mapCardSource).toContain('active-segment')
    expect(mapCardSource).toContain('muted-segment')
    expect(mapCardSource).toContain('start-marker')
    expect(mapCardSource).toContain('end-marker')
    expect(mapCardSource).toContain('external-marker')
    expect(mapCardSource).toContain("node.sourceType === 'external'")
    expect(mapCardSource).toContain('stop-mini-tag.external')
    expect(mapCardSource).toContain('data-visual-role')
  })

  it('emits segment interaction events and upgrades day switching into camera fly + route flow animation', () => {
    expect(mapCardSource).toContain('defineEmits([')
    expect(mapCardSource).toContain("'segment-hover'")
    expect(mapCardSource).toContain("'segment-leave'")
    expect(mapCardSource).toContain("'segment-pin'")
    expect(mapCardSource).toContain("overlay.on?.('mouseover'")
    expect(mapCardSource).toContain("overlay.on?.('mouseout'")
    expect(mapCardSource).toContain("overlay.on?.('click'")
    expect(mapCardSource).toContain("className: 'route-flow-line'")
    expect(mapCardSource).toContain('amapMap.setFitView')
    expect(mapCardSource).toContain('motionToken')
  })

  it('uses departure point and per-node route geometry instead of only joining poi coordinates with straight lines', () => {
    expect(mapCardSource).toContain('departurePoint')
    expect(mapCardSource).toContain('normalizedDeparturePoint')
    expect(mapCardSource).toContain('routePathPoints')
    expect(mapCardSource).toContain('pathPoints')
    expect(mapCardSource).toContain('resolveSegmentPathPoints')
    expect(mapCardSource).toContain('pathAnchorsMatchSegment')
    expect(mapCardSource).toContain('current-location-marker')
    expect(mapCardSource).toContain('departure-marker')
  })

  it('keeps the map stable after layout changes by observing resize and using asymmetric fit padding', () => {
    expect(mapCardSource).toContain('ResizeObserver')
    expect(mapCardSource).toContain('ensureMapResizeObserver')
    expect(mapCardSource).toContain('amapMap.resize')
    expect(mapCardSource).toContain('amapMap.setFitView')
  })

  it('keeps legend and departure marker labels readable for current / first / last / external roles', () => {
    expect(mapCardSource).toContain('当前位置')
    expect(mapCardSource).toContain('首站')
    expect(mapCardSource).toContain('末站')
    expect(mapCardSource).toContain('外部 POI')
    expect(mapCardSource).not.toContain('<small>??</small>')
    expect(mapCardSource).not.toContain('<i>??</i>')
  })
  it('uses AMap JS API v2 and no longer falls back to Leaflet/OpenStreetMap', () => {
    expect(mapCardSource).toContain('ensureAmapJsApiLoaded')
    expect(mapCardSource).toContain('isAmapJsConfigured')
    expect(mapCardSource).toContain('new AMap.Map')
    expect(mapCardSource).toContain('const mapStyle = getAmapMapStyle()')
    expect(mapCardSource).toContain('mapStyle')
    expect(mapCardSource).toContain('AMap JS API unavailable.')
    expect(mapCardSource).not.toContain('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png')
    expect(mapCardSource).not.toContain("from 'leaflet'")
  })
})
