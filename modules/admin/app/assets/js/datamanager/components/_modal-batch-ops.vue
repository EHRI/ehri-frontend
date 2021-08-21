<script lang="ts">

/**
 * The operations in this component are a bit dodgy since they
 * run recursive asynchronous functions which monitor websocket
 * output for progress and termination. Therefore, there are quite
 * a few ways things can go wrong and it should be used with caution.
 */

import ModalWindow from './_modal-window';
import FilePicker from './_file-picker';
import PanelLogWindow from './_panel-log-window';
import {DatasetManagerApi} from '../api';
import {ImportConfig, ImportDataset} from "../types";
import _startsWith from "lodash/startsWith";
import _last from "lodash/last";

export default {
  components: {FilePicker, ModalWindow, PanelLogWindow},
  props: {
    datasets: Array,
    api: DatasetManagerApi,
    config: Object,
  },
  data: function() {
    return {
      tab: 'copy',
      current: null,
      inProgress: false,
      copyFrom: null,
      copyType: 'convert',
      forceConvert: false,
      log: [],
      cancelled: false,
      cancelling: false,
      working: {},
    }
  },
  methods: {
    println: function(...msg: string[]) {
      console.debug(...msg);
      let line = msg.join(' ');
      // FIXME: hack copied from ingest manager
      let progPrefix = "Ingesting..."
      if (this.log && _startsWith(_last(this.log), progPrefix) && _startsWith(line, progPrefix)) {
        this.log.splice(this.log.length - 1, 1, line);
      } else {
        this.log.push(line);
      }

      // Ensure log never goes longer than 1000 lines...
      this.log.splice(0, this.log.length - 1000);
    },
    dispatchWorker: function(worker: Worker, monitorUrl: string) {
      worker.postMessage({
        type: 'websocket',
        url: monitorUrl,
        DONE: DatasetManagerApi.DONE_MSG,
        ERR: DatasetManagerApi.ERR_MSG
      });
    },
    cancelOperation: function() {
      this.println("Cancelling...");
      this.cancelled = true;
    },
    cleanup: function() {
      if (this.cancelled) {
        this.println("Cancelled");
      }
      this.inProgress = false;
      this.cancelled = false;
    },
    syncAllDatasets: function() {
      // This is a shortcut for downloading ResourceSync files
      // for all datasets... it is not really 'production ready'...
      let syncDataset = (sets: ImportDataset[]) => {
        if (!this.cancelled && sets.length > 0) {
          this.inProgress = true;
          let [set, ...rest]  = sets;

          this.api.getSyncConfig(set.id).then(config => {
            if (config) {
              this.println("Syncing", set.name);
              this.$set(this.working, set.id, true);
              this.$emit('processing', set.id);
              this.api.sync(set.id, config).then( ({url}) => {
                let worker = new Worker(this.config.previewLoader);
                worker.onmessage = msg => {
                  if (msg.data.msg) {
                    this.println(msg.data.msg);
                  }
                  if (msg.data.done || msg.data.error) {
                    worker.terminate();
                    this.$delete(this.working, set.id);
                    this.$emit('processing-done', set.id);
                    syncDataset(rest);
                  }
                };
                this.dispatchWorker(worker, url);
              });
            } else {
              this.println("No config found for", set.name);
              syncDataset(rest);
            }
          })
        } else {
          this.cleanup();
        }
      }

      this.api.listDatasets().then(syncDataset);
    },
    convertAllDatasets: function() {
      // This is a shortcut for running conversion on all datasets...
      let convertDataset = (sets: ImportDataset[]) => {
        if (!this.cancelled && sets.length > 0) {
          this.inProgress = true;
          let [set, ...rest]  = sets;

          this.println("Converting", set.name);
          this.api.getConvertConfig(set.id).then(config => {
            this.$set(this.working, set.id, true);
            this.$emit('processing', set.id);
            this.api.convert(set.id, null, {mappings: config, force: this.forceConvert}).then( ({url}) => {
              let worker = new Worker(this.config.previewLoader);
              worker.onmessage = msg => {
                if (msg.data.msg) {
                  this.println(msg.data.msg);
                }
                if (msg.data.done || msg.data.error) {
                  worker.terminate();
                  this.$delete(this.working, set.id);
                  this.$emit('processing-done', set.id);
                  convertDataset(rest);
                }
              };
              this.dispatchWorker(worker, url);
            });
          });
        } else {
          this.cleanup();
        }
      }

      this.api.listDatasets().then(convertDataset);
    },
    importAllDatasets: function() {
      // This is a shortcut for running conversion on all datasets...
      let commitCheck = prompt("Type 'yes' to commit:");
      let commit = commitCheck === null || commitCheck.toLowerCase() === "yes";
      let importDataset = (sets: ImportDataset[]) => {
        if (!this.cancelled && sets.length > 0) {
          this.inProgress = true;
          let [set, ...rest]  = sets;

          this.api.getImportConfig(set.id).then(config => {
            if (config) {
              this.println("Importing", set.name);
              this.$set(this.working, set.id, true);
              this.$emit('processing', set.id);
              this.api.ingestFiles(set.id, [], config, commit).then(({url}) => {
                let worker = new Worker(this.config.previewLoader);
                worker.onmessage = msg => {
                  if (msg.data.msg) {
                    this.println(msg.data.msg);
                  }
                  if (msg.data.done || msg.data.error) {
                    worker.terminate();
                    this.$delete(this.working, set.id);
                    this.$emit('processing-done', set.id);
                    importDataset(rest);
                  }
                };
                this.dispatchWorker(worker, url);
              });
            } else {
              this.println("No import config found for", set.name);
              importDataset(rest);
            }
          });
        } else {
          this.cleanup();
        }
      }

      this.api.listDatasets().then(importDataset);
    },
    copyConvertSettings: function() {
      // Copy convert settings from one dataset to all the others...
      let saveSettings = (sets: ImportDataset[], settings: [string, object][]) => {
        if (sets.length > 0) {
          this.inProgress = true;
          let [set, ...rest] = sets;
          this.api.saveConvertConfig(set.id, settings)
              .then(r => {
                this.println("Copied", set.name);
                saveSettings(rest, settings);
              });
        } else {
          this.cleanup();
        }
      }
      if (window.confirm(`Copy convert settings from ${this.copyFrom}?`)) {
        this.api.getConvertConfig(this.copyFrom).then(data => {
          this.api.listDatasets()
              .then(sets => saveSettings(sets.filter(s => s.id !== this.copyFrom), data));
        })
      }
    },
    copyImportSettings: function() {
      // Copy convert settings from one dataset to all the others...
      let saveSettings = (sets: ImportDataset[], settings: ImportConfig) => {
        if (sets.length > 0) {
          this.inProgress = true;
          let [set, ...rest] = sets;
          this.api.saveImportConfig(set.id, settings)
              .then(() => {
                this.println("Copied", set.name);
                saveSettings(rest, settings);
              });
        } else {
          this.inProgress = false;
        }
      }
      if (window.confirm(`Copy import settings from ${this.copyFrom}?`)) {
        this.api.getImportConfig(this.copyFrom).then(data => {
          this.api.listDatasets()
              .then(sets => saveSettings(sets.filter(s => s.id !== this.copyFrom), data));
        })
      }
    },
    copySettings: function() {
      if (this.copyType === 'import') {
        this.copyImportSettings();
      } else if (this.copyType === 'convert') {
        this.copyConvertSettings();
      }
    }
  },
}
</script>

