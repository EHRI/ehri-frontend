<script lang="ts">

import MixinUtil from './_mixin-util';
import {DatasetManagerApi} from "../api";

export default {
  components: {},
  mixins: [MixinUtil],
  props: {
    config: Object,
    api: DatasetManagerApi,
  },
  data: function() {
    return {
      loaded: false,
      datasets: [],
      stats: {},
    }
  },
  methods: {
  },
};
</script>

<template>
  <div id="dataset-manager-container" class="container">
    <div v-if="error" id="app-error-notice" class="alert alert-danger alert-dismissable">
      <span class="close" v-on:click="error = null">&times;</span>
      {{error}}
    </div>

  </div>
</template>

