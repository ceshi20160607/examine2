export function conversionTargetCodes(configJson?: string): string[] {
  try {
    const config = JSON.parse(configJson || '{}') as {
      targetModuleCode?: unknown
      targets?: Array<{ moduleCode?: unknown }>
    }
    const candidates = Array.isArray(config.targets)
      ? config.targets.map((item) => item.moduleCode)
      : [config.targetModuleCode]
    return [...new Set(candidates
      .filter((value): value is string => typeof value === 'string')
      .map((value) => value.trim())
      .filter((value) => /^[a-z][a-z0-9_]{1,99}$/.test(value)))]
  } catch {
    return []
  }
}
