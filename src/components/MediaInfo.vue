<template>
    <div id="media_info_large" v-show="this.playerStore.mediaInfoShowing && this.playerStore.playing">
        <div class="d-flex">
            <div class="flex1">&nbsp;</div>
            <ImageButton type="close" class="small" v-on:click.stop="hide()" />
        </div>

        <div class="media-info-items">
            <div class="d-flex media-info-item volume-bar-container">
                <VolumeBar :fullWidth="true" />
            </div>
            <div class="d-flex volume-bar-container output-container">
                <ImageButton type="music_output" />
                <p class="media-info-text flex1" v-t="'AUDIO_OUTPUT'"></p>
                <!-- <p class="media-info-text">{{ this.playerStore.nowPlaying.speed }}</p> -->
                <div class="dropdown media-info-text">
                    <button class="btn btn-primary dropdown-toggle" type="button" data-bs-toggle="dropdown"
                        aria-expanded="false">
                        {{ getCurrentOutput() }}
                    </button>
                    <ul class="dropdown-menu">
                        <li class="dropdown-item">
                            <div class="nav-button d-flex btn  btn-lg medium" v-on:click="toggleOutput()"
                                v-bind:class="isOutputActive('browser') ? 'current' : ''">
                                <img :src="(`./icons/web.svg`)" v-if="this.playerStore.outputMode == 'phone'">
                                <img :src="(`./icons/checked.svg`)" v-if="this.playerStore.outputMode != 'phone'">
                                <p class="collapsed-menu-text" v-t="'PLAY_ON_BROWSER'"></p>
                            </div>
                        </li>
                        <li class="dropdown-item">
                            <div class="nav-button d-flex btn  btn-lg medium" v-on:click="toggleOutput()"
                                v-bind:class="isOutputActive('phone') ? 'current' : ''">
                                <img :src="(`./icons/mobile.svg`)" v-if="this.playerStore.outputMode == 'browser'">
                                <img :src="(`./icons/checked.svg`)" v-if="this.playerStore.outputMode != 'browser'">
                                <p class="collapsed-menu-text" v-t="'PLAY_ON_PHONE'"></p>
                            </div>
                        </li>
                    </ul>
                </div>
            </div>
            <div class="d-flex media-info-item" v-on:click.stop="startEditingTimer()">
                <ImageButton type="sleep_timer" />
                <p class="media-info-text flex1" v-t="'SLEEP_TIMER'"></p>
                <Transition name="fade">
                    <div class="media-info-text" v-if="!isEditingTimer">
                        <p class="btn btn-primary">{{ getSleepTimer() }}</p>
                    </div>
                </Transition>
            </div>
            <Transition name="expand" @after-enter="onAfterEnter">
                <div class="card mt-2" ref="sleepForm" v-if="isEditingTimer">
                    <div class="card-body">
                        <p v-t="'SLEEP_IN'" class="m-2"></p>
                        <div class="d-flex gap-2 m-2 align-items-center">
                            <div class="dropdown media-info-text">
                                <button class="btn btn-primary dropdown-toggle" type="button" data-bs-toggle="dropdown"
                                    aria-expanded="false">
                                    {{ this.formatNumber(this.timerHours) }}
                                </button>
                                <ul class="dropdown-menu  scrollable-menu">
                                    <li v-for="speed in 24" :key="speed"><a class="dropdown-item"
                                            v-on:click="timerHours = speed - 1">{{ speed - 1 }}</a></li>
                                </ul>
                            </div>
                            <span v-t="'HOURS'"></span>
                            <div class="dropdown media-info-text">
                                <button class="btn btn-primary dropdown-toggle" type="button" data-bs-toggle="dropdown"
                                    aria-expanded="false">
                                    {{ this.formatNumber(this.timerMinutes) }}
                                </button>
                                <ul class="dropdown-menu  scrollable-menu">
                                    <li v-for="speed in 60" :key="speed"><a class="dropdown-item"
                                            v-on:click="timerMinutes = speed - 1">{{ speed - 1 }}</a></li>
                                </ul>
                            </div>
                            <span v-t="'MINUTES'"></span>
                        </div>
                        <div class="form-check m-2">
                            <input class="form-check-input" type="checkbox" ref="waitCurrentMedia"
                                :checked="waitForMediaEnd" id="wait_current_media">
                            <label class="form-check-label" for="wait_current_media" v-t="'WAIT_CURRENT_MEDIA'">
                            </label>
                        </div>
                        <div class="form-check m-2">
                            <input class="form-check-input" type="checkbox" ref="resetOnInteraction"
                                :checked="resetOnInteraction" value="" id="reset_on_interaction">
                            <label class="form-check-label" for="reset_on_interaction" v-t="'RESET_ON_INTERACTION'">
                            </label>
                        </div>
                        <button type="button" class="btn btn-primary m-2" v-t="'CANCEL'"
                            v-on:click.stop="isEditingTimer = false"></button>
                        <button type="button" class="btn btn-primary m-2" v-t="'OK'"
                            v-on:click.stop="setTimer()"></button>
                    </div>
                </div>
            </Transition>
            <div class="d-flex media-info-item">
                <ImageButton type="speed" />
                <p class="media-info-text flex1" v-t="'PLAYBACK_SPEED'"></p>
                <!-- <p class="media-info-text">{{ this.playerStore.nowPlaying.speed }}</p> -->
                <div class="dropdown media-info-text">
                    <button class="btn btn-primary dropdown-toggle" type="button" data-bs-toggle="dropdown"
                        aria-expanded="false">
                        {{ this.playerStore.nowPlaying.speed }}x
                    </button>
                    <ul class="dropdown-menu">
                        <li v-for="speed in [0.25, 0.5, 0.75, 1, 1.25, 1.5, 2, 4]" :key="speed"><a class="dropdown-item"
                                v-on:click="changeSpeed(speed)">{{ speed }}x</a></li>
                    </ul>
                </div>
            </div>
            <div class="d-flex media-info-item" v-if="this.playerStore.nowPlaying.audioTracks && this.playerStore.nowPlaying.audioTracks.length > 0">
                <ImageButton type="audiotrack" />
                <p class="media-info-text flex1" v-t="'AUDIO_TRACK'"></p>
                <div class="dropdown media-info-text">
                    <button class="btn btn-primary dropdown-toggle" type="button" data-bs-toggle="dropdown"
                        aria-expanded="false">
                        {{ getCurrentAudioTrackName() }}
                    </button>
                    <ul class="dropdown-menu scrollable-menu">
                        <li v-for="track in this.playerStore.nowPlaying.audioTracks" :key="track.id">
                            <a class="dropdown-item" v-on:click="setAudioTrack(track.id)">{{ track.name }}</a>
                        </li>
                    </ul>
                </div>
            </div>
            <div class="d-flex media-info-item" v-if="this.playerStore.nowPlaying.isVideoPlaying || (this.playerStore.nowPlaying.subtitleTracks && this.playerStore.nowPlaying.subtitleTracks.length > 0)">
                <ImageButton type="subtitles" />
                <p class="media-info-text flex1" v-t="'SUBTITLE_TRACK'"></p>
                <div class="dropdown media-info-text">
                    <button class="btn btn-primary dropdown-toggle" type="button" data-bs-toggle="dropdown"
                        aria-expanded="false">
                        {{ getCurrentSubtitleTrackName() }}
                    </button>
                    <ul class="dropdown-menu scrollable-menu">
                        <li v-for="track in this.playerStore.nowPlaying.subtitleTracks" :key="track.id">
                            <a class="dropdown-item" v-on:click="setSubtitleTrack(track.id)">{{ track.name }}</a>
                        </li>
                        <li v-if="this.playerStore.nowPlaying.subtitleTracks && this.playerStore.nowPlaying.subtitleTracks.length > 0"><hr class="dropdown-divider"></li>
                        <li><a class="dropdown-item" v-on:click="pickSubtitle()">{{ $t('PICK_SUBTITLE') }}</a></li>
                    </ul>
                </div>
            </div>
            <div>
                <div class="d-flex">
                    <p class="media-info-title text-primary fw-semibold flex1" v-t="'BOOKMARKS'"></p>
                    <ImageButton type="add" v-on:click="addBookmark()" />

                </div>
                <template v-for="(bookmark, index) in this.playerStore.nowPlaying.bookmarks" :key="bookmark.time">
                    <div class="d-flex media-info-item" v-on:click="seek(bookmark.time)">
                        <ImageButton type="bookmark" v-if="isNotRenaming(bookmark)" />
                        <p class="media-info-text flex1 truncate" v-if="isNotRenaming(bookmark)">{{ bookmark.title }}
                        </p>
                        <template v-else>
                            <form class="flex1 d-flex gap-2" v-on:submit.prevent="renameBookmark(bookmark)">
                                <input class="form-control form-control-sm flex1" type="text"
                                    :data-key="(bookmark.time)" aria-label=".form-control-sm">
                                <button type="button" class="btn btn-primary" v-t="'CANCEL'"
                                    v-on:click.stop="renamingBookmark = undefined"></button>
                                <button type="button" class="btn btn-primary" v-t="'OK'"
                                    v-on:click.stop="renameBookmark(bookmark)"></button>
                            </form>
                        </template>
                        <ImageButton type="edit" v-if="isNotRenaming(bookmark)"
                            v-on:click.stop="setRenamingBookmark(bookmark, index)" />
                        <ImageButton type="delete" v-if="isNotRenaming(bookmark)"
                            v-on:click.stop="deleteBookmark(bookmark.time)" />
                    </div>
                </template>
                <EmptyView
                    v-if="typeof this.playerStore.nowPlaying.bookmarks == 'undefined' || this.playerStore.nowPlaying.bookmarks.length == 0"
                    :message="this.$t('NO_BOOKMARK')" />
            </div>
            <div
                v-if="typeof this.playerStore.nowPlaying.chapters !== 'undefined' && this.playerStore.nowPlaying.chapters.length > 0">
                <p class="media-info-title text-primary fw-semibold" v-t="'CHAPTERS'"></p>
                <template v-for="(chapter, index) in this.playerStore.nowPlaying.chapters" :key="chapter.time">
                    <div class="d-flex media-info-item" v-on:click="navigateChapter(index)">
                        <ImageButton type="chapter" />
                        <p class="media-info-text flex1 truncate">{{ chapter.title }}</p>
                    </div>
                </template>
            </div>
        </div>

    </div>
