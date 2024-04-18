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
      filter: "",
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
  computed: {
    filteredInfo: function() {
      let f = this.filter.toLowerCase();
      function filter(info) {
        return !f || (info.name.toLowerCase().includes(f)
            || info.repoId.includes(f)
            || info.altNames.toLowerCase().includes(f));
      }
      return this.datasetInfo.filter(filter);
    }
  },
  created() {
    this.loadDatasetInfo();
  },
};
</script>

<template>
  <div id="dashboard-container">
      <header id="dashboard-institution-header">
          <h1>{{ config.title }}</h1>
          <div class="dashboard-institution-filter">
                  <label class="sr-only" for="opt-filter">Filter</label>
                   <input v-model="filter" id="opt-filter" class="filter-input form-control form-control-sm" placeholder="Filter..."/>
          </div>
      </header>
    <div id="dashboard-institution-list" class="dashboard-institution-list">
      <div class="panel-placeholder" v-if="loaded && filter && filteredInfo.length === 0">
          No institutions found containing with &quot;<code>{{ filter }}</code>&quot;...
      </div>
      <div v-else
           v-for="info in filteredInfo"
           v-bind:key="info.repoId"
           v-on:click="goTo(info.repoId)"
           class="dashboard-institution-item" v-bind:id="'repo-id-' + info.repoId">
        <img v-bind:src="info.logoUrl" v-bind:alt="info.name" class="item-icon"/>
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

