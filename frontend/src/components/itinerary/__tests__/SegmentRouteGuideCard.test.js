import { describe, expect, it } from 'vitest'
import segmentGuideCardSource from '../SegmentRouteGuideCard.vue?raw'

describe('SegmentRouteGuideCard component', () => {
  it('renders a compact segment summary card without how-to navigation controls', () => {
    expect(segmentGuideCardSource).toContain('class="segment-guide-card"')
    expect(segmentGuideCardSource).toContain('class="guide-summary-row"')
    expect(segmentGuideCardSource).toContain('class="guide-summary-main"')
    expect(segmentGuideCardSource).not.toContain("$emit('focus-segment', segmentIndex)")
    expect(segmentGuideCardSource).not.toContain("$emit('toggle', segmentIndex)")
    expect(segmentGuideCardSource).not.toContain('定位地图')
    expect(segmentGuideCardSource).not.toContain('查看怎么走')
    expect(segmentGuideCardSource).not.toContain('收起导航')
    expect(segmentGuideCardSource).not.toContain('详情不完整')
  })

  it('uses shared helper functions for title and summary without fabricating guide data', () => {
    expect(segmentGuideCardSource).toContain("import { buildSegmentGuideTitle, formatSegmentGuideSummary } from '@/utils/resultUi'")
    expect(segmentGuideCardSource).toContain('buildSegmentGuideTitle({')
    expect(segmentGuideCardSource).toContain('formatSegmentGuideSummary(props.guide)')
    expect(segmentGuideCardSource).toContain('props.guide?.transportMode')
  })

  it('does not render fallback mini maps or step-by-step detail panes anymore', () => {
    expect(segmentGuideCardSource).not.toContain('class="guide-detail-grid"')
    expect(segmentGuideCardSource).not.toContain('class="guide-detail-copy"')
    expect(segmentGuideCardSource).not.toContain('class="guide-step-list"')
    expect(segmentGuideCardSource).not.toContain('<SegmentMiniMap')
    expect(segmentGuideCardSource).not.toContain('detailNotice')
    expect(segmentGuideCardSource).not.toContain('parts.join(')
  })
})
