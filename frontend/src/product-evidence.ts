export type IntendedUse = 'CASUAL_DAILY' | 'ROAD_RUNNING' | 'TRAIL_WALKING' | 'TRAIL_RUNNING'
export type PrimarySurface = 'SMOOTH_HARD_SURFACE' | 'ROAD_TRACK' | 'MIXED_TRAIL'
export type UpperConstruction = 'SYNTHETIC_LEATHER' | 'CANVAS_TEXTILE' | 'MESH_TEXTILE' | 'KNIT_TEXTILE' | 'WOVEN_TEXTILE' | 'MESH_TEXTILE_WITH_SYNTHETIC_OVERLAYS'
export type CutProfile = 'LOW_TOP' | 'HIGH_TOP'
export type PrimarySoleProfile = 'CUPSOLE' | 'FLEX_GROOVED' | 'LUGGED'

export type ProductEvidence = {
  intendedUse: IntendedUse | null
  primarySurface: PrimarySurface | null
  upperConstruction: UpperConstruction | null
  cutProfile: CutProfile | null
  primarySoleProfile: PrimarySoleProfile | null
}

export const evidenceOptions = {
  intendedUse: ['CASUAL_DAILY', 'ROAD_RUNNING', 'TRAIL_WALKING', 'TRAIL_RUNNING'],
  primarySurface: ['SMOOTH_HARD_SURFACE', 'ROAD_TRACK', 'MIXED_TRAIL'],
  upperConstruction: ['SYNTHETIC_LEATHER', 'CANVAS_TEXTILE', 'MESH_TEXTILE', 'KNIT_TEXTILE', 'WOVEN_TEXTILE', 'MESH_TEXTILE_WITH_SYNTHETIC_OVERLAYS'],
  cutProfile: ['LOW_TOP', 'HIGH_TOP'],
  primarySoleProfile: ['CUPSOLE', 'FLEX_GROOVED', 'LUGGED'],
} as const

const labels: Record<string, string> = {
  CASUAL_DAILY: 'Casual / daily', ROAD_RUNNING: 'Road running', TRAIL_WALKING: 'Trail walking', TRAIL_RUNNING: 'Trail running',
  SMOOTH_HARD_SURFACE: 'Smooth hard surface', ROAD_TRACK: 'Road / track', MIXED_TRAIL: 'Mixed trail',
  SYNTHETIC_LEATHER: 'Synthetic leather', CANVAS_TEXTILE: 'Canvas textile', MESH_TEXTILE: 'Mesh textile',
  KNIT_TEXTILE: 'Knit textile', WOVEN_TEXTILE: 'Woven textile', MESH_TEXTILE_WITH_SYNTHETIC_OVERLAYS: 'Mesh textile with synthetic overlays',
  LOW_TOP: 'Low top', HIGH_TOP: 'High top', CUPSOLE: 'Cupsole', FLEX_GROOVED: 'Flex-grooved sole', LUGGED: 'Lugged sole',
  RUNS_SMALL: 'Runs small', TRUE_TO_SIZE: 'True to size', RUNS_LARGE: 'Runs large', NARROW: 'Narrow', REGULAR: 'Regular', WIDE: 'Wide',
}

const positioning: Record<string, string> = {
  'CASUAL_DAILY|SMOOTH_HARD_SURFACE|SYNTHETIC_LEATHER|LOW_TOP|CUPSOLE': 'Low-top synthetic leather for everyday wear.',
  'CASUAL_DAILY|SMOOTH_HARD_SURFACE|CANVAS_TEXTILE|HIGH_TOP|CUPSOLE': 'High-top canvas for everyday wear.',
  'ROAD_RUNNING|ROAD_TRACK|MESH_TEXTILE|LOW_TOP|FLEX_GROOVED': 'Mesh upper for road and track running.',
  'ROAD_RUNNING|ROAD_TRACK|KNIT_TEXTILE|LOW_TOP|FLEX_GROOVED': 'Knit upper for road and track running.',
  'TRAIL_WALKING|MIXED_TRAIL|WOVEN_TEXTILE|LOW_TOP|LUGGED': 'Woven upper and lugged sole for mixed-trail walks.',
  'TRAIL_RUNNING|MIXED_TRAIL|MESH_TEXTILE_WITH_SYNTHETIC_OVERLAYS|LOW_TOP|LUGGED': 'Mesh upper with overlays and a lugged sole for mixed-trail runs.',
}

export function evidenceValueLabel(value: string) { return labels[value] ?? value }

export function productPositioning(evidence: ProductEvidence | null | undefined) {
  if (!evidence) return null
  return positioning[[evidence.intendedUse, evidence.primarySurface, evidence.upperConstruction, evidence.cutProfile, evidence.primarySoleProfile].join('|')] ?? null
}

export function evidenceHighlights(evidence: ProductEvidence | null | undefined) {
  if (!evidence) return []
  return [evidence.intendedUse, evidence.upperConstruction, evidence.primarySurface, evidence.cutProfile, evidence.primarySoleProfile]
    .flatMap(value => value ? [evidenceValueLabel(value)] : []).slice(0, 2)
}

export function evidenceRows(evidence: ProductEvidence | null | undefined): [string, string][] {
  if (!evidence) return []
  const values: [string, string | null][] = [
    ['Intended use', evidence.intendedUse], ['Primary surface', evidence.primarySurface],
    ['Upper construction', evidence.upperConstruction], ['Cut profile', evidence.cutProfile],
    ['Primary sole profile', evidence.primarySoleProfile],
  ]
  return values.flatMap(([key, value]) => value ? [[key, evidenceValueLabel(value)] as [string, string]] : [])
}
