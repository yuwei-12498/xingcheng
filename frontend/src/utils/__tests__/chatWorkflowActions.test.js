import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn()
  }
}))

vi.mock('@/api/itinerary', () => ({
  reqApplyChatReplacement: vi.fn(),
  reqApplyItineraryEdits: vi.fn(),
  reqGenerateItinerary: vi.fn(),
  reqListItineraryEditVersions: vi.fn(),
  reqRestoreChatReplacement: vi.fn(),
  reqRestoreItineraryEditVersion: vi.fn()
}))

vi.mock('@/store/chat', () => ({
  appendAssistantMessage: vi.fn(),
  askChatQuestion: vi.fn()
}))

vi.mock('@/store/itinerary', () => ({
  loadItinerarySnapshot: vi.fn(),
  normalizeItinerarySnapshot: vi.fn(value => ({ ...value })),
  saveItinerarySnapshot: vi.fn()
}))

vi.mock('@/utils/chatReplacementSession', () => ({
  getInitialReplacementVersion: vi.fn(),
  getOrCreateChatReplacementSessionId: vi.fn(() => 'session-1'),
  getPreviousReplacementVersion: vi.fn(),
  getReplacementVersionState: vi.fn(() => ({
    versions: [{ versionToken: 'v0', snapshot: { id: 9 } }],
    activeVersionIndex: 0
  })),
  markReplacementVersionActive: vi.fn(),
  pushReplacementVersion: vi.fn()
}))

import { ElMessage } from 'element-plus'
import {
  reqApplyChatReplacement,
  reqApplyItineraryEdits,
  reqGenerateItinerary,
  reqListItineraryEditVersions,
  reqRestoreChatReplacement,
  reqRestoreItineraryEditVersion
} from '@/api/itinerary'
import { appendAssistantMessage } from '@/store/chat'
import { loadItinerarySnapshot, saveItinerarySnapshot } from '@/store/itinerary'
import {
  getPreviousReplacementVersion,
  markReplacementVersionActive,
  pushReplacementVersion
} from '@/utils/chatReplacementSession'
import { handleChatWorkflowAction } from '../chatWorkflowActions'

describe('chatWorkflowActions', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('applies itinerary edit drafts through the shared edits endpoint after confirmation', async () => {
    loadItinerarySnapshot.mockReturnValue({
      id: 9,
      originalReq: { cityName: '成都' },
      activeEditVersionId: null
    })
    reqApplyItineraryEdits.mockResolvedValue({
      id: 9,
      activeEditVersionId: 22
    })
    reqListItineraryEditVersions.mockResolvedValue([
      { id: 11, versionNo: 1, active: false },
      { id: 22, versionNo: 2, active: true }
    ])

    const handled = await handleChatWorkflowAction({
      action: { key: 'confirm_itinerary_edit' },
      message: {
        meta: {
          skillPayload: {
            workflowType: 'itinerary_edit',
            itineraryEditDraft: {
              summary: '把宽窄巷子少玩 30 分钟',
              operations: [{ type: 'update_stay', nodeKey: 'node-1', stayDuration: 60 }]
            }
          }
        }
      },
      buildContext: () => ({ itinerary: { nodes: [] } })
    })

    expect(handled).toBe(true)
    expect(reqApplyItineraryEdits).toHaveBeenCalledWith({
      itineraryId: 9,
      source: 'chat',
      summary: '把宽窄巷子少玩 30 分钟',
      operations: [{ type: 'update_stay', nodeKey: 'node-1', stayDuration: 60 }]
    })
    expect(reqApplyChatReplacement).not.toHaveBeenCalled()
    expect(pushReplacementVersion).toHaveBeenCalledTimes(1)
    expect(pushReplacementVersion.mock.calls[0][0].activeEditVersionId).toBe(11)
    expect(pushReplacementVersion.mock.calls[0][1].activeEditVersionId).toBe(22)
    expect(saveItinerarySnapshot).toHaveBeenCalled()
    expect(appendAssistantMessage).toHaveBeenCalledWith(
      '已按你的确认应用本次修改，并同步刷新当前路线。',
      expect.objectContaining({
        skillPayload: expect.objectContaining({
          workflowType: 'itinerary_edit',
          workflowState: 'applied'
        })
      })
    )
    expect(ElMessage.success).toHaveBeenCalledWith('修改成功')
  })

  it('restores previous chat edit versions through the shared restore endpoint when version id is known', async () => {
    loadItinerarySnapshot.mockReturnValue({ id: 9 })
    getPreviousReplacementVersion.mockReturnValue({
      versionToken: 'v1',
      snapshot: { id: 9, activeEditVersionId: 11 }
    })
    reqRestoreItineraryEditVersion.mockResolvedValue({
      id: 9,
      activeEditVersionId: 11
    })

    const handled = await handleChatWorkflowAction({
      action: { key: 'restore_previous' },
      message: null,
      buildContext: () => ({})
    })

    expect(handled).toBe(true)
    expect(reqRestoreItineraryEditVersion).toHaveBeenCalledWith({
      itineraryId: 9,
      versionId: 11
    })
    expect(reqRestoreChatReplacement).not.toHaveBeenCalled()
    expect(markReplacementVersionActive).toHaveBeenCalledWith('v1')
    expect(ElMessage.success).toHaveBeenCalledWith('已回退上一版')
  })

  it('generates a new itinerary from confirmed chat summary draft', async () => {
    const draft = {
      cityName: '成都',
      days: 1,
      preferences: ['慢节奏']
    }
    reqGenerateItinerary.mockResolvedValue({
      id: 88,
      originalReq: draft,
      nodes: [{ poiName: '宽窄巷子' }]
    })

    const handled = await handleChatWorkflowAction({
      action: { key: 'confirm_itinerary_generate' },
      message: {
        meta: {
          skillPayload: {
            generateDraft: draft
          }
        }
      },
      buildContext: () => ({})
    })

    expect(handled).toBe(true)
    expect(reqGenerateItinerary).toHaveBeenCalledWith(draft)
    expect(saveItinerarySnapshot).toHaveBeenCalledWith(expect.objectContaining({ id: 88 }))
    expect(appendAssistantMessage).toHaveBeenCalledWith(
      '已按这次对话总结生成新路线，并同步刷新结果页。',
      expect.objectContaining({
        skillPayload: expect.objectContaining({
          workflowType: 'itinerary_generate',
          workflowState: 'applied'
        })
      })
    )
    expect(ElMessage.success).toHaveBeenCalledWith('已生成新路线')
  })

  it('keeps collecting preferences when user continues itinerary generation', async () => {
    const handled = await handleChatWorkflowAction({
      action: { key: 'continue_itinerary_generate' },
      message: null,
      buildContext: () => ({})
    })

    expect(handled).toBe(true)
    expect(reqGenerateItinerary).not.toHaveBeenCalled()
    expect(appendAssistantMessage).toHaveBeenCalledWith(
      '好的，你可以继续补充偏好；说完后我再帮你整理并生成路线。'
    )
  })
})
