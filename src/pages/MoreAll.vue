<template>
    <div class="container" @ml-refresh-needed="fetchStreams">
        <h5 class="media-content text-primary" v-t="'STREAMS'"></h5>
        <div v-if="!streamsLoaded" class="spinner-border text-primary mt-3" role="status">
            <span class="visually-hidden" v-t="'LOADING'"></span>
        </div>
        <template v-else>
            <div v-if="this.appStore.displayType[this.$route.name]" class="row gx-3 gy-3 media-list">
                <MediaItem :isCard="false" :media="newStream" :mediaType="'new-stream'" :hideOverflow="true" />
                <template v-for="stream in streams" :key="stream.id">
                    <MediaItem :isCard="false" :media="stream" :mediaType="'stream'" :hideOverflow="true" />
                </template>
            </div>
            <div v-else class="row gx-3 gy-3 media-content">
                <div class="col-md-3 col-lg-2 col-sm-4 col-6">
                    <MediaItem :isCard="true" :media="newStream" :mediaType="'new-stream'" :hideOverflow="true" />
                </div>
                <div class="col-md-3 col-lg-2 col-sm-4 col-6" v-for="stream in streams" :key="stream.id">
                    <MediaItem :isCard="true" :media="stream" :mediaType="'stream'" :hideOverflow="true" />
                </div>
            </div>
        </template>

        <h5 class="media-content text-primary" v-t="'HISTORY'"></h5>
        <div v-if="!historyLoaded" class="spinner-border text-primary mt-3" role="status">
            <span class="visually-hidden"  v-t="'LOADING'"></span>
        </div>
        <div v-else-if="this.history.length == 0">
            <EmptyView :message="getHistoryEmptyText()" />
        </div>
        <template v-else>
            <div v-if="this.appStore.displayType[this.$route.name]" class="row gx-3 gy-3 media-list">
                <template v-for="historyItem in history" :key="historyItem.id">
                    <MediaItem :isCard="false" :media="historyItem"
                            :mediaType="'video'" :forceAudio="historyItem.fileType != 'video'" :hideOverflow="true" />
                </template>
            </div>
            <div v-else class="row gx-3 gy-3 media-content">
                <div class="col-md-3 col-lg-2 col-sm-4 col-6" v-for="historyItem in history" :key="historyItem.id">
                        <MediaItem :isCard="true" :media="historyItem"
                            :mediaType="'video'" :forceAudio="historyItem.fileType != 'video'" :hideOverflow="true" />
                    </div>
            </div>
        </template>

    </div>

</template>

<script>

import { useAppStore } from '../stores/AppStore'
import { useBrowserStore } from '../stores/BrowserStore'
import { mapStores } from 'pinia'
import http from '../plugins/auth'
import { vlcApi } from '../plugins/api.js'
import MediaItem from '../components/MediaItem.vue'
import EmptyView from '../components/EmptyView.vue'

export default {
    computed: {
        ...mapStores(useAppStore, ['needRefresh'], useBrowserStore)
    },
    components: {
        MediaItem,
        EmptyView,
    },
    data() {
        return {
            streams: [],
            history: [],
            streamsLoaded: false,
            historyLoaded: false,
            forbidden: false,
            historyForbidden: false,
            newStream: {
                "artist": " ",
                "artworkURL": "",
                "id": -1,
                "isFolder": true,
                "length": 0,
                "path": "",
                "playing": false,
                "resolution": "",
                "title": this.$t('NEW_STREAM')
            }
        }
    },
    watch: {
        needRefresh() {
            this.fetchStreams()
        },
    },
    methods: {
        fetchStreams(delayLoading = false) {
            let component = this
            component.appStore.delayLoading(delayLoading, true)
            http.get(vlcApi.streamList)
                .catch(function (error) {
                    if (error.response !== undefined && error.response.status == 403) {
                        component.forbidden = true;
                    }
                    component.appStore.delayLoading(delayLoading, false)
                })
                .then((response) => {
                    this.streamsLoaded = true;
                    if (response) {
                        component.forbidden = false;
                        this.streams = response.data
                    }
                    component.appStore.delayLoading(delayLoading, false)
                });
        },
        fetchHistory(delayLoading = false) {
            let component = this
            component.appStore.delayLoading(delayLoading, true)
            http.get(vlcApi.history)
                .catch(function (error) {
                    if (error.response !== undefined && error.response.status == 403) {
                        component.historyForbidden = true;
                    }
                    component.appStore.delayLoading(delayLoading, false)
                })
                .then((response) => {
                    this.historyLoaded = true;
                    if (response) {
                        component.historyForbidden = false;
                        this.history = response.data
                    }
                    component.appStore.delayLoading(delayLoading, false)
                });
        },
        getFileEmptyText() {
            if (this.forbidden) return this.$t('FORBIDDEN')
            return this.$t('DIRECTORY_EMPTY')
        },
        getHistoryEmptyText() {
            if (this.historyForbidden) return this.$t('FORBIDDEN')
            return this.$t('DIRECTORY_EMPTY')
        }
    },
    created: function () {
        this.fetchStreams();
        this.fetchHistory();
    },
    mounted: function () {
        this.appStore.$subscribe((mutation, state) => {
            if (state.needRefresh) {
                this.$log.log(`A stream has changed!! Reloading with: ${JSON.stringify(state)}`)
                this.fetchStreams(true);
                this.fetchHistory(true);
                this.appStore.needRefresh = false
            }
        })
        this.browserStore.$subscribe((mutation, state) => {
            state.descriptions.forEach(description => {
                this.storages.forEach(item => {
                    if (item.path == description.path) {
                        item.artist = description.description
                    }
                })
            })
        })
    },
}
</script>

<style lang='scss'>
@import '../scss/colors.scss';
</style>
