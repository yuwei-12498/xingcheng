import { describe, expect, it } from 'vitest'
import source from '../HomeRouteExamples.vue?raw'

describe('HomeRouteExamples featured community carousel', () => {
  it('uses admin selected community featured posts instead of hard-coded samples', () => {
    expect(source).toContain('reqListCommunityItineraries')
    expect(source).toContain('pinnedRecords')
    expect(source).not.toContain('communityRecords')
    expect(source).not.toContain('new Map()')
    expect(source).toContain('.slice(0, 5)')
    expect(source).toContain('<el-carousel')
    expect(source).toContain('featuredPosts.length')
    expect(source).not.toContain('cover-1')
    expect(source).not.toContain('cover-2')
  })

  it('supports automatic rotation and opens the selected community post detail', () => {
    expect(source).toContain('autoplay')
    expect(source).toContain('indicator-position')
    expect(source).toContain('openCommunityDetail')
    expect(source).toContain("router.push(`/community/${id}`)")
  })
})
