<template>
  <div class="media-info" v-on:click="play(mediaIndex)">
    <div class="media-artwork-container">
      <div class="playing" v-show="media.playing">
        <span class="playing-bar playing-bar1" v-bind:class="(this.playerStore.nowPlaying.playing) ? '' : 'paused'"></span>
        <span class="playing-bar playing-bar2" v-bind:class="(this.playerStore.nowPlaying.playing) ? '' : 'paused'"></span>
        <span class="playing-bar playing-bar3" v-bind:class="(this.playerStore.nowPlaying.playing) ? '' : 'paused'"></span>
      </div>
      <img class="media-artwork" v-lazy="$getImageUrl(media, 'video')">
    </div>
    <div class="media-text">
      <p class="h6 queue-title text-truncate">{{ media.title }}</p>
      <p class="queue-subtitle text-truncate">{{ getItemSubtitle(media) }}</p>
    </div>
    <ImageButton v-show="this.playerStore.playQueueEdit" type="expand_more"
      v-on:click.stop="moveMediaBottom(mediaIndex)" />
    <ImageButton v-show="this.playerStore.playQueueEdit" type="expand_less" v-on:click.stop="moveMediaTop(mediaIndex)" />
    <ImageButton v-show="this.playerStore.playQueueEdit" type="playlist_remove"
      v-on:click.stop="removeItem(mediaIndex)" />
  </div>
</template>

<script>
import ImageButton from './ImageButton.vue'
import { usePlayerStore } from '../stores/PlayerStore'
import { mapStores } from 'pinia'
export default {
  components: {
    ImageButton
  },
  props: {
    media: Object,
    mediaIndex: Number
  },
  computed: {
    ...mapStores(usePlayerStore),
  },
  mounted: function () {
  },
  methods: {
    removeItem(index) {
      this.$root.sendMessage("delete-media", index);
    },
    play(index) {
      this.$root.sendMessage("play-media", index);
    },
    moveMediaBottom(index) {
      this.$root.sendMessage("move-media-bottom", index);
    },
    moveMediaTop(index) {
      this.$root.sendMessage("move-media-top", index);
    },

    getItemSubtitle(media) {
      if (media.artist.length === 0) {
        return this.$readableDuration(media.duration)
      }
      return `${media.artist} · ${this.$readableDuration(media.duration)}`
    }
  }
}
</script>

<style lang="scss">
@import '../scss/colors.scss';

.media-info {
  width: 100%;
  display: flex;
  overflow: hidden;
}

.media-text {
  flex: auto;
  min-width: 0;
}

.media-artwork-container {
  width: 42px;
  height: 42px;
  position: relative;
}

.media-artwork {
  width: 42px;
  height: 42px;
  border-radius: 6px;
  object-fit: cover;
}

.media-artwork[lazy=loading] {
  background-color: $lighter-gray;
}

.h6.queue-title,
.queue-subtitle {
  padding-left: 16px;
  margin-bottom: 0px;
}

.queue-title {
  font-size: 0.9em !important;
  line-height: 21px;
}

.queue-subtitle {
  font-size: 0.8em !important;
  line-height: 21px;
}

</style>