<script lang="ts">

import Dashboard from './components/_dashboard';
import MixinUtil from './components/_mixin-util';

import {ConfigType} from "./types";
import {DashboardApi} from "./dashboard-api";

export default {
  components: {Dashboard},
  mixins: [MixinUtil],
  props: {
    config: Object,
    service: Object,
  },
  data: function() {
    return {
      api: new DashboardApi(this.service),
      error: null,
    }
  },
  methods: {
    setError: function(err: string, exc?: Error) {
      this.error = err + (exc ? (": " + exc.message) : "");
    },
  },
}
</script>

<template>
  <div id="dashboard-container" class="app-content-inner">
    <div v-if="error" id="app-error-notice" class="alert alert-danger alert-dismissable">
      <span class="close" v-on:click="error = null">&times;</span>
      {{error}}
    </div>
    <dashboard
        v-bind:config="config"
        v-bind:api="api"
        v-on:error="setError"
        />
  </div>
</template>
