<script lang="ts">

import ModalDatasetConfig from './_modal-dataset-config';
import ModalDatasetIo from './_modal-dataset-io'
import ModalBatchOps from './_modal-batch-ops';
import ManagerSnapshots from "./_manager-snapshots";
import ManagerCoreference from "./_manager-coreference";
import ManagerTimeline from "./_manager-timeline";
import ManagerDataset from "./_manager-dataset";

import MixinUtil from './_mixin-util';
import MixinError from './_mixin-error';
import {ImportDataset, ImportDatasetSrc, ImportDatasetStatus} from '../types';
import {DatasetManagerApi} from "../api";

import _find from 'lodash/find';
import _merge from 'lodash/merge';
import _omit from 'lodash/omit';
import _includes from 'lodash/includes';

export default {
  components: {ManagerCoreference, ManagerDataset, ManagerSnapshots, ManagerTimeline, ModalDatasetConfig, ModalDatasetIo, ModalBatchOps},
  mixins: [MixinUtil, MixinError],
  props: {
    config: Object,
    api: DatasetManagerApi,
    initTab: String,
  },
  data: function() {
    return {
      loaded: false,
      datasets: [],
      dataset: null,
      editing: null,
      tab: this.initTab,
      showOptions: false,
      showDatasetForm: false,
      showImportForm: false,
      showBatchForm: false,
      showSelector: false,
      stats: {},
      working: {},
      snapshots: false,
      coreference: false,
      tab2: 'datasets',
    }
  },
  methods: {
    selectDataset: function(ds?: ImportDataset) {
      if (!ds) {
        this.dataset = null;
        return;
      }
      this.dataset = ds;
      history.pushState(
          _merge(this.queryParams(window.location.search), {'ds': ds.id}),
          document.title,
          this.setQueryParam(window.location.search, 'ds', ds.id));
    },
    closeDataset: function() {
      this.dataset = null;
      history.pushState(
          _omit(this.queryParams(window.location.search), 'ds', 'tab'),
          document.title,
          window.location.pathname
          + this.removeQueryParam(window.location.search, ['ds', 'tab']));
    },
    loadStats: function() {
      // NB: this function is not async because we want it to run in parallel
      for (let ds of this.datasets) {
        this.api.fileCount(ds.id).then(count => this.stats[ds.id] = count);;
      }
    },
    loadDatasets: async function() {
      try {
        this.datasets = await this.api.listDatasets();
        this.loadStats();
      } catch (e: Error) {
        this.showError("Error loading datasets", e);
      } finally {
        this.loaded = true;
      }
    },
    reloadDatasets: async function(ds?: ImportDataset) {
      await this.loadDatasets();
      if (ds && this.editing && this.editing.id === ds.id) {
        this.editing = ds;
      }
    },
    stageName: function(code: ImportDatasetSrc): string {
      switch (code) {
        case "oaipmh": return "Harvesting";
        case "upload": return "Uploads";
        case "rs": return "ResourceSync";
        default: return code;
      }
    },
    statusName: function(code: ImportDatasetStatus): string {
      switch (code) {
        case "active": return "Active";
        case "onhold": return "On Hold";
        case "inactive": return "Inactive";
        default: return code;
      }
    },
    switchTab: function(tab: string) {
      this.tab2 = tab;
      if (tab !== 'datasets') {
        history.pushState(
            _merge(this.queryParams(window.location.search), { tab: tab}),
            document.title,
            this.setQueryParam(window.location.search, "tab", tab));
      } else {
        history.pushState(
            _omit(this.queryParams(window.location.search), "tab"),
            document.title,
            window.location.pathname
            + this.removeQueryParam(window.location.search, ["tab"]));
      }
    },
  },
  created() {
    let tab = this.getQueryParam(window.location.search, "tab", "datasets");
    if (_includes(["snapshots", "refs", "logs", "datasets"], tab)) {
      this.tab2 = tab;
    }
    window.onpopstate = event => {
      if (event.state && event.state.tab) {
        this.tab2 = event.state.tab;
      } else {
        this.tab2 = 'datasets';
        if (event.state && event.state.ds) {
          this.dataset = _find(this.datasets, d => d.id === event.state.ds);
        } else {
          this.dataset = null;
        }
      }
    };

    this.loadDatasets().then(() => {
      let qsDs = this.getQueryParam(window.location.search, "ds");
      if (qsDs) {
        this.selectDataset(_find(this.datasets, d => d.id === qsDs));
      }
    });
  },
};
</script>

