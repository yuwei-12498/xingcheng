import { describe, expect, it } from 'vitest'
import { buildFeaturedPosts } from '../communityFeatured'

describe('community featured carousel data', () => {
  it('keeps admin-selected posts first and fills the carousel from public records', () => {
    const result = buildFeaturedPosts({
      pinnedRecords: [
        { id: 29, title: '管理员精选路线', globalPinned: true }
      ],
      records: [
        { id: 27, title: '公开路线 A', globalPinned: false },
        { id: 28, title: '公开路线 B', globalPinned: false }
      ]
    })

    expect(result.map(item => item.id)).toEqual([29, 27, 28])
    expect(result.map(item => item.featuredLabel)).toEqual(['管理员精选', '社区推荐', '社区推荐'])
  })

  it('deduplicates pinned records when they also appear in the regular feed', () => {
    const result = buildFeaturedPosts({
      pinnedRecords: [
        { id: 1, title: '精选', globalPinned: true }
      ],
      records: [
        { id: 1, title: '精选', globalPinned: true },
        { id: 2, title: '补位', globalPinned: false }
      ]
    })

    expect(result.map(item => item.id)).toEqual([1, 2])
  })
})
