<template>
  <div v-show="this.addToPlaylistStore.media" class="playlists">
    <div class="playlists-header d-flex align-items-center">
      <h6 v-t="'ADD_PLAYLIST'" class="flex1 add-playlist-title"></h6>
      <ImageButton type="close" v-on:click="(this.addToPlaylistStore.media = null)" />
    </div>
    <div class="playlists-content">
      <p v-t="'ADDING'" class="p-2"></p>
      <!-- Media to add -->
      <div v-if="this.addToPlaylistStore.media" class="flex add-playlist-item border m-2 p-2 rounded-2">
        <img v-lazy="$getImageUrl(this.addToPlaylistStore.media, this.addToPlaylistStore.mediaType)"
          class="media-img-list flex1">
        <span class="p-2">{{ this.addToPlaylistStore.media.title }}</span>

      </div>
      <p v-t="'TO'" class="p-2"></p>
      <!-- New playlist -->
      <div class="p-2 m-2 align-items-center border rounded-2">
        <p v-t="'NEW_PLAYLIST'"></p>
        <div class="d-flex" style="align-items: center;">
          <input type="text" ref="createPlaylistText" class="form-control flex1" aria-describedby="passwordHelpInline" @keyup.enter="createPlaylist">
          <button type="button" v-on:click="createPlaylist" class="btn btn-outline-primary upload-all"
            v-t="'CREATE'"></button>
        </div>
      </div>
      <!-- Playlists -->
      <div v-if="loaded && this.playlists.length !== 0">
        <template v-for="playlist in playlists" :key="playlist.id">
          <div class="add-playlist-item d-flex playlist-item border m-2 p-2 rounded-2"
            :class="(selectedPlaylists.includes(playlist) ? 'active' : '')" v-on:click="select(playlist)">
            <img v-lazy="$getImageUrl(playlist, 'playlist')" class="media-img-list">
            <div class="flex1 p-2">
              <p class="text-truncate">{{ playlist.title }}</p>
              <p class="card-text text-truncate subtitle">{{ playlist.artist }}</p>
            </div>
            <img :src="(`./icons/checked.svg`)" v-show="(selectedPlaylists.includes(playlist))" />
          </div>
        </template>
      </div>
      <div v-else-if="loaded" class="empty-view-container">
        <EmptyView :message="getEmptyText()" />
      </div>
    </div>
    <button type="button" v-on:click="addToPlaylist" class="btn btn-outline-primary upload-all" v-t="'ADD'"></button>
  </div>
</template>

<script>
import { useAddToPlaylistStore } from '../stores/AddToPlaylistStore'
import { useAppStore } from '../stores/AppStore'
import { mapStores } from 'pinia'
import http from '../plugins/auth'
import { vlcApi } from '../plugins/api.js'
import EmptyView from '../components/EmptyView.vue'
import ImageButton from './ImageButton.vue'

export default {
  components: {
    ImageButton,
    EmptyView,
  },
  computed: {
    ...mapStores(useAppStore, useAddToPlaylistStore)
  },
  data() {
    return {
      playlists: [],
      selectedPlaylists: [],
      loaded: false,
      loading: false,
      forbidden: false,
    }
  },
  methods: {
    fetchPlaylists() {
      let component = this
      component.loading = true
      http.get(vlcApi.playlistList)
        .catch(function (error) {
          if (error.response !== undefined && error.response.status == 403) {
            component.forbidden = true;
          }
        })
        .then((response) => {
          this.loaded = true;
          if (response) {
            component.forbidden = false;
            this.playlists = response.data
          }
          component.loading = false
        });
    },
    select(playlist) {
      if (this.selectedPlaylists.includes(playlist)) {
        this.selectedPlaylists.splice(this.selectedPlaylists.indexOf(playlist), 1)
      } else {
        this.selectedPlaylists.push(playlist)
      }
    },
    createPlaylist() {
      let component = this
      let body = { name: this.$refs.createPlaylistText.value }
      http.post(vlcApi.createPlaylist, body, {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      })
        .catch(function (error) {
          if (error.response !== undefined && error.response.status == 403) {
            component.forbidden = true;
            component.appStore.warning = { type: "warning", message: component.$t('FORBIDDEN') }

          } else {
            if (error.response.data) {
              component.appStore.warning = { type: "warning", message: error.response.data }
            }
          }
        })
        .then(() => {
          this.$refs.createPlaylistText.value = ""
          this.fetchPlaylists()
        });
    },
    addToPlaylist() {
      let component = this
      let body = { mediaId: this.addToPlaylistStore.media.id, mediaType: this.addToPlaylistStore.media.mediaType, playlists: this.selectedPlaylists.map((playlist) => playlist.id) }
      http.post(vlcApi.addToPlaylist, body, {
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded'
        }
      })
        .catch(function (error) {
          if (error.response !== undefined && error.response.status == 403) {
            component.forbidden = true;
            component.appStore.warning = { type: "warning", message: component.$t('FORBIDDEN') }

          }
        })
        .then(() => {
          this.addToPlaylistStore.media = null
        });
    },
    getEmptyText() {
      if (this.forbidden) return this.$t('FORBIDDEN')
      return this.$t('NO_PLAYLIST')
    }
  },
  mounted: function () {
        this.addToPlaylistStore.$subscribe((mutation, state) => {
            console.log(`Something changed in the add to playlist store: ${JSON.stringify(state)}`)
            if (state.media) {
              this.fetchPlaylists()
            }
        })
        this.appStore.$subscribe((mutation, state) => {
            console.log(`Something changed in the app store: ${JSON.stringify(state)}`)
            if (state.needRefreshPlaylist) {
                this.fetchPlaylists();
                this.appStore.needRefreshPlaylist = false
              }
        })
    },
}
</script>

<style lang="scss">
@import '../scss/colors.scss';

.playlist-item {
  cursor: pointer;
  align-items: center;
}

.playlist-item.active {
  background-color: rgba(var(--bs-primary-rgb), 0.6);
}

.playlist-item:hover {
  background-color: rgba(var(--bs-primary-rgb), 0.3);
}

.playlists {
  position: fixed;
  border: 1px solid rgba(0, 0, 0, 0.075);
  bottom: 0;
  right: 16px;
  width: 450px;
  z-index: 1023;
  background-color: var(--light-gray);
  border-top-left-radius: 8px;
  border-top-right-radius: 8px;
  box-shadow: 0 0 0.25rem 0.125rem rgba(0, 0, 0, 0.075) !important
}

.playlists-content {
  max-height: 55vh;
  overflow: auto;
}


.add-to-playlist {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 2000;
  display: flex;
  visibility: hidden;
  opacity: 0;
  transition: visibility 0s, opacity 0.6s ease;
  flex-direction: column;
  flex-wrap: wrap;
  justify-content: center;
  align-items: center;
}

.add-to-playlist.active {
  opacity: 1;
  visibility: visible;
  background-color: var(--progress-background);
}

.playlists-header {
  height: 64px;
  background-color: var(--bs-card-bg);
  border-top-left-radius: 8px;
  border-top-right-radius: 8px;
}

.add-playlist-title {
  padding: 16px;
}
</style>