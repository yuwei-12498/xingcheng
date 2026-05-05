import { ElMessage } from 'element-plus'
import {
  reqApplyChatReplacement,
  reqApplyItineraryEdits,
  reqGenerateItinerary,
  reqListItineraryEditVersions,
  reqRestoreChatReplacement,
  reqRestoreItineraryEditVersion
} from '@/api/itinerary'
import { appendAssistantMessage, askChatQuestion } from '@/store/chat'
import { loadItinerarySnapshot, normalizeItinerarySnapshot, saveItinerarySnapshot } from '@/store/itinerary'
import {
  getInitialReplacementVersion,
  getOrCreateChatReplacementSessionId,
  getPreviousReplacementVersion,
  getReplacementVersionState,
  markReplacementVersionActive,
  pushReplacementVersion
} from '@/utils/chatReplacementSession'

const WORKFLOW_TYPE = 'itinerary_replace'
const GENERATE_WORKFLOW_TYPE = 'itinerary_generate'

const buildHistoryActions = () => {
  const state = getReplacementVersionState()
  const activeVersion = state.versions[state.activeVersionIndex] || null
  const initialVersion = state.versions[0] || null
  const actions = []

  if (state.activeVersionIndex > 0) {
    actions.push({
      key: 'restore_previous',
      label: '回退上一版',
      style: 'secondary'
    })
  }

  if (activeVersion && initialVersion && activeVersion.versionToken !== initialVersion.versionToken) {
    actions.push({
      key: 'restore_initial',
      label: '恢复初版',
      style: 'ghost'
    })
  }

  return actions
}

const buildWorkflowMeta = (workflowState, workflowType = WORKFLOW_TYPE) => ({
  skillPayload: {
    skillName: workflowType,
    messageType: 'workflow',
    workflowType,
    workflowState,
    source: 'local-workflow',
    actions: buildHistoryActions()
  }
})

const buildGenerateWorkflowMeta = workflowState => ({
  skillPayload: {
    skillName: GENERATE_WORKFLOW_TYPE,
    messageType: 'workflow',
    workflowType: GENERATE_WORKFLOW_TYPE,
    workflowState,
    source: 'local-workflow',
    actions: []
  }
})

const restoreTargetByAction = actionKey => {
  if (actionKey === 'restore_previous') {
    return getPreviousReplacementVersion()
  }
  if (actionKey === 'restore_initial') {
    return getInitialReplacementVersion()
  }
  return null
}

const resolveWorkflowType = skillPayload => skillPayload?.workflowType || WORKFLOW_TYPE

const syncEditVersionIds = async (previousSnapshot, nextSnapshot) => {
  const itineraryId = nextSnapshot?.id || previousSnapshot?.id
  if (!itineraryId) return

  const versions = await reqListItineraryEditVersions(itineraryId).catch(() => [])
  if (!Array.isArray(versions) || versions.length === 0) {
    return
  }

  if (previousSnapshot && !previousSnapshot.activeEditVersionId) {
    previousSnapshot.activeEditVersionId = versions[0]?.id || null
  }

  if (nextSnapshot && !nextSnapshot.activeEditVersionId) {
    const activeVersion = versions.find(item => item?.active) || versions[versions.length - 1]
    nextSnapshot.activeEditVersionId = activeVersion?.id || null
  }
}

const applyReplacement = async ({ message, buildContext }) => {
  const skillPayload = message?.meta?.skillPayload || {}
  const snapshot = loadItinerarySnapshot()
  if (!snapshot?.id) {
    throw new Error('当前没有可应用的行程，请先生成路线。')
  }

  const draft = skillPayload?.itineraryEditDraft
  if (Array.isArray(draft?.operations) && draft.operations.length > 0) {
    const nextSnapshotRaw = await reqApplyItineraryEdits({
      itineraryId: snapshot.id,
      source: 'chat',
      summary: draft.summary,
      operations: draft.operations
    })

    const previousSnapshot = normalizeItinerarySnapshot(snapshot)
    const nextSnapshot = normalizeItinerarySnapshot(nextSnapshotRaw)
    await syncEditVersionIds(previousSnapshot, nextSnapshot)
    saveItinerarySnapshot(nextSnapshot)
    pushReplacementVersion(previousSnapshot, nextSnapshot)

    appendAssistantMessage(
      '已按你的确认应用本次修改，并同步刷新当前路线。',
      {
        skillPayload: {
          skillName: resolveWorkflowType(skillPayload),
          messageType: 'workflow',
          workflowType: resolveWorkflowType(skillPayload),
          workflowState: 'applied',
          source: 'local-workflow',
          actions: buildHistoryActions()
        }
      }
    )
    ElMessage.success('修改成功')
    return true
  }

  const context = buildContext()
  const currentNodes = Array.isArray(context?.itinerary?.nodes) ? context.itinerary.nodes : []
  if (!currentNodes.length) {
    throw new Error('当前行程节点为空，暂时无法替换。')
  }

  const clientSessionId = skillPayload.clientSessionId || getOrCreateChatReplacementSessionId()
  const proposalToken = skillPayload.proposalToken
  if (!proposalToken) {
    throw new Error('当前替换提案已失效，请重新发起替换。')
  }

  const nextSnapshotRaw = await reqApplyChatReplacement({
    itineraryId: snapshot.id,
    clientSessionId,
    proposalToken,
    currentNodes,
    originalReq: snapshot.originalReq || null
  })

  const nextSnapshot = normalizeItinerarySnapshot(nextSnapshotRaw)
  saveItinerarySnapshot(nextSnapshot)
  pushReplacementVersion(snapshot, nextSnapshot)

  appendAssistantMessage(
    '已按你的确认替换行程节点，并同步刷新当前路线。',
    buildWorkflowMeta('applied', resolveWorkflowType(skillPayload))
  )
  ElMessage.success('替换成功')
  return true
}