<template>
  <modal-window v-on:close="$emit('close')">
    <template v-slot:title><i class="fa fa-warning"></i> Batch Operations</template>


    <ul class="nav nav-tabs">
      <li class="nav-item">
        <a v-bind:class="{active: tab === 'copy'}" v-on:click.prevent="tab = 'copy'" href="#tab-copy" class="nav-link">
          Copy Settings
        </a>
      </li>
      <li class="nav-item">
        <a v-bind:class="{active: tab === 'sync'}" v-on:click.prevent="tab = 'sync'" href="#tab-sync" class="nav-link">
          Synchronise All Datasets
        </a>
      </li>
      <li class="nav-item">
        <a v-bind:class="{active: tab === 'convert'}" v-on:click.prevent="tab = 'convert'" href="#tab-convert" class="nav-link">
          Convert All Datasets
        </a>
      </li>
      <li class="nav-item">
        <a v-bind:class="{active: tab === 'import'}" v-on:click.prevent="tab = 'import'" href="#tab-import" class="nav-link">
          Import All Datasets
        </a>
      </li>
    </ul>

    <fieldset v-if="tab === 'copy'" id="tab-copy" class="batch-op-tab options-form">
      <div class="form-group">
        <label class="form-label" for="from-dataset">
          Source Dataset
          <span class="required-input">*</span>
        </label>
        <select v-model="copyFrom" class="form-control" id="from-dataset">
          <option v-bind:value="null" disabled selected hidden>(required)</option>
          <option v-for="ds in datasets" v-bind:value="ds.id">{{ ds.name }}</option>
        </select>
      </div>

      <div class="form-group">
        <label class="form-label" for="copy-type">
          Copy Configuration
          <span class="required-input">*</span>
        </label>
        <select v-model="copyType" class="form-control" id="copy-type">
          <option v-bind:value="null" disabled>(required)</option>
          <option v-bind:value="'convert'">Convert Settings</option>
          <option v-bind:value="'import'">Import Settings</option>
        </select>
      </div>
    </fieldset>

    <fieldset v-else-if="tab === 'sync'" id="tab-sync" class="batch-op-tab options-form">
    </fieldset>

    <fieldset v-else-if="tab === 'convert'" id="tab-convert" class="batch-op-tab options-form">
      <div class="form-group form-check">
        <input class="form-check-input" id="opt-force-check" type="checkbox" v-model="forceConvert"/>
        <label class="form-check-label" for="opt-force-check">
          Rerun existing conversions
        </label>
      </div>
    </fieldset>

    <fieldset v-else-if="tab === 'import'" id="tab-import" class="batch-op-tab options-form">
    </fieldset>
    <div class="log-container" id="batch-ops-log">
      <panel-log-window v-bind:log="log" v-if="log.length > 0"/>
      <div class="panel-placeholder" v-else>

      </div>
    </div>
    <template v-slot:footer>
      <button v-bind:disabled="!inProgress" v-on:click="cancelOperation" v-bind:class="{'btn-default': !inProgress, 'btn-danger': inProgress}"
              type="button" class="btn">
        <i v-if="cancelled" class="fa fa-fw fa-circle-o-notch" v-bind:class="{'fa-spin': inProgress}"></i>
        <i v-else class="fa fa-fw fa-stop-circle"></i>
        Cancel Operation
      </button>
      <button v-if="tab === 'copy'" v-bind:disabled="inProgress || !(copyFrom && copyType)" v-on:click="copySettings" type="button" class="btn btn-secondary">
        Copy Settings
      </button>
      <button v-if="tab === 'sync'" v-bind:disabled="inProgress || datasets.find(d => d.src = 'rs') === undefined" v-on:click="syncAllDatasets" type="button" class="btn btn-secondary">
        Synchronise ResourceSync Datasets
      </button>
      <button v-if="tab === 'convert'" v-bind:disabled="inProgress" v-on:click="convertAllDatasets" type="button" class="btn btn-secondary">
        Run Convert
      </button>
      <button v-if="tab === 'import'" v-bind:disabled="inProgress" v-on:click="importAllDatasets" type="button" class="btn btn-secondary">
        Run Import
      </button>
    </template>
  </modal-window>
</template>
