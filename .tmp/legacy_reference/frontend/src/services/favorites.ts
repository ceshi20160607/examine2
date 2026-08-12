import { apiRequest } from './api'
import type {
  RuntimeFavoriteCreateInput,
  RuntimeFavoriteDeleteResponse,
  RuntimeFavoriteItem,
  RuntimeFavoritePage,
} from '@/types/favorites'

const base = (systemId: string) => `/api/v1/systems/${systemId}/runtime/favorites`

export const favoriteApi = {
  list(systemId: string, page = 1, size = 20) {
    const params = new URLSearchParams({ page: String(page), size: String(size) })
    return apiRequest<RuntimeFavoritePage>(`${base(systemId)}?${params}`)
  },
  create(systemId: string, body: RuntimeFavoriteCreateInput) {
    return apiRequest<RuntimeFavoriteItem>(base(systemId), {
      method: 'POST',
      body,
      idempotencyKey: crypto.randomUUID(),
    })
  },
  remove(systemId: string, favoriteId: string, expectedVersion: number) {
    return apiRequest<RuntimeFavoriteDeleteResponse>(
      `${base(systemId)}/${encodeURIComponent(favoriteId)}`,
      {
        method: 'DELETE',
        body: { expectedVersion },
        idempotencyKey: crypto.randomUUID(),
      },
    )
  },
}
