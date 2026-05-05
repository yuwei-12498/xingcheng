import { describe, expect, it } from 'vitest'
import {
  buildAmapJsApiUrl,
  configureAmapSecurity,
  getAmapMapStyle,
  isAmapJsConfigured,
  resolveAmapJsConfig
} from '../amapJsApi'

describe('amap JS API config', () => {
  it('requires both key and securityJsCode before switching the map to AMap JS API', () => {
    expect(isAmapJsConfigured({ apiKey: 'demo-key', securityJsCode: '' })).toBe(false)
    expect(isAmapJsConfigured({ apiKey: '', securityJsCode: 'demo-security' })).toBe(false)
    expect(isAmapJsConfigured({ apiKey: 'demo-key', securityJsCode: 'demo-security' })).toBe(true)
  })

  it('configures window._AMapSecurityConfig before loading the script', () => {
    const targetWindow = {}

    const applied = configureAmapSecurity({ securityJsCode: 'demo-security' }, targetWindow)

    expect(applied).toBe(true)
    expect(targetWindow._AMapSecurityConfig).toEqual({ securityJsCode: 'demo-security' })
  })

  it('builds the official AMap JS API v2 script URL without hardcoding real credentials', () => {
    const url = buildAmapJsApiUrl({
      config: resolveAmapJsConfig({
        apiKey: 'demo-key',
        securityJsCode: 'demo-security',
        apiUrl: 'https://webapi.amap.com/maps',
        version: '2.0'
      }),
      plugins: ['AMap.Scale']
    })

    expect(url).toContain('https://webapi.amap.com/maps')
    expect(url).toContain('v=2.0')
    expect(url).toContain('key=demo-key')
    expect(url).toContain('plugin=AMap.Scale')
    expect(url).not.toContain('securityJsCode')
  })

  it('keeps map style configurable for the visual style id that will be provided later', () => {
    expect(getAmapMapStyle({ mapStyle: '' })).toBe('amap://styles/f77ed9b76b4ac41027493d6a2c4427fb')
    expect(getAmapMapStyle({ mapStyle: 'amap://styles/darkblue' })).toBe('amap://styles/darkblue')
  })
})
