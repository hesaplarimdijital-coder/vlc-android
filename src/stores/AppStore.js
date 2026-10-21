import { defineStore } from 'pinia'
import { reactive } from 'vue'
import { useLocalStorage } from '@vueuse/core'

/**
 * App's main store. Manages global ui states
 */
export const useAppStore = defineStore('app', {

  state: () => ({
    loading: false,
    wsTicket: "",
    displayType: reactive(useLocalStorage('displayType', {})),
    videoGrouping: reactive(useLocalStorage('videoGrouping',0)),
    warning: Object,
    socketOpened: false,
    longPollingOpened: false,
    showAddStream: false,
    needRefresh: false,
    needRefreshPlaylist: false,
    darkTheme: reactive(useLocalStorage('darkTheme',false)),
    showSubtitlePicker: false,
    title: "",
    loadingTimeout: null,
  }),
  getters: {
  },
  actions: {
    toggleDisplayType(route) {
      this.displayType[route] = !this.displayType[route]
    },
    changeGrouping(mode) {
      this.videoGrouping = mode
    },
    delayLoading(delay, loading) {
      if (!delay) {
        this.loading = loading
        return
      }
      clearTimeout(this.loadingTimeout)
      if (!loading) {
        this.loading = false
        return
      }

      if (!this.loading) {
        this.loadingTimeout = setTimeout(function () {
          this.loading = true
        }.bind(this), 800);
      }
    }
  },
})