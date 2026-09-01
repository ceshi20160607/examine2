import type { PrintPreview } from './types'

export type PrintFieldMode = 'none' | 'field' | 'detail'

export function printFieldMode(code: string, fieldCodes: string[], detailFieldCodes: string[]): PrintFieldMode {
  if (detailFieldCodes.includes(code)) return 'detail'
  return fieldCodes.includes(code) ? 'field' : 'none'
}

export function cyclePrintField(code: string, fieldCodes: string[], detailFieldCodes: string[]) {
  const mode = printFieldMode(code, fieldCodes, detailFieldCodes)
  const normal = fieldCodes.filter(item => item !== code)
  const detail = detailFieldCodes.filter(item => item !== code)
  if (mode === 'none') normal.push(code)
  if (mode === 'field') detail.push(code)
  return { fieldCodes: normal, detailFieldCodes: detail }
}

export function printPreviewMatchesDraft(preview: PrintPreview | undefined, draftRevision: number | undefined) {
  return Boolean(preview && draftRevision != null && preview.draftRevision === draftRevision && preview.pages.length)
}
