export type AppEnv = 'dev' | 'test' | 'prod'

/**
 * API 根地址优先级：本地缓存 apiBaseUrl > 环境默认。
 * dev 默认 127.0.0.1:9999；test/prod 优先使用 VITE_API_BASE，未配置时按 H5 同域 /api 处理。
 * App/小程序发布包请通过缓存 apiBaseUrl 或构建变量写入真实 HTTPS API 地址。
 */
export function getEnv(): AppEnv {
  const saved = uni.getStorageSync('env')
  if (saved === 'dev' || saved === 'test' || saved === 'prod') return saved
  return 'dev'
}

function viteApiBase(): string {
  const v = import.meta.env.VITE_API_BASE
  if (typeof v === 'string' && v.trim()) {
    return v.trim().replace(/\/$/, '')
  }
  return ''
}

export function getBaseURL(): string {
  const custom = uni.getStorageSync('apiBaseUrl')
  if (typeof custom === 'string' && custom.trim()) {
    const c = custom.trim().replace(/\/$/, '')
    if (c.startsWith('http') || c.startsWith('/')) return c
  }
  const env = getEnv()
  if (env === 'dev') return 'http://127.0.0.1:9999'
  const built = viteApiBase()
  if (built) return built
  // prod H5 默认同域 /api；小程序须 storage 配置 https://域名/api
  return '/api'
}
