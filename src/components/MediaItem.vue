<template>
    <div v-if="(isCard)" class="card">
        <div v-on:click="manageClick" class="ratio clickable" v-bind:class="(mainImgClasses())">
            <img v-if="this.mediaType == 'file'" v-lazy="$getImageUrl(media, `${media.fileType}_big`)"
                class="media-img-top">
            <img v-else v-lazy="$getImageUrl(media, `${this.mediaType}_big`)" class="media-img-top" :class="this.mediaType == 'new-stream' ? 'tint' : ''">
            <div class="media-overlay" v-show="!isBrowse()">
                <img class="overlay-play" :src="(isOpenable() ? `./icons/open.svg` : `./icons/play_circle_white.svg`)"
                    width="48" />
            </div>
            <div class="media-playing-overlay media-playing-overlay-card" v-show="isCurrent">

                <div class="playing">
                    <span class="playing-bar playing-bar1" v-bind:class="(isCurrentAndPlaying) ? '' : 'paused'"></span>
                    <span class="playing-bar playing-bar2" v-bind:class="(isCurrentAndPlaying) ? '' : 'paused'"></span>
                    <span class="playing-bar playing-bar3" v-bind:class="(isCurrentAndPlaying) ? '' : 'paused'"></span>
                </div>
            </div>
            <span v-if="((mediaType == 'video' || media.fileType == 'video') && media.resolution != '')" class="resolution">{{ media.resolution
                }}</span>
            <img class="played" :src="(`./icons/played.svg`)" v-show="(media.played && (mediaType == 'video' || media.fileType == 'video') && !forceAudio)" />
            <img class="forced-audio" :src="(`./icons/audiotrack.svg`)" v-show="(forceAudio)" />
            <img class="favorite" :src="(`./icons/favorite.svg`)" v-show="(media.favorite)" />

            <div class="card-progress-container" v-show="(media.progress > 0 && !media.played)">
                <div class="card-progress full"></div>
                <div class="card-progress" v-bind:style="(getProgressStyle())"></div>
            </div>
            <div class="item-play" v-show="isPlayable()" v-on:click.stop="$play(media, this.mediaType, false, false)">
                <img class="image-button-image" :src="(`./icons/play_item.svg`)" :title="$t('PLAY')" />
            </div>
        </div>
        <div class="d-flex align-items-end">

            <div class="card-body media-text flex1">
                <h6 class="text-truncate" v-tooltip data-bs-toggle="tooltip" data-bs-placement="top" :title="(media.title)">
                    {{media.title }}
                </h6>
                <p class="card-text text-truncate subtitle"
                    v-bind:class="((!this.getDescription()) ? 'empty-desc' : '')">
                    {{ getDescription() }}
                </p>

            </div>
            <MediaItemMenu v-if="shouldShowMenu()" :media="media" :downloadable="downloadable" :mediaType="(this.forceAudio) ? 'track' : this.mediaType" />
        </div>
    </div>
    <div v-else class="d-flex clickable media-img-list-tr">
        <div v-on:click="manageClick" class="ratio media-img-container" v-bind:class="(mainImgClasses())">
            <img v-if="this.mediaType == 'file'" v-lazy="$getImageUrl(media, media.fileType)" class="media-img-list">
            <img v-else v-lazy="$getImageUrl(media, this.mediaType)" class="media-img-list" :class="this.mediaType == 'new-stream' ? 'tint' : ''">
            <div class="media-overlay" v-show="!isBrowse() && !isCurrent">
                <img class="overlay-play" :src="(isOpenable() ? `./icons/open.svg` : `./icons/play_circle_white.svg`)"
                    width="32" />
                </div>
            <div class="media-playing-overlay" v-show="isCurrent">

                <div class="playing">
                    <span class="playing-bar playing-bar1" v-bind:class="(isCurrentAndPlaying) ? '' : 'paused'"></span>
                    <span class="playing-bar playing-bar2" v-bind:class="(isCurrentAndPlaying) ? '' : 'paused'"></span>
                    <span class="playing-bar playing-bar3" v-bind:class="(isCurrentAndPlaying) ? '' : 'paused'"></span>
                </div>
            </div>
            <span v-if="((mediaType == 'video' || media.fileType == 'video') && media.resolution != '')" class="resolution">{{ media.resolution
                }}</span>
            <img class="played" :src="(`./icons/played.svg`)" v-show="(media.played && (mediaType == 'video' || media.fileType == 'video') && !forceAudio)" />
            <img class="forced-audio" :src="(`./icons/audiotrack.svg`)" v-show="(forceAudio)" />
            <img class="favorite" :src="(`./icons/favorite.svg`)" v-show="(media.favorite)" />
        </div>
        <div v-on:click="manageClick" class="card-body media-text flex1">
            <h6 class="card-title text-truncate" v-tooltip data-bs-toggle="tooltip" data-bs-placement="top" :title="(media.title)">{{ media.title }}</h6>
            <p class="card-text text-truncate subtitle"
                v-bind:class="((!this.getDescription()) ? 'empty-desc empty-desc-list' : '')">{{ getDescription() }}</p>
            <div class="list-progress-container" v-show="(media.progress > 0 && !media.played)">
                <div class="list-progress full"></div>
                <div class="list-progress" v-bind:style="(getProgressStyle())"></div>
            </div>
        </div>
        <MediaItemMenu v-if="shouldShowMenu()" :media="media" :downloadable="downloadable"  :mediaType="(this.forceAudio) ? 'track' : this.mediaType" />
    </div>
