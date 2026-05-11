import { httpGet } from '@/api/http'
import type { ApiResult } from '@/api/http'

export function ping(): Promise<ApiResult<any>> {
  // 后端 PingController: GET /ping
  return httpGet<any>('/ping')
}

