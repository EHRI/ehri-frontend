<script lang="ts">

import MixinUtil from './_mixin-util';
import MixinError from './_mixin-error';
import {DashboardApi} from "../dashboard-api";

export default {
  components: {},
  mixins: [MixinUtil, MixinError],
  props: {
    config: Object,
    api: DashboardApi,
  },
  data: function() {
    return {
      loaded: false,
      loading: false,
      datasetInfo: [],
    }
  },
  methods: {
    loadDatasetInfo: async function() {
      this.loading = true;
      try {
        this.datasetInfo = await this.api.listAllDatasets()
        this.loaded = true;
      } catch (e) {
        this.showError("Error loading dataset info", e);
      } finally {
        this.loading = true;
      }
    },
    managerUrl: function(id: string, ds?: string) {
      return this.api.managerUrl(id, ds);
    }
  },
  created() {
    this.loadDatasetInfo();
  },
};
</script>

<template>
  <div id="dashboard-container">
    <h1>{{ config.title }}</h1>
    <div id="dashboard-institution-list" class="list-group">
      <div v-for="info in datasetInfo" class="list-group-item">
        <a v-bind:href="managerUrl(info.repoId)" class="list-group-item-action">
          <h4>{{ info.name }}</h4>
          <div class="badge badge-success">
            {{ info.sets.length + " Dataset" + (info.sets.length > 1 ? 's' : '')}}
          </div>
        </a>
      </div>
    </div>
  </div>
</template>

