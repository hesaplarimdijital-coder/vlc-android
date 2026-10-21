<template>
    <div class="container">
        <h5 class="media-content text-primary mb-4" v-t="'FEEDBACK'" v-show="this.step == 0"></h5>

        <Transition name="slide-fade">
            <div class="border rounded-2 p-3 mb-2 clickable feedback-entry" v-show="this.emailOpened == false" v-on:click="openForum()">
                <img :src="(`./icons/forum.svg`)" class="feedback-entry-img me-3" />
                <div class="card-body media-text flex1">
                    <h6 class="card-title text-truncate" v-t="'FEEDBACK_FORUM'"></h6>
                    <p class="card-text text-truncate subtitle">{{ generateSubtitle("forum") }}</p>
                </div>
            </div>
        </Transition>

        <Transition name="slide-fade">
            <div class="border rounded-2 p-3 mb-2 clickable feedback-entry" v-show="this.emailOpened == false" v-on:click="openDocumentation()">
                <img :src="(`./icons/documentation.svg`)" class="feedback-entry-img me-3" />
                <div class="card-body media-text flex1">
                    <h6 class="card-title text-truncate" v-t="'FEEDBACK_DOC'"></h6>
                    <p class="card-text text-truncate subtitle" v-t="'GET_HELP'"></p>
                </div>
            </div>
        </Transition>

        <div class="border rounded-2 p-3 mb-2 clickable feedback-entry"
            v-on:click="this.emailOpened = !this.emailOpened">
            <img :src="(`./icons/email.svg`)" class="feedback-entry-img me-3" />
            <div class="card-body media-text flex1">
                <h6 class="card-title text-truncate" v-t="'FEEDBACK_SUBTITLE'"></h6>
                <p class="card-text text-truncate subtitle">{{ generateSubtitle("email") }}</p>
            </div>
        </div>

        <Transition name="slide-fade">
            <div class="mt-5" v-show="this.emailOpened && this.step == 1">
                <h4 class="form-label mt-5 fit-width-center" ref="waitingMessage"></h4>
            </div>
        </Transition>

        <Transition name="opacity">

            <div class="modal-body mt-4 border rounded-2 p-3" v-show="this.emailOpened && this.step == 0">

                <div class="mb-3">
                    <label for="feedback_type" class="form-label" v-t="'FEEDBACK_TYPE'"></label>
                    <div class="dropdown" id="feedback_type">
                        <button class="btn btn-primary dropdown-toggle" type="button" data-bs-toggle="dropdown"
                            aria-expanded="false" v-t="this.reasons[this.reason]">
                        </button>
                        <ul class="dropdown-menu">
                            <li><a class="dropdown-item cursor-pointer" v-t="'FEEDBACK_HELP'"
                                    v-on:click="this.changeReason(0)"></a></li>
                            <li><a class="dropdown-item cursor-pointer" v-t="'FEEDBACK_FEATURE'"
                                    v-on:click="this.changeReason(1)"></a></li>
                            <li><a class="dropdown-item cursor-pointer" v-t="'FEEDBACK_BUG'"
                                    v-on:click="this.changeReason(2)"></a></li>
                            <li><a class="dropdown-item cursor-pointer" v-t="'FEEDBACK_CRASH'"
                                    v-on:click="this.changeReason(3)"></a></li>
                        </ul>
                    </div>
                </div>
                <div class="mb-3">
                    <label for="feedbackSubject" class="form-label" v-t="'FEEDBACK_SUBJECT'"></label>
                    <input type="text" class="form-control" id="feedbackSubject" ref="feedbackSubject">
                </div>
                <div class="mb-3">
                    <label for="feedbackMessage" class="form-label" v-t="'FEEDBACK_MESSAGE'"></label>
                    <textarea class="form-control" id="feedbackMessage" rows="3" ref="feedbackMessage"></textarea>
                </div>

                <div class="form-check" v-show="this.reason > 1">
                    <input class="form-check-input" type="checkbox" value="" id="includeML" ref="includeML"
                        v-bind:checked="this.reason == 3">
                    <label class="form-check-label" for="includeML" v-t="'FEEDBACK_MEDIALIBRARY'"> </label>
                </div>
                <div class="form-check" v-show="this.reason > 1">
                    <input class="form-check-input" type="checkbox" value="" id="includeLogs" ref="includeLogs"
                        v-bind:checked="this.reason == 3">
                    <label class="form-check-label" for="includeLogs" v-t="'FEEDBACK_LOGS'"> </label>
                </div>

                <button class="btn btn-primary mt-4" v-t="'FEEDBACK_GENERATE'" v-on:click="sendForm()"></button>
            </div>
        </Transition>
        <Transition name="slide-fade">
            <div class="modal-body mt-4 border rounded-2 mt-2 mb-2 p-3" v-show="this.emailOpened && this.step == 2">
                <p class="h4 media-content text-primary text-center fit-width-center mb-2"
                    v-t="'FEEDBACK_GENERATED_TITLE'">
                </p>
                <p class="mb-3 border rounded-1 text-center p-3 fit-width-center"
                    v-t="'FEEDBACK_GENERATED_EXPLANATION'">
                </p>
                <div class="d-flex align-items-center">
                    <input type="text" disabled class="form-control mb-3 mt-3" id="feedbackResultEmail"
                        ref="feedbackResultEmail">
                    <ImageButton type="copy" v-on:click="(copy(this.$refs.feedbackResultEmail))" />
                </div>
                <div class="d-flex align-items-center">
                    <input type="text" disabled class="form-control mb-3" id="feedbackResultSubject"
                        ref="feedbackResultSubject">
                    <ImageButton type="copy" v-on:click="(copy(this.$refs.feedbackResultSubject))" />
                </div>
                <div class="d-flex align-items-center">
                    <textarea disabled class="form-control mb-3" id="feedbackResultMessage" rows="3"
                        ref="feedbackResultMessage"></textarea>
                    <ImageButton type="copy" v-on:click="(copy(this.$refs.feedbackResultMessage))" />
                </div>

                <div class="alert alert-warning fit-width-center" v-show="this.fileDownloaded">
                    <p v-t="'FEEDBACK_GENERATED_FILE_EXPLANATION'"></p>
                </div>

                <button class="btn btn-primary mt-4" v-t="'SEND'" v-on:click="mailTo()"></button>

            </div>
        </Transition>
    </div>