</template>

<script>
import MediaItemMenu from './MediaItemMenu.vue'
import { useAddToPlaylistStore } from '../stores/AddToPlaylistStore'
import { usePlayerStore } from '../stores/PlayerStore'
import { mapStores } from 'pinia'
export default {
    computed: {
        ...mapStores(useAddToPlaylistStore, usePlayerStore)
    },
    components: {
        MediaItemMenu,
    },
    data() {
        return {
            isCurrentAndPlaying: false,
            isCurrent: false,
            lastCurrent: undefined
        }
    },
    methods: {
        mainImgClasses() {
            if (this.mediaType.startsWith('video')) return 'ratio-16x9 video audio-img-container'
            if (this.isBrowse() || this.mediaType == "file") return 'ratio-1x1'
            return 'ratio-1x1 audio-img-container'
        },
        getProgressStyle() {
            if ((this.mediaType.startsWith('video')  || this.media.fileType == 'video') && this.media.progress > 0 && this.media.duration > 0) {
                return `width: ${this.media.progress * 100 / this.media.duration}%`
            }
            return ''
        },
        isBrowse() {
            return (this.mediaType == 'folder' || this.mediaType == 'network' || this.mediaType == 'stream' || this.mediaType == 'new-stream')
        },
        checkCurrent() {
            this.isCurrent = this.playerStore.nowPlaying.uri == this.media.path
            this.isCurrentAndPlaying = (this.playerStore.nowPlaying.uri == this.media.path && this.playerStore.nowPlaying.playing)
        },
        isPlayable() {
            return (this.mediaType == 'album' || this.mediaType == 'artist')
        },
        isOpenable() {
            return ['video-group', 'video-folder', 'artist', 'album', 'playlist', 'genre'].includes(this.mediaType)
        },
        shouldShowMenu() {
            return this.mediaType != 'new-stream'
        },
        getDescription() {
            if (this.mediaType == 'video' && !this.forceAudio) {
                return `${this.$readableDuration(this.media.duration)}`
            } else {
                if (this.isCard && this.media.artist == " ") return '\xa0'
                return this.media.artist
            }
        },
        manageClick() {
            if (this.mediaType == 'genre') {
                this.$router.push({ name: 'GenreDetails', params: { genreId: this.media.id } })
            } else if (this.mediaType == 'playlist') {
                this.$router.push({ name: 'PlaylistDetails', params: { playlistId: this.media.id } })
            } else if (this.mediaType == 'album') {
                this.$router.push({ name: 'AlbumDetails', params: { albumId: this.media.id } })
            } else if (this.mediaType == 'artist') {
                this.$router.push({ name: 'ArtistDetails', params: { artistId: this.media.id } })
            } else if (this.mediaType == 'video-group') {
                this.$router.push({ name: 'VideoGroupList', params: { groupId: this.media.id } })
            } else if (this.mediaType == 'video-folder') {
                this.$router.push({ name: 'VideoFolderList', params: { folderId: this.media.id } })
            } else if (['folder', 'storage', 'network'].includes(this.mediaType)) {
                this.$router.push({ name: 'BrowseChild', params: { browseId: this.media.path } })
            } else {
                this.$play(this.media, this.mediaType)
            }
        },
        addToPlaylist(media, mediaType) {
            media.mediaType = mediaType
            this.addToPlaylistStore.media = media
        },
    },
    props: {
        media: Object,
        downloadable: Boolean,
        mediaType: String,
        forceAudio: Boolean,
        isCard: Boolean,
        hideOverflow: {
            type: Boolean,
            default: false
        }
    },
    mounted: function () {
        this.playerStore.$subscribe((mutation, state) => {
            if (!state.playing) {
                this.isCurrent = false
                this.isCurrentAndPlaying = false
            } else if (this.lastCurrent == undefined || this.lastCurrent.uri != state.uri || this.lastCurrent.playing != state.playing) {
                this.checkCurrent()
            }
            this.lastCurrent = state.nowPlaying
        })
        this.checkCurrent()
    },
}
</script>


