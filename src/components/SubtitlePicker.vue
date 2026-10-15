<template>
  <div class="container" v-show="this.appStore.showSubtitlePicker">
    <div class="modal fade" id="subtitlePickerModal" ref="subtitlePickerModal" tabindex="-1" role="dialog"
      aria-labelledby="subtitlePickerModalTitle" aria-hidden="true">
      <div class="modal-dialog modal-dialog-centered modal-lg" role="document">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title" id="subtitlePickerModalTitle" v-t="'PICK_SUBTITLE'"></h5>
            <div class="d-flex gap-2">
              <ImageButton type="file_upload" v-on:click="triggerUpload()" v-tooltip data-bs-placement="bottom" :title="$t('UPLOAD_SUBTITLE')" />
              <ImageButton type="close" v-on:click="hide()" />
            </div>
            <input type="file" ref="subtitleInput" style="display: none" accept=".srt,.sub,.vtt,.ass" @change="onSubtitleFileSelected" />
          </div>
          <div class="modal-body">
            <div class="breadcrumb-container mb-3" v-if="breadcrumb.length > 0">
                <nav aria-label="breadcrumb">
                    <ol class="breadcrumb">
                        <li v-for="(crumb, index) in breadcrumb" :key="index" class="breadcrumb-item"
                          :class="index !== breadcrumb.length - 1 ? 'text-primary clickable' : ''"
                          @click="index !== breadcrumb.length - 1 ? fetchContent(crumb.path) : null">
                            {{ crumb.title }}
                        </li>
                    </ol>
                </nav>
            </div>
            <div class="list-group subtitle-picker-list">
              <div v-if="loading" class="text-center p-5">
                <div class="spinner-border text-primary" role="status">
                  <span class="visually-hidden" v-t="'LOADING'"></span>
                </div>
              </div>
              <template v-else>
                <button v-for="item in items" :key="item.path" type="button"
                  class="list-group-item list-group-item-action d-flex align-items-center gap-3"
                  :class="{ disabled: isDisabled(item) }"
                  :disabled="isDisabled(item)"
                  @click="handleItemClick(item)">
                  <img v-lazy="$getImageUrl(item, getIconType(item))" width="24" height="24" :class="{ tint: item.isFolder || isStorage(item) }" />
                  <span class="text-truncate">{{ item.title || item.name }}</span>
                </button>
                <div v-if="items.length === 0" class="text-center p-5 text-muted" v-t="'DIRECTORY_EMPTY'"></div>
              </template>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { useAppStore } from '../stores/AppStore'
import { mapStores } from 'pinia'
import { Modal } from 'bootstrap'
import ImageButton from './ImageButton.vue'
import http from '../plugins/auth'
import { vlcApi } from '../plugins/api.js'

export default {
  name: 'SubtitlePicker',
  components: {
    ImageButton,
  },
  computed: {
    ...mapStores(useAppStore)
  },
  data() {
    return {
      modal: null,
      items: [],
      breadcrumb: [],
      loading: false
    }
  },
  methods: {
    hide() {
      this.appStore.showSubtitlePicker = false
    },
    fetchStorages() {
      this.loading = true
      this.items = []
      this.breadcrumb = []
      http.get(vlcApi.storageList)
        .then((response) => {
          this.items = response.data
          this.loading = false
        })
        .catch(() => {
          this.loading = false
        })
    },
    fetchContent(path) {
      if (path === 'root') {
        this.fetchStorages()
        return
      }
      this.loading = true
      this.items = []
      http.get(vlcApi.browseList(path))
        .then((response) => {
          this.items = response.data.content
          this.breadcrumb = response.data.breadcrumb
          this.loading = false
        })
        .catch(() => {
          this.loading = false
        })
    },
    handleItemClick(item) {
      if (item.isFolder || this.isStorage(item)) {
        this.fetchContent(item.path)
      } else if (item.fileType === "subtitle") {
        this.$root.sendMessage("pick-subtitle", null, null, null, item.path)
        this.hide()
      }
    },
    triggerUpload() {
      this.$refs.subtitleInput.click()
    },
    onSubtitleFileSelected(event) {
      const file = event.target.files[0]
      if (file) {
        this.loading = true
        this.$uploadSubtitle(file)
          .then(() => {
            this.loading = false
            this.hide()
          })
          .catch((error) => {
            this.loading = false
            this.appStore.warning = { type: "warning", message: error.response?.data || "Upload failed" }
          })
          .finally(() => {
            event.target.value = ''
          })
      }
    },
    isStorage(item) {
        return !item.isFolder && item.id !== undefined && item.path !== undefined && !item.fileType
    },
    isDisabled(item) {
        return !item.isFolder && !this.isStorage(item) && item.fileType !== 'subtitle'
    },
    getIconType(item) {
      if (item.isFolder || this.isStorage(item)) return 'folder'
      return item.fileType || 'file'
    }
  },
  watch: {
    'appStore.showSubtitlePicker'(show) {
      if (show) {
        if (!this.modal) {
          this.modal = new Modal(this.$refs.subtitlePickerModal, {
            backdrop: 'static'
          })
          this.$refs.subtitlePickerModal.addEventListener('hidden.bs.modal', () => {
            this.appStore.showSubtitlePicker = false
          })
        }
        this.fetchStorages()
        this.modal.show()
      } else {
        if (this.modal) {
          this.modal.hide()
        }
      }
    }
  }
}
</script>

<style scoped>
.subtitle-picker-list {
  max-height: 60vh;
  overflow-y: auto;
}
.tint {
    filter: var(--img-tint-active);
}
</style>
