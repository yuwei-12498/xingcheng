import { describe, expect, it, vi } from 'vitest'
import { getHeroRuntimeProfile, readHeroRuntimeProfile, scheduleIdleTask } from '../heroPerformance'

describe('heroPerformance utils', () => {
  it('should allow full lottie and smooth scroll on high-end desktop', () => {
    const profile = getHeroRuntimeProfile({
      viewportWidth: 1440,
      deviceMemory: 8,
      hardwareConcurrency: 8,
      connection: {
        effectiveType: '4g',
        saveData: false,
      },
      prefersReducedMotion: false,
    })

    expect(profile.posterOnly).toBe(false)
    expect(profile.allowLottie).toBe(true)
    expect(profile.allowSmoothScroll).toBe(true)
  })

  it('should fall back to poster mode on small viewport', () => {
    const profile = getHeroRuntimeProfile({
      viewportWidth: 390,
      deviceMemory: 8,
      hardwareConcurrency: 8,
      connection: {
        effectiveType: '4g',
        saveData: false,
      },
      prefersReducedMotion: false,
    })

    expect(profile.isSmallViewport).toBe(true)
    expect(profile.posterOnly).toBe(true)
    expect(profile.allowLottie).toBe(false)
  })

  it('should fall back to poster mode on save-data or slow network', () => {
    const saveDataProfile = getHeroRuntimeProfile({
      viewportWidth: 1440,
      deviceMemory: 8,
      hardwareConcurrency: 8,
      connection: {
        effectiveType: '4g',
        saveData: true,
      },
    })

    const slowNetworkProfile = getHeroRuntimeProfile({
      viewportWidth: 1440,
      deviceMemory: 8,
      hardwareConcurrency: 8,
      connection: {
        effectiveType: '3g',
        saveData: false,
      },
    })

    expect(saveDataProfile.posterOnly).toBe(true)
    expect(slowNetworkProfile.isSlowConnection).toBe(true)
    expect(slowNetworkProfile.allowLottie).toBe(false)
  })

  it('should read browser environment and normalize runtime profile', () => {
    const profile = readHeroRuntimeProfile(
      {
        innerWidth: 1280,
        matchMedia: vi.fn(() => ({ matches: false })),
      },
      {
        deviceMemory: 16,
        hardwareConcurrency: 10,
        connection: {
          effectiveType: '4g',
          saveData: false,
        },
      }
    )

    expect(profile.viewportWidth).toBe(1280)
    expect(profile.posterOnly).toBe(false)
  })

  it('should use requestIdleCallback when available', () => {
    const callback = vi.fn()
    const cancelIdleCallback = vi.fn()
    const target = {
      requestIdleCallback: vi.fn((handler) => {
        handler()
        return 7
      }),
      cancelIdleCallback,
    }

    const cancel = scheduleIdleTask(callback, { target, timeout: 300 })

    expect(target.requestIdleCallback).toHaveBeenCalledOnce()
    expect(callback).toHaveBeenCalledOnce()

    cancel()
    expect(cancelIdleCallback).toHaveBeenCalledWith(7)
  })

  it('should fall back to timeout when requestIdleCallback is unavailable', async () => {
    const callback = vi.fn()

    scheduleIdleTask(callback, { target: {}, timeout: 1 })
    await new Promise(resolve => setTimeout(resolve, 5))

    expect(callback).toHaveBeenCalledOnce()
    expect(callback.mock.calls[0][0].didTimeout).toBe(true)
  })
})
