import { afterEach, describe, expect, it, vi } from 'vitest'

import { canScanBarcode, scanBarcodeImage } from '@/views/system/mobileCapture'

describe('mobile camera capture', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('decodes a photographed barcode and maps the browser format', async () => {
    const close = vi.fn()
    const detect = vi.fn().mockResolvedValue([{ rawValue: ' 6901234567892 ', format: 'ean_13' }])
    vi.stubGlobal('createImageBitmap', vi.fn().mockResolvedValue({ close }))
    vi.stubGlobal('BarcodeDetector', class {
      detect = detect
    })

    const result = await scanBarcodeImage(new File(['photo'], 'barcode.jpg', { type: 'image/jpeg' }))

    expect(canScanBarcode()).toBe(true)
    expect(result).toEqual({ payload: '6901234567892', symbology: 'EAN13' })
    expect(detect).toHaveBeenCalledOnce()
    expect(close).toHaveBeenCalledOnce()
  })

  it('fails clearly when the browser cannot identify a barcode', async () => {
    vi.stubGlobal('createImageBitmap', vi.fn().mockResolvedValue({ close: vi.fn() }))
    vi.stubGlobal('BarcodeDetector', class {
      detect = vi.fn().mockResolvedValue([])
    })

    await expect(scanBarcodeImage(new File(['photo'], 'empty.jpg', { type: 'image/jpeg' })))
      .rejects.toThrow('照片中未识别到条码')
  })
})
