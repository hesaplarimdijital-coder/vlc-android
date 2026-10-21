import { defineStore } from 'pinia'

/**
 * Add to playlist store. Manages the currently adding to playlist media
 */
export const useAddToPlaylistStore = defineStore('addToPlaylist', {

  state: () => ({
    media: null
  }),
  getters: {
  },
  actions: {
  },
})