<template>
  <div v-show="this.show" id="remote" ref="remote">
    <div id="remote_header" ref="remoteHeader" class="d-flex align-items-center">
      <p class="flex1 m-3" v-t="'VIDEO_PLAYER_REMOTE'"></p>
      <ImageButton type="close" v-on:click="this.appStore.showRemote = false" />
    </div>
    <div class="d-pad-container">
      <div class="d-flex align-items-center">
        <ImageButton type="back" v-on:click="this.send('back')" />
        <span class="flex1"></span>
        <ImageButton type="keyboard" class="desktop-only" :selected="(this.keyboardActive)" data-bs-toggle="tooltip" data-bs-placement="bottom" :title="$t('REMOTE_KEYBOARD_NAVIGATION')"
          v-on:click="this.keyboardActive = !this.keyboardActive" />
      </div>
      <div class="d-pad mt-2">
        <table>
          <tbody>
            <tr>
              <td></td>
              <td>
                <ImageButton type="up" v-on:click="this.send('up')" />
              </td>
              <td></td>
            </tr>
            <tr>
              <td>
                <ImageButton type="left" v-on:click="this.send('left')" />
              </td>
              <td>
                <ImageButton type="center" v-on:click="this.send('center')" />
              </td>
              <td>
                <ImageButton type="right" v-on:click="this.send('right')" />
              </td>
            </tr>
            <tr>
              <td></td>
              <td>
                <ImageButton type="down" v-on:click="this.send('down')" />
              </td>
              <td></td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="d-flex align-items-center mt-2">
        <span class="flex1"></span>
        <ImageButton type="skip_previous" v-on:click="this.send('skip-previous')" />
        <ImageButton v-if="this.playerStore.nowPlaying.playing" type="pause" v-on:click="this.send('pause')" />
        <ImageButton v-if="!this.playerStore.nowPlaying.playing" type="play" v-on:click="this.send('play')" />
        <ImageButton type="skip_next" v-on:click="this.send('skip-next')" />
        <span class="flex1"></span>
      </div>





    </div>
  </div>
</template>

<script>
import { useAppStore } from '../stores/AppStore'
import { usePlayerStore } from '../stores/PlayerStore'
import { mapStores } from 'pinia'
import ImageButton from './ImageButton.vue'
import { mapState } from 'pinia'
import { nextTick } from 'vue'

