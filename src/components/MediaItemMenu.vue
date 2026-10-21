<template>
    <div class="dropdown dropstart overlay-more-container" v-show="!this.hideOverflow">
        <ImageButton type="more_vert" id="dropdownMenuButton1" data-bs-toggle="dropdown" aria-expanded="false" />
        <ul class="dropdown-menu media-more" aria-labelledby="dropdownMenuButton1">
            <li> <span v-on:click="$play(media, this.mediaType, false, false)" class="dropdown-item"
                    v-t="'PLAY'"></span> </li>
            <li> <span v-on:click="$play(media, this.mediaType, true, false)" class="dropdown-item"
                    v-t="'APPEND'"></span> </li>
            <li> <span v-if="(mediaType == 'video')" v-on:click="$play(media, this.mediaType, false, true)"
                    class="dropdown-item" v-t="'PLAY_AS_AUDIO'"></span> </li>
            <li v-if="(!isFileOrFolder())">
                <hr class="dropdown-divider">
            </li>
            <li v-if="(!isFileOrFolder() && !media.favorite)">
                <span v-on:click="$favorite(media, this.mediaType, true)" class="dropdown-item"
                    v-t="'ADD_FAVORITE'"></span>
            </li>
            <li v-if="(!isFileOrFolder() && media.favorite)">
                <span v-on:click="$favorite(media, this.mediaType, false)" class="dropdown-item"
                    v-t="'REMOVE_FAVORITE'"></span>
            </li>
            <li v-if="(!isFileOrFolder() && mediaType != 'playlist')">
                <span v-on:click="addToPlaylist(media, mediaType)" class="dropdown-item" v-t="'ADD_PLAYLIST'"></span>
            </li>
            <template v-if="(!isFileOrFolder())">
                <li>
                    <hr class="dropdown-divider">
                </li>
                <li>
                    <span v-on:click="$download(media, this.mediaType, this.downloadable)" class="dropdown-item"
                        v-t="'DOWNLOAD'"></span>
                </li>
            </template>
        </ul>
    </div>
</template>

<script>
import ImageButton from './ImageButton.vue'
import { useAddToPlaylistStore } from '../stores/AddToPlaylistStore'
import { mapStores } from 'pinia'
export default {
    computed: {
        ...mapStores(useAddToPlaylistStore)
    },
    components: {
        ImageButton,
    },
    methods: {
        addToPlaylist(media, mediaType) {
            media.mediaType = mediaType
            this.addToPlaylistStore.mediaType = mediaType
            this.addToPlaylistStore.media = media
        },
        isFileOrFolder() {
            return this.mediaType == 'file' || this.mediaType == 'folder'
        }
    },
    props: {
        media: Object,
        downloadable: Boolean,
        mediaType: String,
        hideOverflow: {
            type: Boolean,
            default: false
        }
    },
}
</script>


<style lang='scss'>
@import '../scss/colors.scss';
</style>