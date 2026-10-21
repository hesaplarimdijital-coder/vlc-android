<template>
    <div class="vol-container" :class="(fullWidth) ? 'full-width' : ''">
        <ImageButton type="volume" id="volume_icon" v-show="(this.effectiveVolume != 0)" v-on:click="mute()" />
        <ImageButton type="volume_off" id="volume_icon" v-show="(this.effectiveVolume == 0)" v-on:click="mute()" />
        <input type="range" ref="volume" min="0" max="100" step="1" @change="this.volumeChange($event)"
            :class="(fullWidth) ? 'full-width' : ''" @input="this.volumeChange($event)"
            @touchstart="this.volumeTouched = true" @touchend="this.volumeTouched = false"
            @touchcancel="this.volumeTouched = false">
    </div>
</template>

<script>
import { usePlayerStore } from '../stores/PlayerStore'
import { mapState } from 'pinia'
import { mapStores } from 'pinia'
import ImageButton from './ImageButton.vue'

export default {
    props: {
        fullWidth: Boolean
    },
    components: {
        ImageButton,
    },
    data() {
        return {
            volumeTouched: false,
            volumeTimeoutId: 0,
            oldVolume: -1,
        }
    },
    computed: {
        ...mapStores(usePlayerStore),
        ...mapState(usePlayerStore, ['nowPlaying']),
        isBrowserMode() {
            return this.playerStore.outputMode === 'browser'
        },
        effectiveVolume() {
            return this.isBrowserMode ? this.playerStore.browserVolume : this.playerStore.volume
        },
    },
    methods: {
        changeVolumeIfNeeded() {
            this.updateVolumeBackground()
            if (this.$refs.volume.matches(':hover') || this.volumeTouched) return
            const currentVol = this.isBrowserMode ? this.playerStore.browserVolume : this.nowPlaying.volume
            if (this.$refs.volume.value != currentVol) this.$refs.volume.value = currentVol
            this.updateVolumeBackground()
        },
        //Mute or unmute depending on the current volume
        mute() {
            if (this.$refs.volume.value == 0) {
                const restoreVol = this.oldVolume == -1 ? this.$refs.volume.max / 2 : this.oldVolume
                this.$refs.volume.value = restoreVol
                if (this.isBrowserMode) {
                    this.playerStore.browserVolume = restoreVol
                } else {
                    this.$root.sendMessage("set-volume", restoreVol)
                }
            } else {
                this.oldVolume = this.$refs.volume.value
                this.$refs.volume.value = 0
                if (this.isBrowserMode) {
                    this.playerStore.browserVolume = 0
                } else {
                    this.$root.sendMessage("set-volume", 0)
                }
            }
            this.updateVolumeBackground()
        },
        updateVolumeBackground() {
            let target = this.$refs.volume
            const min = target.min
            const max = target.max
            const val = target.value

            this.playerStore.volume = this.$refs.volume.value
            target.style.backgroundSize = (val - min) * 100 / (max - min) + '% 100%'
        },
        volumeChange(event) {
            clearTimeout(this.volumeTimeoutId)
            this.updateVolumeBackground()
            this.volumeTimeoutId = setTimeout(() => {
                this.updateVolumeBackground()
                if (this.isBrowserMode) {
                    this.playerStore.browserVolume = event.target.value
                } else {
                    this.$root.sendMessage("set-volume", event.target.value)
                }
                this.changeVolumeIfNeeded()
            }, "50");

            this.playerStore.volume = event.target.value

        },
    },
    mounted() {
        this.$nextTick(() => {
            if (this.isBrowserMode) {
                this.$refs.volume.value = this.playerStore.browserVolume
            }
            this.updateVolumeBackground()
        })
    },
    watch: {
        nowPlaying() {
            if (!this.isBrowserMode) {
                this.changeVolumeIfNeeded()
            }
        },
        'playerStore.browserVolume'() {
            if (this.isBrowserMode) {
                this.changeVolumeIfNeeded()
            }
        },
        'playerStore.outputMode'() {
            this.changeVolumeIfNeeded()
        },
    },
}
</script>

<style lang="scss">
@import '../scss/colors.scss';


#play_queue {
    position: fixed;
    right: 0;
    top: 0;
    bottom: calc(var(--playerHeight) - 24px);
    z-index: 1021;
    width: 100vw;
    overflow: hidden;
    background-color: var(--bs-body-bg);
    padding-bottom: 48px;
    cursor: pointer;
    box-shadow: 0 4px 12px 0 rgba(0, 0, 0, 0.15);
}

@media screen and (min-width: 768px) {
    #play_queue {
        width: 400px;
        background-color: var(--bs-body-bg);
    }

    .play-queue-header {
        display: none !important;
    }

}

.play-queue-toolbar {
    background-color: var(--bs-card-bg);
    padding-bottom: 8px;
    padding-top: 8px;
}

.play-queue-items {
    height: 100%;
    overflow-y: scroll;
}

.play-queue-item {
    padding-left: 8px;
    padding-right: 8px;
    padding-top: 8px;
    padding-bottom: 8px;
}


.play-queue-item:hover {
    background-color: var(--hover-gray);
}

.play-queue-header {
    display: flex;
}

input.full-width {
    width: 100%;
}

.vol-container {
    height: 100%;
    display: flex;
    align-items: center;
}

.vol-container.full-width {
    width: 100vh;
}
</style>:class="(fullWidth) ? 'full-width' : '' "