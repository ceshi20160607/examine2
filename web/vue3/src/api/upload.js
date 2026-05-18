import { buildApiUrl, getToken } from './http'

export async function uploadFile(file) {
  const url = buildApiUrl('/v1/system/uploads')
  const headers = {}
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`

  const form = new FormData()
  form.append('file', file)

  const res = await fetch(url, { method: 'POST', headers, body: form })
  const data = await res.json()
  if (!data || data.code !== 0) {
    throw new Error(data?.message || `上传失败 ${res.status}`)
  }
  return data
}
