<template>
    <div v-if="loaded && this.videos.length !== 0" class="container">
        <div v-if="this.appStore.displayType[this.$route.name]" class="row gx-3 gy-3 media-list">
            <template v-for="video in videos" :key="video.id">
                <MediaItem :isCard="false" :media="video" :downloadable="getMediaType(video) == 'video'" :mediaType="getMediaType(video)" />
            </template>
        </div>
        <div v-else class="row gx-3 gy-3 media-content">
            <div class="col-md-3 col-sm-4 col-6" v-for="video in videos" :key="video.id">
                <MediaItem :isCard="true" :media="video" :downloadable="getMediaType(video) == 'video'" :mediaType="getMediaType(video)" />
            </div>
        </div>
    </div>
    <div v-else-if="loaded" class="empty-view-container">
        <EmptyView :message="getEmptyText()" />
    </div>
</template>

<script>

import http from '../plugins/auth'
import { vlcApi } from '../plugins/api.js'
import { useAppStore } from '../stores/AppStore'
import { mapStores } from 'pinia'
import EmptyView from '../components/EmptyView.vue'
import MediaItem from '../components/MediaItem.vue'

export default {
    components: {
        EmptyView,
        MediaItem,
    },
    computed: {
        ...mapStores(useAppStore)
    },
    data() {
        return {
            videos: [],
            loaded: false,
            forbidden: false,
            videoGrouping: 0
        }
    },
    methods: {
        fetchVideos(delayLoading = false) {
            let component = this
            component.appStore.delayLoading(delayLoading, true)
            this.videoGrouping = this.appStore.videoGrouping
            let groupId = this.$route.params.groupId
            let folderId = this.$route.params.folderId
            http.get(vlcApi.videoList(this.appStore.videoGrouping, groupId, folderId))
                .catch(function (error) {
                    component.appStore.delayLoading(delayLoading, false)
                    if (error.response !== undefined && error.response.status == 403) {
                        component.forbidden = true;
                    }
                })
                .then((response) => {
                    this.loaded = true;
                    if (response) {
                        component.forbidden = false;
                        this.videos = response.data.content
                        component.appStore.delayLoading(delayLoading, false)
                        component.appStore.title = response.data.item
                    }
                });
        },
        getEmptyText() {
            if (this.forbidden) return this.$t('FORBIDDEN')
            return this.$t('NO_MEDIA')
        },
        getMediaType(video) {
            if (video.fileType) return video.fileType
            return "video"
        }
    },
    watch: {
        $route() {
            this.loaded = false
            this.fetchVideos()
        }
    },
    mounted: function () {
        this.appStore.$subscribe((mutation, state) => {
            this.$log.log(`Something changed in the app store: ${JSON.stringify(state)}`)
            if (state.needRefresh) {
                this.fetchVideos(true);
                this.appStore.needRefresh = false
            }
            if (this.videoGrouping != this.appStore.videoGrouping) {
                this.fetchVideos();
                this.$log.log(`Grouping changed to : ${this.appStore.videoGrouping}`)
            }
        })

    },
    created: function () {
        this.fetchVideos();
    }
}
</script>


<style lang="scss">
@import '../scss/colors.scss';

.ratio>.resolution {
    position: absolute;
    top: 8px;
    left: 8px;
    width: auto;
    height: auto;
    color: #fff;
    background: rgba(0, 0, 0, 0.6);
    padding: 4px 6px;
    border-radius: 2px;
    font-size: 0.6rem;
}

.ratio>.played {
    position: absolute;
    top: 8px;
    right: 8px;
    left: auto;
    width: 24px;
    height: 24px;
    padding: 4px;
    color: #fff;
    background: rgba(0, 0, 0, 0.6);
    border-radius: 2px;
    font-size: 0.6rem;
}

.ratio>.forced-audio {
    position: absolute;
    top: 0px;
    bottom: 0px;
    left: auto;
    right: 0px;
    width: 48px;
    height: 100%;
    padding: 4px;
    color: #fff;
    background: rgba(0, 0, 0, 0.6);
    border-radius: 2px;
    font-size: 0.6rem;
}

.media-img-list-tr .ratio>.forced-audio {
    top: 0px;
    right: 0px;
    border-radius: 0px;
    border-top-right-radius: 6px;
    border-bottom-right-radius: 6px;
    width: 32px;
}

.ratio>.favorite {
    position: absolute;
    bottom: 8px;
    left: 8px;
    top: auto;
    width: 24px;
    height: 24px;
    padding: 4px;
    color: #fff;
    background: rgba(0, 0, 0, 0.6);
    border-radius: 2px;
    font-size: 0.6rem;
}


.media-img-list-tr .ratio>.resolution {
    top: 0px;
    left: 0px;
    border-radius: 0px;
    border-bottom-right-radius: 2px;
    border-top-left-radius: 6px;
}

.media-img-list-tr .ratio>.played {
    top: 0px;
    right: 0px;
    border-radius: 0px;
    border-bottom-left-radius: 2px;
    border-top-right-radius: 6px;
}



.media-img-list-tr .ratio>.favorite {
    bottom: 0px;
    left: 0px;
    border-radius: 0px;
    border-top-right-radius: 2px;
    border-bottom-left-radius: 6px;
}


</style>
