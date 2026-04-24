import { defineStore } from 'pinia'

export const useUiStore = defineStore('ui', {
  state: () => ({
    globalLoading: false as boolean,
    globalLoadingText: '' as string
  }),
  actions: {
    showLoading(text?: string) {
      this.globalLoading = true
      this.globalLoadingText = (text || '').trim()
      uni.showLoading({ title: this.globalLoadingText || '加载中...' })
    },
    hideLoading() {
      this.globalLoading = false
      this.globalLoadingText = ''
      uni.hideLoading()
    },
    toast(message: string) {
      uni.showToast({ title: (message || '提示').trim(), icon: 'none', duration: 2500 })
    }
  }
})

