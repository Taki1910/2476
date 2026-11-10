import { describe, expect, it } from 'vitest'
import { campaignDestination, storeCampaigns } from './campaigns'

describe('store campaign navigation', () => {
  it('uses the four existing collection searches and local campaign artwork', () => {
    expect(storeCampaigns.map(campaign => [campaign.name, campaign.query])).toEqual([
      ['Court High', 'Court Originals'],
      ['Metro Runner', 'Metro Motion'],
      ['Daily Canvas', 'Daily Essentials'],
      ['After Dark', 'After Hours'],
    ])
    expect(storeCampaigns.every(campaign => campaign.image.startsWith('/campaigns/'))).toBe(true)
  })

  it.each(storeCampaigns)('links $name to its complete filtered listing', campaign => {
    expect(campaignDestination(campaign)).toEqual({
      path: '/', query: { q: campaign.query, view: 'all' }, hash: '#catalog-heading',
    })
  })
})
