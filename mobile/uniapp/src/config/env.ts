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

export function getBaseURL(): string {
  const custom = uni.getStorageSync('apiBaseUrl')
  if (typeof custom === 'string' && custom.trim().startsWith('http')) {
    return custom.trim().replace(/\/$/, '')
  }
  const env = getEnv()
  if (env === 'dev') return 'http://127.0.0.1:9999'
  // test/prod：须通过 uni.setStorageSync('apiBaseUrl', 'https://api.example.com') 配置
  return 'http://127.0.0.1:9999'
}
