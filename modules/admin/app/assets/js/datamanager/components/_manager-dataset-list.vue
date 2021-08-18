<script lang="ts">

import ModalDatasetConfig from './_modal-dataset-config';
import ModalDatasetImport from './_modal-dataset-import'
import ManagerSnapshots from "./_manager-snapshots.vue";
import ManagerDataset from "./_manager-dataset";

import MixinUtil from './_mixin-util';
import MixinError from './_mixin-error';
import {ImportConfig, ImportDataset, ImportDatasetSrc} from '../types';
import {DatasetManagerApi} from "../api";

import _find from 'lodash/find';
import _merge from 'lodash/merge';
import _omit from 'lodash/omit';

export default {
  components: {ManagerDataset, ManagerSnapshots, ModalDatasetConfig, ModalDatasetImport},
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
      showSelector: false,
      stats: {},
      working: {},
      snapshots: false,
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
    syncAllDatasets: function() {
      // This is a shortcut for downloading ResourceSync files
      // for all datasets... it is not really 'production ready'...
      let syncDataset = (sets: ImportDataset[]) => {
        if (sets.length > 0) {
          let [set, ...rest]  = sets;

          console.debug("Syncing", set);
          this.api.getSyncConfig(set.id).then(config => {
            if (config) {
              this.$set(this.working, set.id, true);
              this.api.sync(set.id, config).then( ({url}) => {
                let worker = new Worker(this.config.previewLoader);
                worker.onmessage = msg => {
                  if (msg.data.msg) {
                    console.debug(msg.data.msg);
                  }
                  if (msg.data.done || msg.data.error) {
                    worker.terminate();
                    this.refreshStats();
                    this.$delete(this.working, set.id);
                    syncDataset(rest);
                  }
                };
                worker.postMessage({
                  type: 'websocket',
                  url: url,
                  DONE: DatasetManagerApi.DONE_MSG,
                  ERR: DatasetManagerApi.ERR_MSG
                });
              });
            } else {
              syncDataset(rest);
            }
          })
        }
      }

      this.api.listDatasets().then(syncDataset);
    },
    convertAllDatasets: function() {
      // This is a shortcut for running conversion on all datasets...
      let forceCheck = prompt("Type 'yes' to force conversions:");
      let force = forceCheck === null || forceCheck.toLowerCase() === "yes";
      let convertDataset = (sets: ImportDataset[]) => {
        if (sets.length > 0) {
          let [set, ...rest]  = sets;

          console.debug("Converting", set);
          this.api.getConvertConfig(set.id).then(config => {
            this.$set(this.working, set.id, true);
            this.api.convert(set.id, null, {mappings: config, force: force}).then( ({url}) => {
              let worker = new Worker(this.config.previewLoader);
              worker.onmessage = msg => {
                if (msg.data.msg) {
                  console.debug(msg.data.msg);
                }
                if (msg.data.done || msg.data.error) {
                  worker.terminate();
                  this.refreshStats();
                  this.$delete(this.working, set.id);
                  convertDataset(rest);
                }
              };
              worker.postMessage({
                type: 'websocket',
                url: url,
                DONE: DatasetManagerApi.DONE_MSG,
                ERR: DatasetManagerApi.ERR_MSG
              });
            });
          });
        }
      }

      this.api.listDatasets().then(convertDataset);
    },
    importAllDatasets: function() {
      // This is a shortcut for running conversion on all datasets...
      let commitCheck = prompt("Type 'yes' to commit:");
      let commit = commitCheck === null || commitCheck.toLowerCase() === "yes";
      let importDataset = (sets: ImportDataset[]) => {
        if (sets.length > 0) {
          let [set, ...rest]  = sets;

          console.debug("Importing", set);
          this.api.getImportConfig(set.id).then(config => {
            this.$set(this.working, set.id, true);
            this.api.ingestFiles(set.id, [], config, commit).then( ({url}) => {
              let worker = new Worker(this.config.previewLoader);
              worker.onmessage = msg => {
                if (msg.data.msg) {
                  console.debug(msg.data.msg);
                }
                if (msg.data.done || msg.data.error) {
                  worker.terminate();
                  this.refreshStats();
                  this.$delete(this.working, set.id);
                  importDataset(rest);
                }
              };
              worker.postMessage({
                type: 'websocket',
                url: url,
                DONE: DatasetManagerApi.DONE_MSG,
                ERR: DatasetManagerApi.ERR_MSG
              });
            });
          });
        }
      }

      this.api.listDatasets().then(importDataset);
    },
    copyConvertSettingsFrom: function() {
      // Copy convert settings from one dataset to all the others...
      let saveSettings = (sets: ImportDataset[], settings: [string, object][]) => {
        if (sets.length > 0) {
          let [set, ...rest] = sets;
          this.api.saveConvertConfig(set.id, settings)
              .then(r => {
                console.debug("Saved: ", set.id, r.ok);
                saveSettings(rest, settings);
              });
        }
      }
      let from = prompt('Pick source dataset:');
      if (from !== null) {
        this.api.getConvertConfig(from).then(data => {
          this.api.listDatasets()
              .then(sets => saveSettings(sets.filter(s => s.id !== from), data));
        })
      }
    },
    copyImportSettingsFrom: function() {
      // Copy convert settings from one dataset to all the others...
      let saveSettings = (sets: ImportDataset[], settings: ImportConfig) => {
        if (sets.length > 0) {
          let [set, ...rest] = sets;
          this.api.saveImportConfig(set.id, settings)
              .then(() => {
                console.debug("Saved: ", set.id);
                saveSettings(rest, settings);
              });
        }
      }
      let from = prompt('Pick source dataset:');
      if (from !== null) {
        this.api.getImportConfig(from).then(data => {
          this.api.listDatasets()
              .then(sets => saveSettings(sets.filter(s => s.id !== from), data));
        })
      }
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
    }
  },
  created() {
    this.snapshots = this.getQueryParam(window.location.search, "snapshots") === "true";
    window.onpopstate = event => {
      if (event.state && event.state.snapshots) {
        this.snapshots = true;
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

    <modal-dataset-import v-if="showImportForm"
                  v-bind:config="config"
                  v-bind:api="api"
                  v-on:close="showImportForm = false"
                  v-on:saved="reloadDatasets(); showImportForm = false" />

    <manager-snapshots
      v-if="snapshots"
      v-bind:config="config"
      v-bind:api="api"
      v-on:close="closeSnapshots"
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
            <button v-on:click.prevent="showImportForm = true; showOptions = false" class="btn dropdown-item">
              <i class="fa fa-file-code-o"></i>
              Import datasets from JSON
            </button>
            <button v-on:click.prevent="showOptions = false; syncAllDatasets()" class="btn btn-danger dropdown-item">
              <i class="fa fa-refresh"></i>
              Sync All Datasets
            </button>
            <button v-on:click.prevent="showOptions = false; copyConvertSettingsFrom()" class="btn btn-danger dropdown-item">
              <i class="fa fa-copy"></i>
              Sync Convert Settings
            </button>
            <button v-on:click.prevent="showOptions = false; convertAllDatasets()" class="btn btn-danger dropdown-item">
              <i class="fa fa-file-code-o"></i>
              Convert All Datasets
            </button>
            <button v-on:click.prevent="showOptions = false; copyImportSettingsFrom()" class="btn btn-danger dropdown-item">
              <i class="fa fa-copy"></i>
              Sync Import Settings
            </button>
            <button v-on:click.prevent="showOptions = false; importAllDatasets()" class="btn btn-danger dropdown-item">
              <i class="fa fa-database"></i>
              Import All Datasets
            </button>
            <button v-on:click.prevent="showOptions = false; openSnapshots()" class="btn btn-danger dropdown-item">
              <i class="fa fa-list-alt"></i>
              Manage snapshots
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

