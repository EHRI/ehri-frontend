<script lang="ts">

import ModalDatasetConfig from './_modal-dataset-config';
import ModalDatasetIo from './_modal-dataset-io'
import ModalBatchOps from './_modal-batch-ops';
import ManagerSnapshots from "./_manager-snapshots";
import ManagerCoreference from "./_manager-coreference";
import ManagerDataset from "./_manager-dataset";

import MixinUtil from './_mixin-util';
import MixinError from './_mixin-error';
import {ImportDataset, ImportDatasetSrc} from '../types';
import {DatasetManagerApi} from "../api";

import _find from 'lodash/find';
import _merge from 'lodash/merge';
import _omit from 'lodash/omit';

export default {
  components: {ManagerCoreference, ManagerDataset, ManagerSnapshots, ModalDatasetConfig, ModalDatasetIo, ModalBatchOps},
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
    refreshStats: function() {
      this.api.datasetStats().then(stats => this.stats = stats);
    },
    loadDatasets: function() {
      this.refreshStats();
      return this.api.listDatasets()
          .then(dsl => this.datasets = dsl)
          .catch(e => this.showError("Error loading datasets", e))
          .finally(() => this.loaded = true);
    },
    reloadDatasets: function(ds?: ImportDataset) {
      this.loadDatasets().then(() => {
        if(ds) {
          this.selectDataset(ds);
        }
      });
    },
    stageName: function(code: ImportDatasetSrc): string {
      switch (code) {
        case "oaipmh": return "Harvesting";
        case "upload": return "Uploads";
        case "rs": return "ResourceSync";
        default: return code;
      }
    },
    openSnapshots: function() {
      this.snapshots = true;
      history.pushState(
          _merge(this.queryParams(window.location.search), {'snapshots': true}),
          document.title,
          this.setQueryParam(window.location.search, 'snapshots', true));
    },
    closeSnapshots: function() {
      this.snapshots = false;
      history.pushState(
          _omit(this.queryParams(window.location.search), 'snapshots'),
          document.title,
          window.location.pathname
          + this.removeQueryParam(window.location.search, ['snapshots']));
    },
    openCoreference: function() {
      this.coreference = true;
      history.pushState(
          _merge(this.queryParams(window.location.search), {'refs': true}),
          document.title,
          this.setQueryParam(window.location.search, 'refs', true));
    },
    closeCoreference: function() {
      this.coreference = false;
      history.pushState(
          _omit(this.queryParams(window.location.search), 'refs'),
          document.title,
          window.location.pathname
          + this.removeQueryParam(window.location.search, ['refs']));
    }
  },
  created() {
    if (this.getQueryParam(window.location.search, "snapshots") === "true") {
      this.snapshots = true;
    } else if (this.getQueryParam(window.location.search, "refs") === "true") {
      this.coreference = true;
    }
    window.onpopstate = event => {
      if (event.state && event.state.snapshots) {
        this.snapshots = true;
      } else if (event.state && event.state.refs) {
        this.coreference = true;
      }
      if (event.state && event.state.ds) {
        this.dataset = _find(this.datasets, d => d.id === event.state.ds);
      } else {
        this.dataset = null;
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
  <div id="dataset-manager-container">
    <modal-dataset-config v-if="showDatasetForm"
                          v-bind:info="dataset"
                          v-bind:config="config"
                          v-bind:api="api"
                          v-on:close="showDatasetForm = false"
                          v-on:saved-dataset="reloadDatasets"
                          v-on:deleted-dataset="dataset = null; reloadDatasets()" />

    <modal-dataset-io v-if="showImportForm"
                  v-bind:datasets="datasets"
                  v-bind:api="api"
                  v-bind:config="config"
                  v-on:close="showImportForm = false"
                  v-on:saved="reloadDatasets(); showImportForm = false" />

    <keep-alive>
      <modal-batch-ops
          v-if="showBatchForm"
          v-bind:datasets="datasets"
          v-bind:api="api"
          v-bind:config="config"
          v-on:close="showBatchForm = false"
          v-on:processing="id => $set(working, id, true)"
          v-on:processing-done="id => {$delete(working, id); refreshStats();}"
      />
    </keep-alive>

    <manager-snapshots
        v-if="snapshots"
        v-bind:config="config"
        v-bind:api="api"
        v-on:close="closeSnapshots"
    />

    <manager-coreference
      v-else-if="coreference"
      v-bind:config="config"
      v-bind:api="api"
      v-on:close="closeCoreference"
        />

    <div v-else-if="!loaded && dataset === null" class="dataset-loading-indicator">
      <h2>
        <i class="fa fa-lg fa-spin fa-spinner"></i>
        Loading datasets...
      </h2>
    </div>
    <div v-else-if="dataset === null" id="dataset-manager">
      <div id="dataset-manager-heading">
        <h2 v-if="loaded && datasets.length === 0">Create a new dataset...</h2>
        <h2 v-else>Datasets</h2>
        <div class="dropdown">
          <button class="btn" v-on:click="showOptions = !showOptions">
            <i class="fa fa-fw fa-ellipsis-v"></i>
          </button>
          <div v-if="showOptions" class="dropdown-backdrop" v-on:click="showOptions = false">
          </div>
          <div v-if="showOptions" class="dropdown-menu dropdown-menu-right show">
            <button v-on:click.prevent="showOptions = false; openSnapshots()" class="dropdown-item">
              <i class="fa fa-list-alt"></i>
              Manage Snapshots
            </button>
            <button v-on:click.prevent="showOptions = false; openCoreference()" class="dropdown-item">
              <i class="fa fa-link"></i>
              Manage Coreference Table
            </button>
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
      <p v-if="loaded && datasets.length === 0" class="info-message">
        To manage institution data you must create at least one dataset. A dataset is
        a set of files that typically come from the same source and are processed in
        the same way.
      </p>
      <template v-else>
        <div class="dataset-manager-list">
          <div v-for="ds in datasets" v-on:click.prevent="selectDataset(ds)" class="dataset-manager-item">
            <div class="badge badge-primary" v-bind:class="'badge-' + ds.src">
              {{stageName(ds.src)}}
              <span v-if="ds.id in stats">({{stats[ds.id]}})</span>
            </div>
            <h3>
              {{ds.name}}
              <i v-if="ds.id in working" class="fa fa-gear fa-spin fa-fw"></i>
            </h3>
            <p v-if="ds.notes">{{ds.notes}}</p>
          </div>
        </div>
      </template>
      <div class="dataset-actions">
        <button v-on:click.prevent="showDatasetForm = true" class="btn btn-success">
          <i class="fa fa-plus-circle"></i>
          Create a new dataset...
        </button>
      </div>
    </div>
    <manager-dataset v-else
       v-bind:config="config"
       v-bind:api="api"
       v-bind:init-tab="initTab"
       v-bind:dataset="dataset"
       v-bind:datasets="datasets"
       v-bind:stats="stats"
       v-on:error="showError"
       v-on:edit-dataset="showDatasetForm = true"
       v-on:close-dataset="closeDataset"
       v-on:select-dataset="selectDataset"
       v-on:refresh-stats="refreshStats"
        />
  </div>
</template>

