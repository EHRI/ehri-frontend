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
  data: function () {
    return {
      loaded: false,
      loading: false,
      datasetInfo: [],
    }
  },
  methods: {
    loadDatasetInfo: async function () {
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
    goTo: function (id: string, ds?: string) {
      window.location = this.api.managerUrl(id, ds);
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
    <div id="dashboard-institution-list" class="dashboard-institution-list">
      <div v-for="info in datasetInfo" v-on:click="goTo(info.repoId)" class="dashboard-institution-item">
        <img v-bind:src="info.logoUrl" alt="info.name" class="item-icon"/>
        <h3 class="item-heading">{{ info.name }}</h3>
        <div class="item-meta">
        </div>
        <div class="badge badge-success item-badge">
          {{ info.sets.length + " Dataset" + (info.sets.length > 1 ? 's' : '') }}
        </div>
      </div>
    </div>
  </div>
</template>

