<template>
  <div class="footer" id="player" v-show="this.playerStore.playing" @wheel.prevent @scroll.prevent
    v-bind:class="(this.playerStore.responsivePlayerShowing) ? 'footer force-show' : 'footer'">
    <div class="progress-container">
      <p id="time"> {{ $readableDuration(new Date(this.playerStore.nowPlaying.progress)) }}
      </p>
      <p id="duration">{{ $readableDuration(new Date(this.playerStore.nowPlaying.duration))
      }}</p>
            <ImageButton v-for="bookmark in this.playerStore.nowPlaying.bookmarks" :key="bookmark.time" type="bookmark" class="bookmark" v-bind:style="this.getBookmarkLeft(bookmark)" v-on:click.stop="seek(bookmark.time)"
        v-tooltip data-bs-placement="bottom" :title="bookmark.title" />
      <input type="range" ref="progress" min="0" :max="this.playerStore.nowPlaying.duration" class="player-progress"
        step="1" @change="this.progressChange($event)" @input="this.progressChange($event)"
        @touchstart="this.progressTouched = true" @touchend="this.progressTouched = false"
        @touchcancel="this.progressTouched = false">
    </div>
    <div id="player_content" class="row">
      <div class="col-12 col-md player-inner-content" id="media_info" v-on:click.stop="toggleMediaInfo($event)">
        <img ref="playerArtwork" class="player-artwork" :class="this.playerStore.nowPlaying.isVideoPlaying ? 'video' : ''">
        <div class="player-info">
          <p id="title" class="h6 text-truncate">{{ this.playerStore.nowPlaying.title }}</p>
          <p id="artist" class="text-truncate">{{ this.playerStore.nowPlaying.artist }}</p>
        </div>
        <img :src="(`./icons/info.svg`)" class="badge-image"/>
      </div>

      <div class="player_controls player-inner-content col-12 col-md">
        <span class="flex1" />
        <ImageButton type="shuffle" id="player_shuffle" ref="shuffle" v-on:click="shuffle()"
          v-bind:class="(this.playerStore.nowPlaying.shuffle) ? 'active' : ''" />
        <ImageButton type="skip_previous" id="player_previous" ref="previous" v-on:click="previous()" />
        <ImageButton type="replay_10" id="player_previous_10" ref="previous10" v-on:click="previous10()" />
        <ImageButton type="play_circle" class="big" id="player_play" ref="play"
          v-show="!this.playerStore.nowPlaying.playing" v-on:click="play()" />
        <ImageButton type="pause_circle" class="big" id="player_pause" ref="pause"
          v-show="this.playerStore.nowPlaying.playing" v-on:click="pause()" />
        <ImageButton type="forward_10" id="player_next_10" ref="next10" v-on:click="next10()" />
        <ImageButton type="skip_next" id="player_next" ref="next" v-on:click="next()" />
        <ImageButton id="player_repeat" ref="repeat" @click.stop="repeat()"
          v-bind:type="(this.playerStore.nowPlaying.repeat != 1) ? 'repeat' : 'repeat_one'"
          v-bind:class="(this.playerStore.nowPlaying.repeat != 0) ? 'active' : ''" />
        <span class="flex1" />
      </div>
      <div class="player_right player-inner-content col-12 col-md">
        <div class="player-right-container playqueue-container">
          <ImageButton type="queue_music" id="playqueue" :selected="this.playerStore.playqueueShowing" ref="playqueueButton"
            v-on:click.stop="togglePlayQueue($event)" v-bind:class="(this.playerStore.playqueueShowing) ? 'active' : ''" />
        </div>
        <div class="player-right-container volume-container">
          <VolumeBar />
        </div>
        <div class="player-right-container output-container dropdown dropup">
          <ImageButton type="music_output" data-bs-toggle="dropdown" aria-expanded="false" data-bs-trigger="hover" data-bs-placement="left" />
          <ul class="dropdown-menu nav-dropdown output-dropdown">
            <li class="dropdown-item">
                <div class="nav-button d-flex btn  btn-lg medium" v-on:click="toggleOutput()"  v-bind:class="isActive('browser') ? 'current' : ''">
                  <img :src="(`./icons/web.svg`)" v-if="this.playerStore.outputMode == 'phone'">
                <img :src="(`./icons/checked.svg`)" v-if="this.playerStore.outputMode != 'phone'">
                <p class="collapsed-menu-text" v-t="'PLAY_ON_BROWSER'"></p>
              </div>
            </li>
            <li class="dropdown-item">
                <div class="nav-button d-flex btn  btn-lg medium" v-on:click="toggleOutput()"  v-bind:class="isActive('phone') ? 'current' : ''">
                  <img :src="(`./icons/mobile.svg`)" v-if="this.playerStore.outputMode == 'browser'">
                  <img :src="(`./icons/checked.svg`)"  v-if="this.playerStore.outputMode != 'browser'">
                  <p class="collapsed-menu-text" v-t="'PLAY_ON_PHONE'"></p>
                </div>
            </li>
          </ul>
        </div>
      </div>
    </div>
  </div>
  <div class="mini-player-fab" v-on:click.stop="showResponsivePlayer()"
    v-show="!this.playerStore.responsivePlayerShowing && this.playerStore.playing">
    <div class="playing">
      <span class="playing-bar playing-bar1"></span>
      <span class="playing-bar playing-bar2"></span>
      <span class="playing-bar playing-bar3"></span>
    </div>

  </div>
