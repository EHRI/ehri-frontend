<script lang="ts">

import ManagerDatasetList from './components/_manager-dataset-list';
import ManagerDataset from './components/_manager-dataset';
import ManagerSnapshots from './components/_manager-snapshots';
import MixinUtil from './components/_mixin-util';
import {DatasetManagerApi} from "./api";

import {ConfigType} from "./types";

export default {
  components: {ManagerDatasetList},
  mixins: [MixinUtil],
  props: {
    service: Object,
    config: Object,
  },
  data: function() {
    return {
      api: new DatasetManagerApi(this.service, this.config.repoId),
      tab: 'input',
      dataset: null,
      snapshots: false,
      error: null,
    }
  },
  methods: {
    setError: function(err: string, exc?: Error) {
      this.error = err + (exc ? (": " + exc.message) : "");
    },
  },
  created() {
    if (!this.config.versioned) {
      this.setError("Note: file storage does not have versioning enabled.");
    }
  },
  mounted() {
    // Prevent default drag/drop action...
    if (typeof window !== 'undefined') {
      window.addEventListener("dragover", e => e.preventDefault(), false);
      window.addEventListener("drop", e => e.preventDefault(), false);
    }
  }
}
</script>

<template>
  <div class="app-content-inner">
    <div v-if="error" id="app-error-notice" class="alert alert-danger alert-dismissable">
      <span class="close" v-on:click="error = null">&times;</span>
      {{error}}
    </div>
    <manager-dataset-list
        v-bind:config="config"
        v-bind:init-tab="tab"
        v-bind:api="api"
        v-on:error="setError"
        />
  </div>
</template>
