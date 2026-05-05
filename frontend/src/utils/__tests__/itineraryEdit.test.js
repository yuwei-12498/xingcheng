import { describe, expect, it } from 'vitest'
import {
  buildItineraryEditPayload,
  deriveEditableDayWindows,
  deriveEditableStops
} from '../itineraryEdit'

describe('itineraryEdit utils', () => {
  const snapshot = {
    originalReq: {
      startTime: '09:00',
      endTime: '18:00'
    },
    dayWindows: [
      { dayNo: 1, startTime: '09:00', endTime: '18:00' },
      { dayNo: 2, startTime: '09:00', endTime: '18:00' }
    ],
    nodes: [
      { nodeKey: 'node-a', dayNo: 1, stepOrder: 1, poiName: '武侯祠', stayDuration: 90 },
      { nodeKey: 'node-b', dayNo: 1, stepOrder: 2, poiName: '锦里', stayDuration: 80 },
      { nodeKey: 'node-c', dayNo: 2, stepOrder: 1, poiName: '杜甫草堂', stayDuration: 70 }
    ]
  }

  it('derives editable windows and stop rows from itinerary snapshot', () => {
    expect(deriveEditableDayWindows(snapshot)).toEqual([
      { dayNo: 1, startTime: '09:00', endTime: '18:00' },
      { dayNo: 2, startTime: '09:00', endTime: '18:00' }
    ])
    expect(deriveEditableStops(snapshot)).toEqual([
      { nodeKey: 'node-a', poiName: '武侯祠', dayNo: 1, order: 1, stayDuration: 90, removed: false },
      { nodeKey: 'node-b', poiName: '锦里', dayNo: 1, order: 2, stayDuration: 80, removed: false },
      { nodeKey: 'node-c', poiName: '杜甫草堂', dayNo: 2, order: 1, stayDuration: 70, removed: false }
    ])
  })

  it('builds ordered edit operations for windows, stay changes, moves, removals and inline custom pois', () => {
    const payload = buildItineraryEditPayload({
      originalDayWindows: deriveEditableDayWindows(snapshot),
      dayWindows: [
        { dayNo: 1, startTime: '09:00', endTime: '18:00' },
        { dayNo: 2, startTime: '10:00', endTime: '21:30' }
      ],
      originalStops: deriveEditableStops(snapshot),
      stops: [
        { nodeKey: 'node-a', poiName: '武侯祠', dayNo: 1, order: 1, stayDuration: 90, removed: false },
        { nodeKey: 'node-b', poiName: '锦里', dayNo: 1, order: 3, stayDuration: 30, removed: false },
        { nodeKey: 'node-c', poiName: '杜甫草堂', dayNo: 1, order: 2, stayDuration: 70, removed: false }
      ],
      customRows: [
        {
          mode: 'inline',
          dayNo: 2,
          order: 1,
          stayDuration: 45,
          name: '社区书店',
          roughLocation: '武侯区某街道',
          category: '文艺',
          reason: '下午想加一个可休息的阅读点'
        }
      ]
    })

    expect(payload.operations).toEqual([
      { type: 'update_stay', nodeKey: 'node-b', stayDuration: 30 },
      { type: 'update_day_window', dayNo: 2, startTime: '10:00', endTime: '21:30' },
      { type: 'move_node', nodeKey: 'node-c', targetDayNo: 1, targetIndex: 2 },
      { type: 'move_node', nodeKey: 'node-b', targetDayNo: 1, targetIndex: 3 },
      {
        type: 'insert_inline_custom_poi',
        dayNo: 2,
        targetIndex: 1,
        stayDuration: 45,
        customPoiDraft: {
          name: '社区书店',
          roughLocation: '武侯区某街道',
          category: '文艺',
          reason: '下午想加一个可休息的阅读点'
        }
      }
    ])
  })
})