</template>

<script>


import http from '../plugins/auth'
import { vlcApi } from '../plugins/api.js'
import { useAppStore } from '../stores/AppStore'
import { mapStores } from 'pinia'
import ImageButton from '../components/ImageButton.vue'

export default {
    components: {
        ImageButton,
    },
    computed: {
        ...mapStores(useAppStore)
    },
    data() {
        return {
            emailOpened: false,
            step: 0,
            reason: 0,
            fileDownloaded: false,
            reasons: ['FEEDBACK_HELP', 'FEEDBACK_FEATURE', 'FEEDBACK_BUG', 'FEEDBACK_CRASH']
        }
    },
    methods: {
        changeReason(index) {
            this.reason = index
        },
        mailTo() {
            let message = this.$refs.feedbackResultMessage.value.replace(/(?:\r\n|\r|\n)/g, '%0D%0A')
            window.open(`mailto:${this.$refs.feedbackResultEmail.value}?subject=${this.$refs.feedbackResultSubject.value}&body=${message}`);
        },
        copy(copyText) {
            copyText.select()
            copyText.setSelectionRange(0, 99999)
            navigator.clipboard.writeText(copyText.value)
            this.appStore.warning = { type: "warning", message: this.$t('COPIED') }
            setTimeout(() => {
                this.appStore.warning = undefined
            }, 1500);
        },
        generateSubtitle(type) {
            if (type == "forum") return `${this.$t('FEEDBACK')} · ${this.$t('REPORT_A_BUG')}`
            if (type == "email") return `${this.$t('FEEDBACK')} · ${this.$t('GET_HELP')} · ${this.$t('REPORT_A_BUG')}`
        },
        openForum() {
            window.open(this.$t('FEEDBACK_FORUM_URL'), '_blank').focus();
        },
        openDocumentation() {
            window.open(this.$t('FEEDBACK_DOC_URL'), '_blank').focus();
        },
        sendForm() {
            let component = this

            let body = {
                type: this.reason,
                includeML: this.$refs.includeML.checked,
                includeLogs: this.$refs.includeLogs.checked,
                subject: this.$refs.feedbackSubject.value,
                message: this.$refs.feedbackMessage.value,
            }
            if (this.$refs.includeML.checked || this.$refs.includeLogs.checked) {
                this.$refs.waitingMessage.innerText = this.$t('FEEDBACK_GENERATING_LOGS')
            } else {
                this.$refs.waitingMessage.innerText = this.$t('FEEDBACK_PLEASE_WAIT')
            }

            this.step = 1
            this.appStore.loading = true

            http.post(vlcApi.feedbackForm, body, {
                headers: {
                    'Content-Type': 'multipart/form-data'
                }
            })
                .catch(function (error) {
                    component.step = 0
                    component.appStore.loading = false
                    if (error.response !== undefined && error.response.status == 403) {
                        component.appStore.warning = { type: "warning", message: component.$t('LOGS_CONTROL_FORBIDDEN') }
                    }
                })
                .then((response) => {
                    component.appStore.loading = false
                    if (response) {
                        this.step = 2
                        component.forbidden = false
                        this.storages = response.data
                        if (response.data.file) {
                            window.location.href = vlcApi.download(response.data.file)
                            this.fileDownloaded = true
                        }
                        this.$refs.feedbackResultEmail.value = response.data.mail
                        this.$refs.feedbackResultSubject.value = response.data.subject
                        this.$refs.feedbackResultMessage.value = response.data.message
                    }
                });
        },
    }
}
</script>

<style lang='scss'>
@import '../scss/colors.scss';

.fit-width-center {
    width: fit-content;
    margin: auto;
}

.feedback-entry:hover {
    background-color: var(--list-hover-bg);
}

.feedback-entry {
    margin: 0;
    padding: 0.5rem;
    border-bottom: 1px solid var(--bs-border-color);
    align-items: center;
    display: flex;
}

.feedback-entry-img {
    width: 32px;
    height: 32px;
    object-fit: cover;
    border-radius: 6px;
}

//Transitions

.slide-fade-enter-active {
  transition: all 0.3s ease-out;
}

.slide-fade-leave-active {
  transition: all 0.3s cubic-bezier(1, 0.5, 0.8, 1);
}

.slide-fade-enter-from,
.slide-fade-leave-to {
  transform: translateY(-78px);
  opacity: 0;
}

.opacity-enter-active,
.opacity-leave-active {
  transition: opacity 0.5s ease;
}

.opacity-enter-from,
.opacity-leave-to {
  opacity: 0;
}

.feedback-entry img {
    filter: var(--img-tint);
}


</style>
