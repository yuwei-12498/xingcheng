import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/chat', () => ({
  reqStreamChat: vi.fn()
}))

import { reqStreamChat } from '@/api/chat'
import {
  askChatQuestion,
  clearActiveChatState,
  resetChatState,
  useChatState
} from '../chat'

describe('chat store skill payload state', () => {
  beforeEach(() => {
    window.sessionStorage.clear()
    window.localStorage.clear()
    clearActiveChatState()
    resetChatState()
    reqStreamChat.mockReset()
  })

  it('stores the structured skill payload returned by stream meta', async () => {
    const payload = {
      skillName: 'nearby_hotel',
      status: 'ok',
      source: 'vivo-geo',
      results: [
        { name: '????', address: '?????', category: '??' }
      ]
    }

    reqStreamChat.mockImplementation(async (_req, handlers) => {
      handlers.onMeta({
        relatedTips: ['?????'],
        evidence: ['source=vivo-geo'],
        skillPayload: payload
      })
      return {
        answer: '??? 1 ??????',
        relatedTips: ['?????'],
        evidence: ['source=vivo-geo'],
        skillPayload: payload
      }
    })

    await askChatQuestion('??????????', { cityName: '??' })

    expect(useChatState().currentSkillPayload).toEqual(payload)
    expect(useChatState().currentSkillPayload.results[0].name).toBe('????')
  })

  it('keeps clarification payload even when the result list is empty', async () => {
    const payload = {
      skillName: 'nearby_hotel',
      status: 'clarification_required',
      source: 'vivo-geo',
      fallbackMessage: '?????? IFS???????????',
      results: []
    }

    reqStreamChat.mockImplementation(async (_req, handlers) => {
      handlers.onMeta({
        relatedTips: ['????????'],
        evidence: ['skill:nearby_hotel'],
        skillPayload: payload
      })
      return {
        answer: '?????? IFS?',
        relatedTips: ['????????'],
        evidence: ['skill:nearby_hotel'],
        skillPayload: payload
      }
    })

    await askChatQuestion('?? IFS ????', { cityName: '??' })

    expect(useChatState().currentSkillPayload).toEqual(payload)
    expect(useChatState().currentSkillPayload.status).toBe('clarification_required')
    expect(useChatState().currentSkillPayload.results).toEqual([])
  })

  it('falls back to the final result skill payload when stream meta is absent', async () => {
    const payload = {
      skillName: 'poi_search',
      status: 'ok',
      source: 'vivo-geo',
      results: [
        { name: '????', address: '???????', category: '??' }
      ]
    }

    reqStreamChat.mockResolvedValue({
      answer: '?????????',
      relatedTips: ['??????'],
      evidence: ['source=vivo-geo'],
      skillPayload: payload
    })

    await askChatQuestion('???????', { cityName: '??' })

    expect(useChatState().currentSkillPayload).toEqual(payload)
    expect(useChatState().currentSkillPayload.skillName).toBe('poi_search')
  })

  it('does not show raw model gateway errors to users', async () => {
    reqStreamChat.mockRejectedValue(new Error('Model request failed. model=mimo-v2-omni, endpoint=https://token-plan-cn.xiaomimimo.com/v1/chat/completions, reason=OpenAI message content is empty'))

    await expect(askChatQuestion('加入IFS就行了', { cityName: '成都' })).rejects.toThrow()

    const messages = useChatState().messages
    const assistant = messages[messages.length - 1]
    expect(assistant.role).toBe('assistant')
    expect(assistant.content).toContain('模型没有返回有效内容')
    expect(assistant.content).not.toContain('endpoint=')
    expect(assistant.content).not.toContain('mimo-v2-omni')
  })

  it('attaches workflow meta and action buttons to the assistant message', async () => {
    const payload = {
      skillName: 'itinerary_replace',
      status: 'ok',
      messageType: 'workflow',
      workflowType: 'itinerary_replace',
      workflowState: 'proposal_ready',
      clientSessionId: 'session-1',
      proposalToken: 'proposal-1',
      actions: [
        { key: 'confirm_replacement', label: '????' },
        { key: 'regenerate_replacement', label: '????' }
      ],
      results: [
        { name: '????', address: '??????', category: '??' }
      ]
    }

    reqStreamChat.mockImplementation(async (_req, handlers) => {
      handlers.onMeta({
        relatedTips: ['????'],
        evidence: ['skill:itinerary_replace'],
        skillPayload: payload
      })
      return {
        answer: '????????????????????????',
        relatedTips: ['????'],
        evidence: ['skill:itinerary_replace'],
        skillPayload: payload
      }
    })

    await askChatQuestion('???????????', { cityName: '??' }, { type: 'confirm_replacement' })

    const messages = useChatState().messages
    const assistant = messages[messages.length - 1]
    expect(assistant.role).toBe('assistant')
    expect(assistant.meta.skillPayload).toEqual(payload)
    expect(assistant.meta.actions.map(item => item.key)).toEqual(['confirm_replacement', 'regenerate_replacement'])
    expect(reqStreamChat).toHaveBeenCalledWith(
      expect.objectContaining({
        action: expect.objectContaining({ type: 'confirm_replacement' })
      }),
      expect.any(Object)
    )
  })
  it('sends recent chat turns with the current question', async () => {
    reqStreamChat.mockResolvedValue({
      answer: '可以，我先记下你的偏好。',
      relatedTips: [],
      evidence: [],
      skillPayload: null
    })

    await askChatQuestion('我喜欢美食和轻松一点', { pageType: 'result', cityName: '成都' })
    await askChatQuestion('就按刚才说的生成路线', { pageType: 'result', cityName: '成都' })

    const secondRequest = reqStreamChat.mock.calls[1][0]
    const recentText = secondRequest.recentMessages.map(item => item.content).join('\n')
    expect(recentText).toContain('我喜欢美食和轻松一点')
    expect(recentText).toContain('就按刚才说的生成路线')
  })
})
