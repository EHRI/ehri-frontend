<script lang="ts">

import MixinUtil from './components/_mixin-util';
import {DatasetManagerApi} from "./api";
import DatasetList from "./components/_dataset-list.vue";

export default {
  components: {DatasetList},
  mixins: [MixinUtil],
  data: function() {
    return {
      config: CONFIG,
      api: new DatasetManagerApi(SERVICE, CONFIG.repoId),
      tab: 'input',
    }
  },
}
</script>

<template>
  <div id="app-container">
    <dataset-list
        v-bind:config="config"
        v-bind:api="api" />
  </div>
</template>