export default {
  name: 'App',
  components: {
    ImageButton,
  },
  computed: {
    ...mapStores(usePlayerStore, useAppStore),
    ...mapState(usePlayerStore, ['playing']),
    ...mapState(useAppStore, ['showRemote'])
  },
  data() {
    return {
      show: false,
      remoteWidth: 0,
      remoteHeight: 0,
      keyboardActive: false
    }
  },
  methods: {
    send(action) {
      this.$root.sendMessage("remote", null, null, null, action)
    },
    dragElement(header) {
      let remote = this.$refs.remote
      var newX = 0, newY = 0, startX = 0, startY = 0
      header.onmousedown = dragMouseDown

      function dragMouseDown(e) {
        e = e || window.event
        e.preventDefault()
        // cursor position
        startX = e.clientX
        startY = e.clientY
        document.onmouseup = closeDragElement
        document.onmousemove = elementDrag
      }

      function elementDrag(e) {
        e = e || window.event
        e.preventDefault()
        // calculate the new cursor position:
        newX = startX - e.clientX
        newY = startY - e.clientY
        startX = e.clientX
        startY = e.clientY
        // set the element's new position:

        //bounds
        let body = document.body
        let html = document.documentElement

        var pageHeight = Math.max(body.scrollHeight, body.offsetHeight, html.clientHeight, html.scrollHeight, html.offsetHeight)
        var pageWidth = Math.max(body.scrollWidth, body.offsetWidth, html.clientWidth, html.scrollWidth, html.offsetWidth)
        let width = remote.offsetWidth + 1
        let height = remote.offsetHeight
        let y = Math.min(Math.max((remote.offsetTop - newY), 0), (pageHeight - height))
        let x = Math.min(Math.max((remote.offsetLeft - newX), 0), (pageWidth - width))
        remote.style.top = `${y}px`
        remote.style.left = `${x}px`

        localStorage.setItem("remoteX", x)
        localStorage.setItem("remoteY", y)

      }

      function closeDragElement() {
        // stop moving when mouse button is released:
        document.onmouseup = null
        document.onmousemove = null
      }
    },
    getPageSize() {
      let body = document.body
      let html = document.documentElement
      var pageHeight = Math.max(body.scrollHeight, body.offsetHeight, html.clientHeight, html.offsetHeight)
      var pageWidth = Math.max(body.scrollWidth, body.offsetWidth, html.clientWidth, html.scrollWidth, html.offsetWidth)
      return { width: pageWidth, height: pageHeight }
    },
    resizeHandler() {
      if (this.show) {
        this.constraintToBounds()
      }
    },
    getConstraintBounds(wantedX, wantedY) {
      let pageSize = this.getPageSize()
      let width = this.remoteWidth + 1
      let height = this.remoteHeight
      if (width < 1 || height < 1) {
        return
      }
      let y = Math.min(Math.max((wantedY), 0), (pageSize.height - height))
      let x = Math.min(Math.max((wantedX), 0), (pageSize.width - width))
      return { x: x, y: y }
    },
    constraintToBounds() {
      let remote = this.$refs.remote
      let bounds = this.getConstraintBounds(this.$refs.remote.offsetLeft, this.$refs.remote.offsetTop)
      remote.style.top = `${bounds.y}px`
      remote.style.left = `${bounds.x}px`
    }
  },
  mounted() {
    this.dragElement(this.$refs.remoteHeader)
  },
  watch: {
    playing() {
      this.show = this.playerStore.playing && this.appStore.showRemote
    },
    showRemote() {
      this.show = this.playerStore.playing && this.appStore.showRemote
    },
    show() {
      if (this.show) {
        nextTick(() => {
          if (this.remoteWidth == 0) this.remoteWidth = this.$refs.remote.offsetWidth
          if (this.remoteHeight == 0) this.remoteHeight = this.$refs.remote.offsetHeight
          let y = localStorage.getItem("remoteY")
          let x = localStorage.getItem("remoteX")
          let bounds = this.getConstraintBounds(x, y)

          this.$refs.remote.style.top = `${bounds.y}px`
          this.$refs.remote.style.left = `${bounds.x}px`
        })
      }
    }
  },

  created() {
    window.addEventListener("resize", this.resizeHandler)
    document.addEventListener('keyup', (e) => {
      if (!this.keyboardActive) return
      if (e.code === "ArrowUp") this.send("up")
      if (e.code === "ArrowDown") this.send("down")
      if (e.code === "ArrowLeft") this.send("left")
      if (e.code === "ArrowRight") this.send("right")
      if (e.code === "Enter" || e.code === "NumpadEnter") this.send("center")
      if (e.code === "Escape") this.send("back")
    })
  },
  unmounted() {
    window.removeEventListener("resize", this.resizeHandler)
  },
}
</script>

<style>
#remote {
  position: absolute;
  z-index: 1022;
  background-color: var(--light-gray);
  border: 1px solid var(--bs-border-color);
  border-radius: 8px;
  text-align: center;
}

#remote_header {
  cursor: move;
  z-index: 1023;
  height: 64px;
  background-color: var(--bs-card-bg);
  border-radius: 8px 8px 0px 0px;
  border-bottom: 1px solid var(--bs-border-color);
}

.d-pad-container {
  padding-top: 16px;
  padding-bottom: 16px;
  width: 100%;
  height: 100%;
}

.d-pad {
  display: inline-block;
  height: 168px;
  width: 168px;
  margin: 0 auto;
  border-radius: 50%;
  border: 2px solid var(--bs-border-color);
  display: flex;
}


@media screen and (max-width: 768px) {
  #remote {
    width: 100%;
    height: 100%;
    top: 0 !important;
    left: 0 !important;
  }

  .desktop-only {
    display: none !important;
  }
}

</style>