const regenerateReplacement = async ({ message, buildContext }) => {
  const skillPayload = message?.meta?.skillPayload || {}
  const clientSessionId = skillPayload.clientSessionId || getOrCreateChatReplacementSessionId()
  const proposalToken = skillPayload.proposalToken
  if (!proposalToken) {
    throw new Error('当前替换提案已失效，请重新生成。')
  }

  await askChatQuestion('换一批', buildContext(), {
    type: 'regenerate_replacement',
    proposalToken,
    clientSessionId
  })
  return true
}

const declineReplacement = () => {
  appendAssistantMessage('好的，先保留当前路线；如果你还想换别的站点，随时告诉我。')
  return true
}

const applyItineraryGenerate = async ({ message }) => {
  const draft = message?.meta?.skillPayload?.generateDraft
  if (!draft || typeof draft !== 'object' || Array.isArray(draft)) {
    throw new Error('当前没有可生成路线的需求摘要，请继续补充你的路线偏好。')
  }

  const nextSnapshotRaw = await reqGenerateItinerary(draft)
  const nextSnapshot = normalizeItinerarySnapshot(nextSnapshotRaw)
  saveItinerarySnapshot(nextSnapshot)

  appendAssistantMessage(
    '已按这次对话总结生成新路线，并同步刷新结果页。',
    buildGenerateWorkflowMeta('applied')
  )
  ElMessage.success('已生成新路线')
  return true
}

const continueItineraryGenerate = () => {
  appendAssistantMessage('好的，你可以继续补充偏好；说完后我再帮你整理并生成路线。')
  return true
}

const restoreReplacement = async (actionKey, message) => {
  const target = restoreTargetByAction(actionKey)
  if (!target?.snapshot) {
    throw new Error('没有可恢复的历史版本。')
  }

  const currentSnapshot = loadItinerarySnapshot()
  const itineraryId = currentSnapshot?.id || target.snapshot?.id
  if (!itineraryId) {
    throw new Error('当前缺少有效的行程标识，无法恢复版本。')
  }

  let restoredRaw
  if (target.snapshot?.activeEditVersionId) {
    restoredRaw = await reqRestoreItineraryEditVersion({
      itineraryId,
      versionId: target.snapshot.activeEditVersionId
    })
  } else {
    restoredRaw = await reqRestoreChatReplacement({
      itineraryId,
      versionToken: target.versionToken,
      itinerarySnapshot: target.snapshot
    })
  }

  const restoredSnapshot = normalizeItinerarySnapshot(restoredRaw)
  saveItinerarySnapshot(restoredSnapshot)
  markReplacementVersionActive(target.versionToken)

  appendAssistantMessage(
    actionKey === 'restore_previous' ? '已回退到上一版行程。' : '已恢复到最初版本。',
    buildWorkflowMeta('restored', resolveWorkflowType(message?.meta?.skillPayload))
  )
  ElMessage.success(actionKey === 'restore_previous' ? '已回退上一版' : '已恢复初版')
  return true
}

export async function handleChatWorkflowAction({ action, message, buildContext }) {
  const actionKey = action?.key || action?.type || ''
  if (!actionKey) {
    return false
  }

  if (actionKey === 'confirm_replacement' || actionKey === 'confirm_itinerary_edit') {
    return applyReplacement({ message, buildContext })
  }
  if (actionKey === 'confirm_itinerary_generate') {
    return applyItineraryGenerate({ message })
  }
  if (actionKey === 'continue_itinerary_generate') {
    return continueItineraryGenerate()
  }
  if (actionKey === 'regenerate_replacement') {
    return regenerateReplacement({ message, buildContext })
  }
  if (actionKey === 'decline_replacement' || actionKey === 'decline_itinerary_edit') {
    return declineReplacement()
  }
  if (actionKey === 'restore_previous' || actionKey === 'restore_initial') {
    return restoreReplacement(actionKey, message)
  }
  return false
}