</template>

<script>
import ImageButton from './ImageButton.vue'
import { usePlayerStore } from '../stores/PlayerStore'
import { mapStores } from 'pinia'
import { mapState } from 'pinia'
import { vlcApi } from '../plugins/api.js'
import VolumeBar from './VolumeBar.vue'

export default {
  components: {
    ImageButton,
    VolumeBar
  },
  data() {
    return {
      playing: false,
      loadedArtworkUrl: "",
      progressTouched: false,
    }
  },
  computed: {
    ...mapStores(usePlayerStore),
    ...mapState(usePlayerStore, ['nowPlaying'])
  },
  methods: {
    changeProgressIfNeeded() {
      this.updateProgressBackground()
      if (this.$refs.progress.matches(':hover') || this.volumeTouched) return
      if (this.$refs.progress.value != this.nowPlaying.progress) this.$refs.progress.value = this.nowPlaying.progress
      this.updateProgressBackground()
    },
    updateProgressBackground() {
      let target = this.$refs.progress
      const min = target.min
      const max = target.max
      const val = target.value

      target.style.backgroundSize = (val - min) * 100 / (max - min) + '% 100%'
    },
    play() {
      this.$root.sendMessage("play");
      this.$log.log("Sending play");
    },
    pause() {
      this.$root.sendMessage("pause");
      this.$log.log("Sending pause");
    },
    previous() {
      this.$root.sendMessage("previous");
      this.$log.log("Sending previous");
    },
    next() {
      this.$root.sendMessage("next");
      this.$log.log("Sending next");
    },
    shuffle() {
      this.nowPlaying.shuffle = !this.nowPlaying.shuffle
      this.$root.sendMessage("shuffle");
      this.$log.log("Sending shuffle");
    },
    repeat() {
      let newRepeat = this.nowPlaying.repeat + 1
      if (newRepeat > 2) newRepeat = 0
      this.nowPlaying.repeat = newRepeat
      this.$root.sendMessage("repeat");
      this.$log.log("Sending repeat");
    },
    previous10() {
      this.$root.sendMessage("previous10");
      this.$log.log("Sending previous10");
    },
    next10() {
      this.$root.sendMessage("next10");
      this.$log.log("Sending next10");
    },
    progressChange(event) {
      this.updateProgressBackground()
      this.$root.sendMessage("set-progress", event.target.value);
      this.changeProgressIfNeeded()
    },
    seek(time) {
      this.updateProgressBackground()
      this.$root.sendMessage("set-progress", time);
      this.changeProgressIfNeeded()
    },
    togglePlayQueue() {
      this.playerStore.playqueueShowing = !this.playerStore.playqueueShowing
    },
    toggleOutput() {
      this.playerStore.outputMode = this.playerStore.outputMode == 'phone' ? 'browser' : 'phone'
    },
    toggleMediaInfo() {
      this.playerStore.mediaInfoShowing = !this.playerStore.mediaInfoShowing
    },
    showResponsivePlayer() {
      this.playerStore.playqueueShowing = !this.playerStore.playqueueShowing
      this.playerStore.responsivePlayerShowing = !this.responsivePlayerShowing
    },
    getBookmarkLeft(bookmark) {
       return `left: calc(${(bookmark.time / this.playerStore.nowPlaying.duration)*100}% - 28px)`
     },
    isActive(outputMode) {
       return this.playerStore.outputMode == outputMode
     },
  },
  watch: {
    nowPlaying() {
      this.changeProgressIfNeeded()
      if (this.loadedArtworkUrl != this.nowPlaying.uri) {
        this.loadedArtworkUrl = this.nowPlaying.uri
        this.$refs.playerArtwork.src = vlcApi.artwork(null, null, null, Date.now())
      }
    },
  },
}
</script>

