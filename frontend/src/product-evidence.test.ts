import { afterEach, describe, expect, it } from 'vitest'
import { setLocale, t } from './i18n'
import { evidenceHighlights, evidenceRows, evidenceValueLabel, productPositioning } from './product-evidence'

afterEach(() => setLocale('en'))

describe('controlled product evidence presentation', () => {
  it.each([
    ['CASUAL_DAILY', 'SMOOTH_HARD_SURFACE', 'SYNTHETIC_LEATHER', 'LOW_TOP', 'CUPSOLE', 'Low-top synthetic leather for everyday wear.'],
    ['CASUAL_DAILY', 'SMOOTH_HARD_SURFACE', 'CANVAS_TEXTILE', 'HIGH_TOP', 'CUPSOLE', 'High-top canvas for everyday wear.'],
    ['ROAD_RUNNING', 'ROAD_TRACK', 'MESH_TEXTILE', 'LOW_TOP', 'FLEX_GROOVED', 'Mesh upper for road and track running.'],
    ['ROAD_RUNNING', 'ROAD_TRACK', 'KNIT_TEXTILE', 'LOW_TOP', 'FLEX_GROOVED', 'Knit upper for road and track running.'],
    ['TRAIL_WALKING', 'MIXED_TRAIL', 'WOVEN_TEXTILE', 'LOW_TOP', 'LUGGED', 'Woven upper and lugged sole for mixed-trail walks.'],
    ['TRAIL_RUNNING', 'MIXED_TRAIL', 'MESH_TEXTILE_WITH_SYNTHETIC_OVERLAYS', 'LOW_TOP', 'LUGGED', 'Mesh upper with overlays and a lugged sole for mixed-trail runs.'],
  ] as const)('turns approved %s evidence into concise shopper positioning', (intendedUse, primarySurface, upperConstruction, cutProfile, primarySoleProfile, expected) => {
    expect(productPositioning({ intendedUse, primarySurface, upperConstruction, cutProfile, primarySoleProfile })).toBe(expected)
  })

  it('does not invent positioning for incomplete or unapproved evidence combinations', () => {
    expect(productPositioning(null)).toBeNull()
    expect(productPositioning({ intendedUse: 'ROAD_RUNNING', primarySurface: 'ROAD_TRACK', upperConstruction: null, cutProfile: 'LOW_TOP', primarySoleProfile: 'FLEX_GROOVED' })).toBeNull()
  })

  it('uses only known structured facts and keeps cards compact', () => {
    const evidence = { intendedUse: 'ROAD_RUNNING', primarySurface: 'ROAD_TRACK', upperConstruction: 'MESH_TEXTILE', cutProfile: 'LOW_TOP', primarySoleProfile: null } as const
    expect(evidenceHighlights(evidence)).toEqual(['Road running', 'Mesh textile'])
    expect(evidenceRows(evidence)).toEqual([
      ['Intended use', 'Road running'], ['Primary surface', 'Road / track'],
      ['Upper construction', 'Mesh textile'], ['Cut profile', 'Low top'],
    ])
  })

  it('renders no placeholders or inferred defaults for unknown evidence', () => {
    expect(evidenceHighlights(null)).toEqual([])
    expect(evidenceRows(null)).toEqual([])
  })

  it.each([
    ['SMOOTH_HARD_SURFACE', 'Bề mặt phẳng, cứng'], ['ROAD_TRACK', 'Đường nhựa / đường chạy'], ['MIXED_TRAIL', 'Địa hình hỗn hợp'],
    ['LOW_TOP', 'Cổ thấp'], ['HIGH_TOP', 'Cổ cao'], ['CUPSOLE', 'Đế cupsole'],
    ['FLEX_GROOVED', 'Đế rãnh linh hoạt'], ['LUGGED', 'Đế vấu bám'],
  ])('localizes sparse controlled evidence %s for shoppers', (value, expected) => {
    setLocale('vi-VN')
    expect(t(evidenceValueLabel(value))).toBe(expected)
  })

  it('localizes the shopper positioning without exposing evidence codes', () => {
    setLocale('vi-VN')
    const key = productPositioning({ intendedUse: 'TRAIL_RUNNING', primarySurface: 'MIXED_TRAIL', upperConstruction: 'MESH_TEXTILE_WITH_SYNTHETIC_OVERLAYS', cutProfile: 'LOW_TOP', primarySoleProfile: 'LUGGED' })!
    expect(t(key)).toBe('Thân giày lưới có lớp gia cố và đế vấu cho chạy địa hình hỗn hợp.')
  })
})