</template>

<script>
import { usePlayerStore } from '../stores/PlayerStore'
import { useAppStore } from '../stores/AppStore'
import { mapStores } from 'pinia'
import ImageButton from './ImageButton.vue'
import EmptyView from '../components/EmptyView.vue'
import VolumeBar from './VolumeBar.vue'

export default {
    components: {
        ImageButton,
        EmptyView,
        VolumeBar
    },
    data() {
        return {
            renamingBookmark: undefined,
            isEditingTimer: false,
            timerEditingValue: 0,
            timerHours: 0,
            timerMinutes: 0,
            waitForMediaEnd: false,
            resetOnInteraction: false
        }
    },
    updated: function () {
        if (this.renamingBookmark && !this.renamingBookmark.focused) {
            this.renamingBookmark.focused = true
            const dropdown = document.querySelector(`input[data-key='${this.renamingBookmark.time}']`)
            dropdown.value = this.renamingBookmark.title
            dropdown.focus()
            dropdown.select()
        }
    },
    computed: {
        ...mapStores(usePlayerStore, useAppStore),
    },
    methods: {
        hide() {
            this.playerStore.mediaInfoShowing = false
            this.renamingBookmark = undefined
            this.isEditingTimer = false
        },
        navigateChapter(index) {
            this.$root.sendMessage("play-chapter", index)
        },
        changeSpeed(speed) {
            this.$root.sendMessage("speed", null, speed)
        },
        getCurrentAudioTrackName() {
            const track = this.playerStore.nowPlaying.audioTracks?.find(t => t.id === this.playerStore.nowPlaying.currentAudioTrack)
            return track ? track.name : this.$t('NONE')
        },
        setAudioTrack(id) {
            this.$root.sendMessage("set-audio-track", null, null, null, id)
        },
        getCurrentSubtitleTrackName() {
            const track = this.playerStore.nowPlaying.subtitleTracks?.find(t => t.id === this.playerStore.nowPlaying.currentSubtitleTrack)
            return track ? track.name : this.$t('NONE')
        },
        setSubtitleTrack(id) {
            this.$root.sendMessage("set-subtitle-track", null, null, null, id)
        },
        pickSubtitle() {
            this.appStore.showSubtitlePicker = true
            this.hide()
        },
        seek(time) {
            this.$root.sendMessage("set-progress", time)
        },
        addBookmark() {
            this.$root.sendMessage("add-bookmark", null, null, this.playerStore.nowPlaying.progress)
        },
        deleteBookmark(time) {
            this.$root.sendMessage("delete-bookmark", null, null, time)
        },
        isNotRenaming(bookmark) {
            return (typeof this.renamingBookmark == 'undefined' || this.renamingBookmark.time != bookmark.time)
        },
        setRenamingBookmark(bookmark) {
            this.renamingBookmark = bookmark
        },
        renameBookmark(bookmark) {
            const dropdown = document.querySelector(`input[data-key='${bookmark.time}']`)
            this.$root.sendMessage("rename-bookmark", null, null, bookmark.time, dropdown.value)
            this.renamingBookmark = undefined
        },
        getSleepTimer() {
            let sleepTimerDate = new Date(this.playerStore.nowPlaying.sleepTimer)
            var now = new Date()
            if (sleepTimerDate < now) {
                return this.$t('NOT_SET')
            } else {
                return sleepTimerDate.toLocaleTimeString(undefined,  { hour: "2-digit", minute: "2-digit" })
            }
        },
        formatNumber(num) {
            if (num < 10) {
                return `0${num}`
            } else {
                return `${num}`
            }
        },
        // Set the correct values in the UI
        startEditingTimer() {
            this.isEditingTimer = true
            let sleepTimerDate = new Date(this.playerStore.nowPlaying.sleepTimer)
            var now = new Date()
            if (sleepTimerDate < now) {
                this.timerHours = 0
                this.timerMinutes = 0
                return
            }
            var diffMs = (sleepTimerDate - now);
            var diffHrs = Math.floor((diffMs % 86400000) / 3600000);
            var diffMins = Math.round(((diffMs % 86400000) % 3600000) / 60000);
            if (diffMins == 60) {
                diffMins = 0
                diffHrs += 1
            }
            this.timerHours = diffHrs
            this.timerMinutes = diffMins
            this.waitForMediaEnd = this.playerStore.nowPlaying.waitForMediaEnd
            this.resetOnInteraction = this.playerStore.nowPlaying.resetOnInteraction


        },
        setTimer() {
            var interval = (this.timerHours * 3600000) + (this.timerMinutes * 60000)
            this.$root.sendMessage("sleep-timer", null, null, interval, null)
            this.$root.sendMessage("sleep-timer-wait", null, null, null, `${this.$refs.waitCurrentMedia.checked}`)
            this.$root.sendMessage("sleep-timer-reset", null, null, null, `${this.$refs.resetOnInteraction.checked}`)
            this.isEditingTimer = false
        },
        onAfterEnter() {
            var r = document.querySelector(':root');
            r.style.setProperty('--timer-form-height', `${this.$refs.sleepForm.offsetHeight}px`);
        },
        toggleOutput() {
            this.playerStore.outputMode = this.playerStore.outputMode == 'phone' ? 'browser' : 'phone'
        },
        isOutputActive(outputMode) {
            return this.playerStore.outputMode == outputMode
        },
        getCurrentOutput() {
            if (this.playerStore.outputMode == 'phone') {
                return this.$t('PLAY_ON_PHONE')
            } else {
                return this.$t('PLAY_ON_BROWSER')
            }
        }
    }
}
</script>

