<template>
  <AppHeader @send-files="sendFiles" />
  <main v-bind:class="(this.playerStore.playing) ? 'footer-bottom-margin' : ''" class="flex1">
    <router-view></router-view>
  </main>
  <PlayQueue />
  <MediaInfo />
  <MiniPlayer ref="miniPlayer" />
  <Alert />
  <AddStream />
  <Remote />
  <SubtitlePicker />
  <UploadDialog ref="uploadComponent" />
  <AddPlaylistDialog ref="addPlaylistComponent" />
  <audio ref="browserAudio" />
  <div class="main-loading" v-show="this.appStore.loading">
    <div class="spinner-border text-primary" role="status">
      <span class="visually-hidden" v-t="'LOADING'"></span>
    </div>
  </div>
</template>

<script>
import AppHeader from './components/AppHeader.vue'
import MiniPlayer from './components/MiniPlayer.vue'
import MediaInfo from './components/MediaInfo.vue'
import PlayQueue from './components/PlayQueue.vue'
import Alert from './components/Alert.vue'
import AddStream from './components/AddStream.vue'
import Remote from './components/Remote.vue'
import SubtitlePicker from './components/SubtitlePicker.vue'
import UploadDialog from './components/UploadDialog.vue'
import AddPlaylistDialog from './components/AddPlaylistDialog.vue'
import { vlcApi } from './plugins/api.js'
import { usePlayerStore } from './stores/PlayerStore'
import { useBrowserStore } from './stores/BrowserStore'
import { useAppStore } from './stores/AppStore'
import { mapStores } from 'pinia'
import http from './plugins/auth'