<template>
  <div id="dataset-list-container">

    <modal-dataset-config v-if="showDatasetForm"
                          v-bind:info="editing"
                          v-bind:config="config"
                          v-bind:api="api"
                          v-on:close="editing = null; showDatasetForm = false"
                          v-on:saved-dataset="reloadDatasets"
                          v-on:deleted-dataset="dataset = null; editing = null; reloadDatasets()" />

    <modal-dataset-io v-if="showImportForm"
                      v-bind:datasets="datasets"
                      v-bind:api="api"
                      v-bind:config="config"
                      v-on:close="showImportForm = false"
                      v-on:saved="reloadDatasets(); showImportForm = false" />

    <manager-dataset v-if="dataset"
                     v-bind:config="config"
                     v-bind:api="api"
                     v-bind:init-tab="initTab"
                     v-bind:dataset="dataset"
                     v-bind:datasets="datasets"
                     v-bind:stats="stats"
                     v-on:error="showError"
                     v-on:edit-dataset="editing = dataset; showDatasetForm = true"
                     v-on:close-dataset="closeDataset"
                     v-on:select-dataset="selectDataset"
                     v-on:refresh-stats="loadStats"
    />


    <template v-else-if="loaded">
      <ul id="dataset-manager-tabs" class="nav nav-tabs">
        <li class="nav-item">
          <a v-bind:class="{active: tab2 === 'datasets'}" v-on:click.prevent="switchTab('datasets')" href="#" class="nav-link">
            Datasets
          </a>
        </li>
        <li class="nav-item">
          <a v-bind:class="{active: tab2 === 'snapshots'}" v-on:click.prevent="switchTab('snapshots')" href="#" class="nav-link">
            Content Snapshots
          </a>
        </li>
        <li class="nav-item">
          <a v-bind:class="{active: tab2 === 'refs'}" v-on:click.prevent="switchTab('refs')" href="#" class="nav-link">
            Coreference Table
          </a>
        </li>
        <li class="nav-item">
          <a v-bind:class="{active: tab2 === 'logs'}" v-on:click.prevent="switchTab('logs')" href="#" class="nav-link">
            Import Logs
          </a>
        </li>

        <li class="dataset-manager-menu">
          <div class="buttons">
            <button v-on:click.prevent="showDatasetForm = true" class="btn btn-sm btn-success">
              <i class="fa fa-plus-circle"></i>
              Create a new dataset...
            </button>
            <div class="dropdown">
              <button class="btn btn-sm btn-default" v-on:click="showOptions = !showOptions">
                <i class="fa fa-fw fa-ellipsis-v"></i>
              </button>
              <div v-if="showOptions" class="dropdown-backdrop" v-on:click="showOptions = false">
              </div>
              <div v-if="showOptions" class="dropdown-menu dropdown-menu-right show">
                <button v-on:click.prevent="showImportForm = true; showOptions = false" class="dropdown-item">
                  <i class="fa fa-file-code-o"></i>
                  Import/export Datasets
                </button>
                <button v-on:click.prevent="showOptions = false; showBatchForm = true" class="dropdown-item" v-bind:disabled="datasets.length <= 1">
                  <i class="fa fa-warning"></i>
                  Batch Operations
                </button>
              </div>
            </div>
          </div>
        </li>
      </ul>

      <keep-alive>
        <modal-batch-ops
            v-if="showBatchForm"
            v-bind:datasets="datasets"
            v-bind:api="api"
            v-bind:config="config"
            v-on:close="showBatchForm = false"
            v-on:processing="id => $set(working, id, true)"
            v-on:processing-done="id => {$delete(working, id); loadStats();}"
        />
      </keep-alive>

      <manager-snapshots
          v-if="tab2 === 'snapshots'"
          v-bind:config="config"
          v-bind:api="api"
          v-on:error="showError"
      />

      <manager-coreference
          v-else-if="tab2 === 'refs'"
          v-bind:config="config"
          v-bind:api="api"
          v-on:error="showError"
      />

      <manager-timeline
          v-else-if="tab2 === 'logs'"
          v-bind:datasets="datasets"
          v-bind:config="config"
          v-bind:api="api"
          v-on:error="showError"
      />

      <template v-if="tab2 === 'datasets'">
        <div id="dataset-manager">
          <template v-if="loaded && datasets.length === 0">
            <div id="dataset-manager-heading">
              <h2>There are no datasets yet...</h2>
            </div>
            <p class="info-message">
              To manage institution data you must create at least one dataset. A dataset is
              a set of files that typically come from the same source and are processed in
              the same way.
            </p>
          </template>
          <template v-else>
            <div class="dataset-manager-list">
              <div v-for="ds in datasets" v-on:click.prevent="selectDataset(ds)" v-bind:class="ds.status" class="dataset-manager-item">
                <div class="item-meta">
                  <p v-if="ds.notes">{{ds.notes}}</p>
                </div>
                <h3 class="item-heading">
                  {{ds.name}}
                  <i v-if="ds.id in working" class="fa fa-gear fa-spin fa-fw"></i>
                </h3>
                <div class="item-badge">
                  <div class="badge badge-light">
                    <template v-if="ds.id in stats">{{ stats[ds.id] }} File{{ stats[ds.id] === 1 ? '' : 's'}}</template>
                    <i v-else class="fa fa-fw fa-spinner fa-spin"></i>
                  </div>
                  <div class="badge badge-primary" v-bind:class="'badge-' + ds.status">
                    {{ statusName(ds.status) }}
                  </div>
                  <div class="badge badge-primary" v-bind:class="'badge-' + ds.src">
                    {{stageName(ds.src)}}
                  </div>
                </div>
                <div class="item-controls">
                  <button v-on:click.prevent.stop="editing = ds; showDatasetForm = true" class="btn btn-sm btn-default">
                    <i class="fa fa-edit"></i>
                  </button>
                </div>
              </div>
            </div>
          </template>
        </div>
      </template>
    </template>
    <div v-if="!loaded" class="dataset-loading-indicator">
      <h2>
        <i class="fa fa-lg fa-spin fa-spinner"></i>
        Loading datasets...
      </h2>
    </div>
  </div>
</template>

