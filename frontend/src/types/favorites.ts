export type RuntimeFavoriteType = 'MODULE' | 'RECORD'

export interface RuntimeFavoriteItem {
  favoriteId: string
  version: number
  type: RuntimeFavoriteType
  moduleCode: string
  recordId?: string
  displayLabel: string
  status?: string
  updatedAt: string
}

export type RuntimeFavoriteCreateInput =
  | { type: 'MODULE'; moduleCode: string }
  | { type: 'RECORD'; moduleCode: string; recordId: string }

export interface RuntimeFavoritePage {
  items: RuntimeFavoriteItem[]
  page: number
  size: number
  total: number
}

export interface RuntimeFavoriteDeleteResponse {
  favoriteId: string
  version: number
  deleted: true
}
