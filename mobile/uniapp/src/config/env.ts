export type AppEnv = 'dev' | 'test' | 'prod'

/**
 * API 根地址优先级：本地缓存 apiBaseUrl > 环境默认。
 * test/prod 未配置 apiBaseUrl 时回退 dev（127.0.0.1:9999），发布前请在「Me」页或构建变量写入真实地址。
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
