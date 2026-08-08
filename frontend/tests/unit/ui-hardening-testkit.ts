import { expect } from 'vitest'

export interface TestViewport {
  name: 'compact' | 'tablet' | 'desktop'
  width: number
  height: number
}

export const UI_HARDENING_VIEWPORTS: readonly TestViewport[] = [
  { name: 'compact', width: 360, height: 800 },
  { name: 'tablet', width: 768, height: 1024 },
  { name: 'desktop', width: 1440, height: 900 },
]

export function setTestViewport(viewport: TestViewport) {
  Object.defineProperties(window, {
    innerWidth: { configurable: true, value: viewport.width },
    innerHeight: { configurable: true, value: viewport.height },
  })
  Object.defineProperties(document.documentElement, {
    clientWidth: { configurable: true, value: viewport.width },
    clientHeight: { configurable: true, value: viewport.height },
  })
  window.dispatchEvent(new Event('resize'))
}

interface ElementBox {
  left?: number
  top?: number
  width: number
  height?: number
  clientWidth?: number
  scrollWidth?: number
}

/**
 * jsdom has no layout engine. Tests use this helper to model the browser box
 * that a production class owns, while keeping overflow assertions executable.
 */
export function installElementBox(element: Element, box: ElementBox) {
  const left = box.left ?? 0
  const top = box.top ?? 0
  const height = box.height ?? 48
  const clientWidth = box.clientWidth ?? box.width
  const scrollWidth = box.scrollWidth ?? clientWidth
  Object.defineProperties(element, {
    clientWidth: { configurable: true, value: clientWidth },
    scrollWidth: { configurable: true, value: scrollWidth },
    getBoundingClientRect: {
      configurable: true,
      value: () => ({
        x: left,
        y: top,
        left,
        top,
        width: box.width,
        height,
        right: left + box.width,
        bottom: top + height,
        toJSON: () => ({}),
      }),
    },
  })
}

export function expectContainedByViewport(element: Element, viewport: TestViewport) {
  const bounds = element.getBoundingClientRect()
  expect(bounds.left, 'surface starts outside the viewport').toBeGreaterThanOrEqual(0)
  expect(bounds.right, 'surface crosses the viewport edge').toBeLessThanOrEqual(viewport.width)
  expect(element.scrollWidth, 'surface leaks child overflow to the page').toBeLessThanOrEqual(viewport.width)
}

export function expectOwnedHorizontalOverflow(element: Element) {
  expect(element.scrollWidth, 'expected this explicit scroll owner to contain wide content')
    .toBeGreaterThan(element.clientWidth)
}

function normalize(value: string | null | undefined) {
  return value?.replace(/\s+/gu, ' ').trim() ?? ''
}

export function accessibleName(element: Element): string {
  const ariaLabel = normalize(element.getAttribute('aria-label'))
  if (ariaLabel) return ariaLabel

  const labelledBy = normalize(element.getAttribute('aria-labelledby'))
  if (labelledBy) {
    const label = labelledBy
      .split(' ')
      .map(id => normalize(element.ownerDocument.getElementById(id)?.textContent))
      .filter(Boolean)
      .join(' ')
    if (label) return label
  }

  if (element instanceof HTMLInputElement || element instanceof HTMLTextAreaElement || element instanceof HTMLSelectElement) {
    const labels = Array.from(element.labels ?? []).map(label => normalize(label.textContent)).filter(Boolean)
    if (labels.length) return labels.join(' ')
  }

  const title = normalize(element.getAttribute('title'))
  if (title) return title
  return normalize(element.textContent)
}

export function expectAccessibleName(element: Element, expected?: string | RegExp) {
  const name = accessibleName(element)
  expect(name, 'interactive control or dialog has no accessible name').not.toBe('')
  if (typeof expected === 'string') expect(name).toBe(expected)
  else if (expected) expect(name).toMatch(expected)
}
