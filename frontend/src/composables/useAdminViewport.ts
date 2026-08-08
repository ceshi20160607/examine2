import { onBeforeUnmount, onMounted, ref } from 'vue'

export function useAdminViewport() {
  const isMobile = ref(false)
  let media: MediaQueryList | undefined
  let usingResizeFallback = false

  const update = () => {
    isMobile.value = media?.matches ?? window.innerWidth <= 720
  }

  onMounted(() => {
    if (typeof window.matchMedia !== 'function') {
      usingResizeFallback = true
      update()
      window.addEventListener('resize', update)
      return
    }
    media = window.matchMedia('(max-width: 720px)')
    update()
    media.addEventListener('change', update)
  })

  onBeforeUnmount(() => {
    media?.removeEventListener('change', update)
    if (usingResizeFallback) window.removeEventListener('resize', update)
  })

  return { isMobile }
}
