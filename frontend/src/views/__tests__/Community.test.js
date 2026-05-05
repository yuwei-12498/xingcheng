import { describe, expect, it } from 'vitest'
import communitySource from '../Community.vue?raw'

describe('Community view recovery', () => {
  it('shows a dedicated empty-state recovery card instead of a bare empty placeholder', () => {
    expect(communitySource).toContain('class="empty-community-card state-card"')
    expect(communitySource).toContain('当前社区里还没有公开路线帖')
    expect(communitySource).toContain('去历史记录里发布')
    expect(communitySource).toContain('先生成一条新路线')
    expect(communitySource).toContain('handleOpenHistory')
    expect(communitySource).toContain('description="暂时没有符合当前筛选条件的路线帖')
  })
})
