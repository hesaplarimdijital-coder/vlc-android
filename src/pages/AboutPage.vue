<template>
    <div class="container">
        <h5 class="media-content text-primary" v-t="'ABOUT'"></h5>
        <div class="modal-body mt-4">

            <div class="table-responsive card">
                <table class="table table-hover table-no-margin bottomless-table">
                    <thead>
                        <tr class="table-striped-columns">
                            <th colspan="2" role="columnheader" scope="col" v-t="'REMOTE_ACCESS_TITLE'">
                            </th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr>
                            <td scope="col" class="col-1">
                                <p v-t="'REMOTE_ACCESS_VERSION_TITLE'"></p>
                            </td>
                            <td scope="col" class="col-4">
                                <p v-t="'REMOTE_ACCESS_VERSION'"></p>
                            </td>
                        </tr>
                        <tr>
                            <td scope="col" class="col-1">
                                <p v-t="'REMOTE_ACCESS_HASH_TITLE'"></p>
                            </td>
                            <td scope="col" class="col-4">
                                <p v-t="'REMOTE_ACCESS_HASH'"></p>
                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>

            <div class="table-responsive card mt-4">
                <table class="table table-hover table-no-margin bottomless-table">
                    <thead>
                        <tr class="table-striped-columns">
                            <th colspan="2" role="columnheader" scope="col" v-t="'CONNECTED_DEVICE'">
                            </th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr>
                            <td scope="col" class="col-1">
                                <p v-t="'DEVICE_MODEL'"></p>
                            </td>
                            <td scope="col" class="col-4">
                                <p v-t="'DEVICE_NAME'"></p>
                            </td>
                        </tr>
                        <tr>
                            <td scope="col" class="col-1">
                                <p v-t="'VLC_VERSION'"></p>
                            </td>
                            <td scope="col" class="col-4">
                                <p v-t="'APP_VERSION'"></p>
                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>

            <div class="table-responsive card mt-4">
                <table class="table table-hover table-no-margin align-middle bottomless-table">
                    <thead>
                        <tr class="table-striped-columns">
                            <th colspan="3" role="columnheader" scope="col" v-t="'LIBRARIES'">
                            </th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr v-for="item in this.librairies" :key="item.name">
                            <td scope="col" class="col-1">
                                <p><a :href="item.link" target="_blank">{{item.name}}</a>
                                </p>
                            </td>
                            <td scope="col" class="col-3">
                                <p>{{ item.copyright}}</p>
                            </td>
                            <td scope="col" class="col-1">
                                <p>{{ item.license }}</p>
                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>

        </div>
    </div>
</template>

<script>


export default {
    data() {
        return {
            librairies: [
                {
                    name: "floating-ui/vue",
                    link: "https://github.com/floating-ui/floating-ui",
                    copyright: "Copyright (c) 2021 Floating UI contributors",
                    license: "MIT"
                },
                {
                    name: "vue",
                    link: "https://github.com/vuejs/core",
                    copyright: "Copyright (c) 2018-present, Yuxi (Evan) You and Vue contributors",
                    license: "MIT"
                },
                {
                    name: "axios",
                    link: "https://github.com/axios/axios",
                    copyright: "Copyright (c) 2014-present Matt Zabriskie & Collaborators",
                    license: "MIT"
                },
                {
                    name: "bootstrap",
                    link: "https://github.com/twbs/bootstrap",
                    copyright: "Copyright (c) 2011-2024 The Bootstrap Authors",
                    license: "MIT"
                }, 
                {
                    name: "js-sha256",
                    link: "https://github.com/emn178/js-sha256",
                    copyright: "Copyright (c) 2014-2024 Chen, Yi-Cyuan",
                    license: "MIT"
                }, 
                {
                    name: "pinia",
                    link: "https://github.com/vuejs/pinia",
                    copyright: "Copyright (c) 2019-present Eduardo San Martin Morote",
                    license: "MIT"
                }, 
                {
                    name: "vue-i18n",
                    link: "https://github.com/intlify/vue-i18n",
                    copyright: "Copyright (c) 2016 kazuya kawaguchi",
                    license: "MIT"
                }, 
                {
                    name: "vue-lazyload",
                    link: "https://github.com/hilongjw/vue-lazyload",
                    copyright: "Copyright (c) 2016 Awe",
                    license: "MIT"
                },
                {
                    name: "vue-logger-plugin",
                    link: "https://github.com/dev-tavern/vue-logger-plugin",
                    copyright: "Copyright (c) 2020 devtavern",
                    license: "MIT"
                },
            ]
        }
    },
}
</script>

<style lang='scss'>
@import '../scss/colors.scss';

.bottomless-table tbody > tr:last-child > td {
  border-bottom: 0;
}
</style>