export default {
  name: 'App',
  components: {
    AppHeader,
    MediaInfo,
    MiniPlayer,
    PlayQueue,
    Alert,
    AddStream,
    Remote,
    SubtitlePicker,
    UploadDialog,
    AddPlaylistDialog,
  },
  computed: {
    ...mapStores(usePlayerStore, useAppStore, useBrowserStore)
  },
  data() {
    return {
      retryDelay: 500,
      retrying: false,
      connection: null,
      queuedMessage: null,
      browserAudioSynced: false
    }
  },
  methods: {
    sendMessage(message, id, floatValue, longValue, stringValue) {
      this.$log.log(`Sending message : ${message} - ${id} - ${floatValue} - ${longValue} - ${stringValue}`)
      if (this.connection) {
        this.connection.send(
          JSON.stringify({
            message: message,
            id: id,
            floatValue: floatValue,
            longValue: longValue,
            stringValue: stringValue,
            authTicket: this.appStore.wsTicket
          })
        )
      } else {
        let component = this
        http.get(vlcApi.playbackEvent(message, id, floatValue, longValue, stringValue, this.appStore.wsTicket))
          .catch(function (error) {
            if (error.response !== undefined && error.response.status == 403) {
              component.appStore.warning = { type: "warning", message: component.$t('PLAYBACK_CONTROL_FORBIDDEN') }
            }
          })
      }
    },
    handleServerMessage(msg) {
      if (this.playerStore.playing == false && msg.shouldShow) {
        this.$log.info("Starting player ...")
        this.playerStore.playing = true
      }

      switch (msg.type) {
        case 'player-status':
          this.playerStore.playing = msg.playing
          if (!msg.playing) {
            this.playerStore.playqueueShowing = false
            this.playerStore.responsivePlayerShowing = false
            if (this.playerStore.outputMode === 'browser') {
              this.stopBrowserAudio()
            }
          }
          break
        case 'volume':
          this.playerStore.volume = msg.volume
          break
        case 'login-needed':
          if (msg.dialogOpened)
            this.appStore.warning = { type: "warning", message: this.$t('INVALID_LOGIN') }
          else
            this.appStore.warning = undefined
          break
        case 'now-playing':
          this.playerStore.nowPlaying = msg
          this.playerStore.volume = msg.volume
          if (this.playerStore.outputMode === 'browser') {
            if (!this.browserAudioSynced) {
              this.sendMessage("set-browser-audio", 1)
              this.browserAudioSynced = true
            }
            this.syncBrowserAudio(msg)
          }
          break
        case 'play-queue':
          this.playerStore.playqueueData = msg
          break
        case 'auth':
          //websockets not authorized. Asking for a ticket
          this.askWSTicket(JSON.parse(msg.initialMessage))
          break
        case 'playback-control-forbidden':
          this.appStore.warning = { type: "warning", message: this.$t('PLAYBACK_CONTROL_FORBIDDEN') }
          break
        case 'error':
            this.appStore.warning = { type: "warning", message: msg.text }
          break
        case 'ml-refresh-needed':
          this.$log.info("ML refresh needed")
          this.appStore.needRefresh = true
          break
        case 'browser-description':
          this.browserStore.descriptions.push(msg)
          break
        case 'resume-confirmation':
            this.playerStore.waitingConfirmation = { title: msg.mediaTitle, consumed: msg.consumed }
        break
        case 'network-shares':
          this.browserStore.networkShares = msg.shares
        break
      }
    },
    askWSTicket(initialMessage) {
      let component = this
      let message = initialMessage
      http.get(vlcApi.websocketAuthTicket)
        .catch(function () { })
        .then((response) => {
          if (response !== undefined) {
            component.appStore.wsTicket = response.data
            if (this.connection) {
              this.sendMessage(message.message, message.id, message.floatValue, message.longValue, message.stringValue)
            } else {
              this.queuedMessage = message
            }
          }
        })
    },
    syncBrowserAudio(msg) {
      const audio = this.$refs.browserAudio
      if (!audio) return

      if (audio.getAttribute('data-media-id') !== String(msg.id)) {
        audio.setAttribute('data-media-id', String(msg.id))

        http.head(vlcApi.stream(msg.id))
          .then(() => {
            audio.src = vlcApi.stream(msg.id)
            audio.load()
          })
          .catch((error) => {
            if (error.response && error.response.status === 403) {
              this.appStore.warning = { type: "warning", message: this.$t('PLAYBACK_CONTROL_FORBIDDEN') }
            } else if (error.response && (error.response.status === 404 || error.response.status === 400)) {
              this.appStore.warning = { type: "warning", message: this.$t('MEDIA_NOT_FOUND') }
              this.stopBrowserAudio()
            }
          })
        return
      }

      if (msg.playing && audio.paused) {
        audio.play().catch(() => {})
      } else if (!msg.playing && !audio.paused) {
        audio.pause()
      }

      const browserTimeMs = audio.currentTime * 1000
      if (Math.abs(browserTimeMs - msg.progress) > 2000) {
        audio.currentTime = msg.progress / 1000
      }

      if (msg.speed && audio.playbackRate !== msg.speed) {
        audio.playbackRate = msg.speed
      }

      audio.volume = this.playerStore.browserVolume / 100
    },
    stopBrowserAudio() {
      const audio = this.$refs.browserAudio
      if (!audio) return
      audio.pause()
      audio.removeAttribute('src')
      audio.removeAttribute('data-media-id')
      audio.load()
    },
    sendFiles() {
      this.$refs.uploadComponent.openFiles()
    },
    /**
     * Make sure a WS ticket is available before starting the WS connection
     */
    startWS() {
      let component = this
      http.get(vlcApi.websocketAuthTicket)
        .then((response) => {
          component.appStore.wsTicket = response.data
          this.setupWS()
        })
        .catch((error) => {
          this.longPolling()
          this.$log.log('An error occurred while getting the websocket auth ticket, Started Polling', error)
        })
    },
    setupWS() {
      this.$log.log("Starting connection to WebSocket Server")
      this.connection = new WebSocket(vlcApi.websocket, "player")
      this.connection.onmessage = (event) => {
        const msg = JSON.parse(event.data)
        this.handleServerMessage(msg)
      }

      this.connection.onopen = (event) => {
        if (this.retrying) {
          this.playerStore.playing = false
          this.browserAudioSynced = false
        }
        clearTimeout(this.retryId)
        this.retryDelay = 500
        this.retrying = false
        this.$log.log(`Successfully connected to the echo websocket server. Event: `, event)
        this.appStore.socketOpened = true
        if (this.queuedMessage) { 
          this.sendMessage(this.queuedMessage.message, this.queuedMessage.id, this.queuedMessage.floatValue, this.queuedMessage.longValue, this.queuedMessage.stringValue)
          this.queuedMessage = null
        } else {
          this.sendMessage("hello")
        }
      }

      this.connection.onclose = () => {
        this.$log.log("Socket closed")
        this.appStore.socketOpened = false
        if (!this.retrying) this.retry()
      }

      this.connection.onerror = () => {
        this.$log.log("Socket on error")
        this.appStore.socketOpened = false
      }
    },
    retry() {
      this.retrying = true
      clearTimeout(this.retryId)
      this.retryDelay = this.retryDelay + 500
      if (this.retryDelay > 10000) this.retryDelay = 10000
      this.setupWS()
      this.retryId = setTimeout(this.retry, this.retryDelay)
      this.$log.log(`Will retry in ${this.retryDelay}ms`)
    },
    longPolling() {
      this.appStore.longPollingOpened = true
      http.get(vlcApi.longPolling)
        .then((response) => {
          response.data.forEach(msg => {
            this.handleServerMessage(msg)
          })
          setTimeout(() => this.longPolling(), vlcApi.pollingInterval)
        })
        .catch((error) => {
          this.$log.log('An error occurred during long polling:', error)
          setTimeout(() => this.longPolling(), this.retryDelay)
        })
    },
  },
  watch: {
    'playerStore.outputMode'(mode) {
      if (mode === 'browser') {
        this.sendMessage("set-browser-audio", 1)
        this.browserAudioSynced = true
        if (this.playerStore.nowPlaying && this.playerStore.nowPlaying.id) {
          this.syncBrowserAudio(this.playerStore.nowPlaying)
        }
      } else {
        this.stopBrowserAudio()
        this.sendMessage("set-browser-audio", 0)
      }
    },
    'playerStore.browserVolume'(vol) {
      if (this.playerStore.outputMode === 'browser' && this.$refs.browserAudio) {
        this.$refs.browserAudio.volume = vol / 100
      }
    },
  },
  created: function () {
    console.log("App.vue onCreated")
    window.addEventListener('resize', () => {
      // We execute the same script as before
      let vh = window.innerHeight * 0.01
      document.documentElement.style.setProperty('--vh', `${vh}px`)
    })
    if (location.protocol !== 'https:' && process.env.NODE_ENV !== 'development') {
      this.$router.push({ name: 'SslPage' })
    } else {
      this.startWS()
    }

    if (this.appStore.darkTheme) {
      document.documentElement.setAttribute('data-bs-theme', 'dark')
    } else {
      document.documentElement.removeAttribute('data-bs-theme')
    }

  },
  mounted: function () {
    this.appStore.$subscribe((mutation, state) => {
      if (state.darkTheme) {
        document.documentElement.setAttribute('data-bs-theme', 'dark')
      } else {
        document.documentElement.removeAttribute('data-bs-theme')
      }
    })

    const audio = this.$refs.browserAudio
    if (audio) {
      audio.addEventListener('error', () => {
        if (!audio.error) return
        const code = audio.error.code
        if (code === MediaError.MEDIA_ERR_SRC_NOT_SUPPORTED || code === MediaError.MEDIA_ERR_DECODE) {
          this.appStore.warning = { type: "warning", message: this.$t('BROWSER_AUDIO_UNSUPPORTED_FORMAT') }
        }
      })
    }
  },
}
</script>

<style>
main {
  padding-bottom: 48px;
}

::-webkit-scrollbar {
  height: 8px;
  width: 8px;
}

/* Handle */
::-webkit-scrollbar-thumb {
  background: var(--scrollbar-thumb);
}

::-webkit-scrollbar-track {
  background: var(--scrollbar-track);
}

::-webkit-scrollbar-thumb:window-inactive {
  background: var(--scrollbar-thumb);
}

.footer-bottom-margin {
  overflow: auto;
}

.main-loading {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);

}
</style>