<style lang='scss'>
@import '../scss/colors.scss';

:root {
  --playerHeight: 156px;
}

@media screen and (max-width: 1096px) {
  .player-right-container.volume-container,
  .player-right-container.output-container {
    display: none !important;
  }
}

@media screen and (max-width: 768px) {
  #player {

    display: none;
  }

  .player_right {
    display: none !important;
  }

  .player-right-container.playqueue-container {
    display: none;
  }

  .player-right-container.volume-container {
    float: none;
  }

  .badge-image {
    top: 8px !important;
    right: 16px !important;
  }
}

@media screen and (max-width: 450px) {
  .player_controls .image-button {

    width: 40px;
  }

  .player_controls .image-button.big {
    width: 58px;
  }
}

@media screen and (max-width: 330px) {
  .player_controls .image-button {

    width: 32px;
  }

  .player_controls .image-button.big {
    width: 48px;
  }
}

@media screen and (min-width: 768px) {
  :root {
    --playerHeight: 98px;
  }

  .mini-player-fab {
    display: none;
  }

  #media_info {
    overflow: auto;
    cursor: pointer;
    height: 100%;
  }

  #time,
  #duration {
    background-color: transparent !important;
  }


}

.output-dropdown .btn {
  padding-left: 0px;
  padding-right: 0px;
}

.output-container .current {
  color: var(--bs-primary);
  filter: var(--img-tint-active);
}

#media_info:hover {
    background-color: var(--hover-gray);
  }

.force-show {
  display: block !important;
}


#player {
  width: 100%;
  color: var(--bs-heading-color);
  align-items: center;
  z-index: 1022;
}

#player_content {
  display: flex;
  grid-template-columns: repeat(3, 1fr);
  grid-gap: 10px;
  align-items: center;
  background: var(--bs-card-bg);
}

.player-inner-content {
  padding-left: 16px !important;
  padding-right: 16px !important;
  // 4px offset to compensate the progress height
  padding-top: 10px !important;
  padding-bottom: 6px !important;
  border-radius: 8px;
}



.progress-container {
  position: absolute;
  margin-top: -14px;
  width: 100vw;
}

#progress_bar {
  min-width: 200px;
}

#player_controls_progress {
  display: flex;
  width: 100%;
  // position: absolute;
  top: 0;
  left: 0;
}

.player-artwork {
  width: 54px;
  height: 54px;
  float: left;
  border-radius: 6px;
  object-fit: cover;
}

.player-artwork.video {
  width: 96px;
}

.player-progress {
  position: relative;
  z-index: 1;
}

#time {
  left: 8px;
}

#duration {
  right: 8px;
}

#time,
#duration {
  padding-left: 8px;
  padding-right: 8px;
  background-color: var(--light-gray);
  border-radius: 8px 8px 0px 0px;
  position: absolute;
  top: -10px;
}

.player_right>* {
  float: right;
}

.player-right-container {
  height: 100%;
  display: flex;
  align-items: center;
}

.player_controls {
  display: flex;
  align-items: baseline;
}

.flex1 {
  flex: 1;
}

#title,
#artist {
  padding-left: 16px;
  margin-bottom: 0;
  height: 27px;
  vertical-align: middle;
  line-height: 27px;
}

#artist {
  color: var(--secondary-text);
}


.mini-player-fab {
  position: fixed;
  bottom: 16px;
  right: 16px;
  width: 42px;
  height: 42px;
  background-color: var(--bs-primary);
  border-radius: 50%;
}

.mini-player-fab .playing {
  background: none;
}

.bookmark {
  position: absolute;
  top: -25px;
}

#media_info {
  position: relative;
}

.badge-image {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 18px;
  filter: var(--img-tint);
}

#player_shuffle.active, #player_repeat.active {
  filter: none;
}

#player_shuffle.active img, #player_repeat.active img {
  filter: invert(61%) sepia(64%) saturate(4340%) hue-rotate(358deg) brightness(99%) contrast(109%);
}
</style>media_info