<style lang="scss">
@import '../scss/colors.scss';



@media screen and (min-width: 1097px) {
    .volume-bar-container.d-flex {
        display: none !important;
    }
}

#media_info_large {
    position: fixed;
    left: 0;
    top: 0;
    bottom: calc(var(--playerHeight) - 24px);
    z-index: 1022;
    width: 100vw;
    overflow: hidden;
    background-color: var(--bs-body-bg);
    padding-bottom: 72px;
    box-shadow: 0 4px 12px 0 rgba(0, 0, 0, 0.15);
}

@media screen and (min-width: 768px) {
    #media_info_large {
        width: 400px;
        background-color: var(--bs-body-bg);
    }
}

.media-info-items {
    height: 100%;
    overflow-y: scroll;
}

.media-info-items .card {
    margin-left: 8px;
    margin-right: 8px;
}

.scrollable-menu {
    height: auto;
    max-height: 200px;
    overflow-x: hidden;
}

.media-info-item {
    padding-left: 8px;
    padding-right: 8px;
    padding-top: 8px;
    padding-bottom: 8px;
    height: 62px;
    cursor: pointer;
}

.media-info-item:hover {
    background-color: var(--hover-gray);
}

.media-info-title {
    padding: 16px;
}

.media-info-header {
    display: flex;
}

.media-info-text {
    align-self: center;
}

.truncate {
    overflow: hidden;
    text-overflow: ellipsis;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
}

.output-container .current {
  color: var(--bs-primary);
  filter: var(--img-tint-active);
}

:root {
    --timer-form-height: 220px;
}

// Transitions
.expand-enter-active,
.expand-leave-active {
    max-height: var(--timer-form-height);
    transition: max-height 0.25s ease-in;
    overflow: hidden;
}
.expand-enter-from,
.expand-leave-to {
    max-height: 0;
    transition: max-height 0.25s ease-out;
    overflow: hidden;
}
</style>