export type AppEnv = 'dev' | 'test' | 'prod'

export function getEnv(): AppEnv {
  // 第一版默认 dev；后续可在“我的-环境设置”里切换并持久化
  return 'dev'
}

export function getBaseURL(): string {
  const env = getEnv()
  if (env === 'dev') return 'http://127.0.0.1:8080'
  if (env === 'test') return 'https://test.example.com'
  return 'https://prod.example.com'
}

