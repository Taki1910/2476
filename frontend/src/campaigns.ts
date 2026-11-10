export type StoreCampaign = { id: string; name: string; image: string; query: string; art: 'court' | 'metro' | 'daily' | 'dark' }

export const storeCampaigns: StoreCampaign[] = [
  { id: 'court-high', name: 'Court High', image: '/campaigns/court-high.png', query: 'Court Originals', art: 'court' },
  { id: 'metro-runner', name: 'Metro Runner', image: '/campaigns/metro-runner.png', query: 'Metro Motion', art: 'metro' },
  { id: 'daily-canvas', name: 'Daily Canvas', image: '/campaigns/daily-canvas.png', query: 'Daily Essentials', art: 'daily' },
  { id: 'after-dark', name: 'After Dark', image: '/campaigns/after-dark.png', query: 'After Hours', art: 'dark' },
]

export function campaignDestination(campaign: StoreCampaign) {
  return { path: '/', query: { q: campaign.query, view: 'all' }, hash: '#catalog-heading' }
}