<style lang='scss'>
@import '../scss/colors.scss';

.empty-desc {
    background: var(--hover-gray);
    width: 100%;
    height: 16px;
    margin-top: 4px;
    margin-bottom: 4px !important;
    border-radius: 4px;
}

.empty-desc-list {
    max-width: 250px;
}

.empty-desc:after {
    content: "";
    width: 100%;
    height: 100%;
    top: 0;
    left: 0;
    display: block;
    animation: slide 1.5s infinite;
    background: linear-gradient(90deg, rgba(0, 0, 0, 0) 0%, var(--placeholder-animation) 50%, rgba(0, 0, 0, 0) 100%);
    float: left;
}

.card-progress-container {
    height: 14px;
    position: absolute;
    bottom: 0;
    top: auto;
    overflow: hidden;
}

.card-progress {
    height: 4px;
    background-color: var(--bs-primary);
    position: absolute;
    bottom: 0;
}

.card-progress.full {
    width: 100%;
    background-color: var(--progress-background);
}

.list-progress-container {
    height: 4px;
    position: relative;
    width: 100%;
    bottom: 0;
    top: auto;
    overflow: hidden;
}

.list-progress {
    height: 4px;
    background-color: var(--bs-primary);
    position: absolute;
    bottom: 0;
    border-radius: 2px;
}

.list-progress.full {
    width: 100%;
    background-color: var(--progress-background);
    border-radius: 2px;
}

.item-play {
    position: absolute;
    bottom: 0px;
    right: 0px;
    top: auto;
    left: auto;
    margin-bottom: 8px;
    margin-right: 8px;
    width: 32px;
    height: 32px;
    background-color: var(--bs-card-bg);
    border-radius: 50%;
    border: 1px solid var(--bs-border-color-translucent);
    padding: 4px;
    display: flex;
}

.item-play:hover {
    background-color: var(--bs-primary);
    border: none;
}

.item-play > img {
    filter: var(--play-button-fg);
}

.item-play:hover > img {
    filter: invert(100%) sepia(6%) saturate(28%) hue-rotate(322deg) brightness(106%) contrast(100%);
}

.tint[lazy="loaded"] {
    filter: var(--img-tint-active);
}

/* animation */

@keyframes slide {
    0% {
        transform: translateX(-100%);
    }

    100% {
        transform: translateX(100%);
    }
}
</style>