export interface RuntimeQuickCreateModule {
  moduleCode: string
  moduleName: string
  schemaVersionId: string
}

export interface RuntimeQuickCreateModules {
  items: RuntimeQuickCreateModule[]
}
