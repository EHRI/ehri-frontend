<script lang="ts">

import MixinUtil from './_mixin-util';
import MixinError from './_mixin-error';
import {DashboardApi} from "../dashboard-api";
import {DashboardConfigType} from "../types";

export default {
  components: {},
  mixins: [MixinUtil, MixinError],
  props: {
    config: Object as DashboardConfigType,
    api: DashboardApi,
  },
  data: function () {
    return {
      loaded: false,
      filter: "",
      datasetInfo: [],
    }
  },
  methods: {
    loadDatasetInfo: async function () {
      try {
        this.datasetInfo = await this.api.listAllDatasets()
        this.loaded = true;
      } catch (e) {
        console.log(e);
        this.showError("Error loading dataset info", e);
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
            || info.altNames.toLowerCase().includes(f)
            || (info.countryName || "").toLowerCase().includes(f));
      }
      return this.datasetInfo.filter(filter);
    },
    // The filtered institutions grouped by country, in country name order,
    // with any institutions without a country last.
    groupedInfo: function() {
      let groups = new Map();
      for (let info of this.filteredInfo) {
        let key = info.countryCode || "";
        if (!groups.has(key)) {
          groups.set(key, {code: key, name: info.countryName || "Other", items: []});
        }
        groups.get(key).items.push(info);
      }
      return Array.from(groups.values()).sort((a, b) =>
          Number(a.code === "") - Number(b.code === "") || a.name.localeCompare(b.name));
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
      <div class="panel-placeholder" v-else-if="loaded && datasetInfo.length === 0">
          There are no institutions with datasets that you can manage.
      </div>
      <section v-else
               v-for="group in groupedInfo"
               v-bind:key="group.code"
               class="dashboard-country">
        <h2 class="dashboard-country-heading">
          {{ group.name }}
          <span class="badge badge-light">{{ group.items.length }}</span>
        </h2>
        <div v-for="info in group.items"
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
      </section>
    </div>
  </div>
</template>

