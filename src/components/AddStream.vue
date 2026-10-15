<template>
  <div class="container" v-show="this.appStore.showAddStream">
    <div class="modal fade" id="streamModal" ref="streamModal" tabindex="-1" role="dialog"
      aria-labelledby="streamModalTitle" aria-hidden="true">
      <div class="modal-dialog modal-dialog-centered" role="document">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title" id="streamModalTitle" v-t="'NEW_STREAM'"></h5>
          </div>
          <div class="modal-body">
            <div class="container">
              <div class="d-flex align-items-center justify-content-center">
                <input type="text" id="searchInput" class="search-input" ref="searchText"
                  :placeholder="$t('ENTER_STREAM')" @keydown.enter="onPressEnter">
                <ImageButton type="send" data-bs-toggle="tooltip" data-bs-placement="bottom" v-on:click="play()" :title="$t('SEND')" />
              </div>
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
import ImageButton from '../components/ImageButton.vue'

export default {
  name: 'AddStream',
  components: {
    ImageButton,
  },
  computed: {
    ...mapStores(useAppStore)
  },
  data() {
    return {
      modal: null,
    }
  },
  methods: {
    play() {
      this.$play({ path: this.$refs.searchText.value, id: -1 }, "stream", false, false)
      this.appStore.showAddStream = false
    },
    onPressEnter() {
      this.play()
    },
    hidden() {
      this.appStore.showAddStream = false
    }
  },
  watch: {
    'appStore.showAddStream'(val) {
      if (val) {
        this.modal.show()
      } else {
        this.modal.hide()
      }
    }
  },
  mounted: function () {
    this.modal = new Modal(this.$refs.streamModal, {})
    this.$refs.streamModal.addEventListener('hidden.bs.modal', () => {
      this.appStore.showAddStream = false
    })

    if (this.appStore.showAddStream) {
      this.modal.show()
    }
  },
}
</script>
  
<style>
</style>
  