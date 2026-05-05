import { describe, expect, it } from 'vitest'
import source from '../admin/CommunityManage.vue?raw'

describe('Admin community manage carousel controls', () => {
  it('exposes explicit homepage carousel wording for governance actions', () => {
    expect(source).toContain('首页轮播')
    expect(source).toContain('轮播状态')
    expect(source).toContain('加入轮播')
    expect(source).toContain('移出轮播')
    expect(source).toContain('reqAdminCommunityPin')
  })
})
