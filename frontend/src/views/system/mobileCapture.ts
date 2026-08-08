export interface ScannedBarcode {
  payload: string
  symbology?: 'CODE128' | 'EAN13'
}

interface BarcodeResult {
  rawValue?: string
  format?: string
}

interface BarcodeDetectorLike {
  detect(source: ImageBitmapSource): Promise<BarcodeResult[]>
}

interface BarcodeDetectorConstructor {
  new(options?: { formats?: string[] }): BarcodeDetectorLike
}

function detectorConstructor(): BarcodeDetectorConstructor | undefined {
  return (globalThis as typeof globalThis & { BarcodeDetector?: BarcodeDetectorConstructor }).BarcodeDetector
}

export function canScanBarcode() {
  return Boolean(detectorConstructor() && globalThis.createImageBitmap)
}

export async function scanBarcodeImage(file: File): Promise<ScannedBarcode> {
  const Detector = detectorConstructor()
  if (!Detector || !globalThis.createImageBitmap) {
    throw new Error('当前浏览器不支持自动识别条码，请直接输入条码内容')
  }

  const bitmap = await globalThis.createImageBitmap(file)
  try {
    const results = await new Detector({ formats: ['code_128', 'ean_13'] }).detect(bitmap)
    const first = results.find((item) => item.rawValue?.trim())
    if (!first?.rawValue) throw new Error('照片中未识别到条码，请对准条码后重试')
    return {
      payload: first.rawValue.trim(),
      symbology: first.format === 'ean_13' ? 'EAN13' : first.format === 'code_128' ? 'CODE128' : undefined,
    }
  } finally {
    bitmap.close()
  }
}
