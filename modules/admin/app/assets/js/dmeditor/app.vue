<script lang="ts">
import {defineComponent} from 'vue';
import ListEt from "./components/_list-et.vue";
import EntityTypeMetadataApi from "./api";
import {VueI18n} from "vue-i18n";
import Vue from "vue";


export default defineComponent({
  components: {ListEt},
  props: {
    service: Object,
    config: Object,
  },
  data: function () {
    return {
      api: new EntityTypeMetadataApi(this.service, this.config),
      error: null,
    }
  },
  methods: {
    setError: function (err: string, exc?: Error) {
      this.error = err + (exc ? (": " + exc.message) : "");
    },
  },
});
</script>

<template>
    <div class="app-content-inner">
        <div v-if="error" id="app-error-notice" class="alert alert-danger alert-dismissable">
            <span class="close" v-on:click="error = null">&times;</span>
            {{ error }}
        </div>
        <list-et v-bind:api="api" v-on:error="setError"/>
    </div>
</template>
