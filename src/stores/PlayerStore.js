import { defineStore } from 'pinia'
import { reactive } from 'vue'
import { useLocalStorage } from '@vueuse/core'

/**
 * Manages the state of the player
 */
export const usePlayerStore = defineStore('player', {

  state: () => ({
    playing: false,
    nowPlaying: Object,
    playqueueData: Object,
    playqueueShowing: false,
    mediaInfoShowing: false,
    playQueueEdit: false,
    responsivePlayerShowing: false,
    showRemote: false,
    volume:0,
    waitingConfirmation: Object,
    outputMode: reactive(useLocalStorage('outputMode', 'phone')),
    browserVolume: reactive(useLocalStorage('browserVolume', 100)),
  }),
  getters: {
  },
  actions: {
    togglePlayQueueEdit() {
      this.playQueueEdit = !this.playQueueEdit
    },
  },
})