export type AppEnv = 'dev' | 'test' | 'prod'

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
  if (env === 'test') return 'https://test.example.com'
  return 'https://prod.example.com'
}

