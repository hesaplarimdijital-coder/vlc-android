<template>
  <div class="alert app-alert alert-warning alert-dismissible fade d-inline-block" role="alert" ref="warning"
    :class="[this.appStore.warning !== undefined && this.appStore.warning.type == 'warning' ? 'show' : '', this.playerStore.playing ? 'player-showing' : '']">
    <p v-if="this.appStore.warning !== undefined">{{ this.appStore.warning.message }}</p>
    <button type="button" v-on:click.stop="hideWarning()" class="btn-close" aria-label="Close"></button>
  </div>

  <div class="alert app-alert alert-success alert-dismissible fade d-inline-block" role="alert" ref="warning"
    :class="[this.appStore.warning !== undefined && this.appStore.warning.type == 'message' ? 'show' : '', this.playerStore.playing ? 'player-showing' : '']">
    <p v-if="this.appStore.warning !== undefined">{{ this.appStore.warning.message }}</p>
    <button type="button" v-on:click.stop="hideWarning()" class="btn-close" aria-label="Close"></button>
  </div>
  <div class="modal fade" id="waitingConfirmation"  ref="waitingConfirmation" data-bs-backdrop="static" data-bs-keyboard="false" tabindex="-1" aria-labelledby="waitingConfirmationLabel" aria-hidden="true">
  <div class="modal-dialog">
    <div class="modal-content">
      <div class="modal-header">
        <h1 class="modal-title fs-5" id="waitingConfirmationLabel" ref="waitingConfirmationMedia">{{ modalTitle }}</h1>
      </div>
      <div class="modal-body">
        <p v-t="'CONFIRM_RESUME'"></p>
        <div class="form-check mt-2">
          <input class="form-check-input" id="applyPlaylist" ref="applyPlaylist" type="checkbox" value="">
          <label for="applyPlaylist" class="form-check-label" v-t="'APPLY_PLAYQUEUE'"></label>
        </div>

      </div>
      <div class="modal-footer">
        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal" v-t="'NO'" v-on:click.stop="$resume(false, this.$refs.applyPlaylist.checked)"></button>
        <button type="button" class="btn btn-primary" v-t="'RESUME'" v-on:click.stop="$resume(true, this.$refs.applyPlaylist.checked)" ></button>
      </div>
    </div>
  </div>
</div>
</template>
  
  <script>
  import { useAppStore } from '../stores/AppStore'
  import { usePlayerStore } from '../stores/PlayerStore'
  import { mapStores } from 'pinia'
  import { Modal } from 'bootstrap'
  import logger from '../plugins/logger'

  export default {
    name: 'App',
    components: {
    },
    data() {
      return {
        modal: null,
        modalTitle: ""
      }
    },
    computed: {
      ...mapStores(useAppStore, usePlayerStore)
    },
    methods: {
      hideWarning() {
        this.appStore.warning = undefined
      }
    },
    mounted: function () {
      this.playerStore.$subscribe((mutation, state) => {
        if (state.waitingConfirmation) {
          if (Object.hasOwn(this.playerStore.waitingConfirmation, 'consumed') && !this.playerStore.waitingConfirmation.consumed) {
            logger.info(`Received resume confirmation: ${this.playerStore.waitingConfirmation}`)
            this.modal = new Modal(document.getElementById('waitingConfirmation'), {})
            this.modalTitle = this.playerStore.waitingConfirmation.title
            this.modal.show()
          } else {
            if (this.modal) this.modal.hide()
            this.playerStore.waitingConfirmation = undefined
          }
        }
      })

    },
  }
  </script>
  
  <style>
  </style>
  