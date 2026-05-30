type OpenDocumentOptions = {
  filePath: string
  showMenu?: boolean
  success?: () => void
  fail?: (err?: unknown) => void
}

type UniWithDocument = typeof uni & {
  openDocument?: (options: OpenDocumentOptions) => void
}

export function openTempDocument(filePath: string, onFail?: () => void): boolean {
  const openDocument = (uni as UniWithDocument).openDocument
  if (typeof openDocument !== 'function') {
    onFail?.()
    return false
  }
  openDocument({
    filePath,
    showMenu: true,
    fail: () => onFail?.()
  })
  return true
}
