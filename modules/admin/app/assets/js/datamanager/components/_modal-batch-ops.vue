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
import {ImportConfig, ImportDataset, ResourceSyncConfig} from "../types";
import _startsWith from "lodash/startsWith";
import _last from "lodash/last";
import convert from "lodash/fp/convert";

class CancelledTask extends Error {
  constructor() {
    super("Task cancelled");
    this.name = "TaskCancelled";
  }

}

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
      cleanupOrphans: false,
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
    monitor: async function(wsUrl: string) {
      return await new Promise(((resolve) => {
        let worker = new Worker(this.config.previewLoader);
        worker.onmessage = msg => {
          if (msg.data.msg) {
            this.println(msg.data.msg);
          }
          if (msg.data.done || msg.data.error) {
            worker.terminate();
            resolve();
          }
        };
        this.dispatchWorker(worker, wsUrl);
      })).catch(e => {throw e});
    },

    println: function(...msg: string[]) {
      //this.log = [msg.join(' ')];
      console.debug(...msg);
      let line = msg.join(' ');
      // FIXME: hack copied from ingest manager
      let progPrefix = "Ingesting..."
      if (this.log && _startsWith(_last(this.log), progPrefix) && _startsWith(line, progPrefix)) {
        this.log.splice(this.log.length - 1, 1, line);
      } else {
        this.log.push(line);
      }

      // Cull the list back to 1000 items every
      // time we exceed a threshold
      // FIXME: this is crap
      if (this.log.length >= 2000) {
        // this.log.shift();
        this.log.splice(0, 1000);
      }
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
    setRunning: function() {
      this.inProgress = true;
    },
    checkForOrphans: async function() {
      let datasets: ImportDataset[] = this.datasets;
      this.setRunning();

      for (let set of datasets) {
        if (this.cancelled) {
          this.cleanup();
          throw new CancelledTask();
        }
          let config = await this.api.getSyncConfig(set.id);
          if (config) {
            this.println("Checking", set.name + "...");
            this.$set(this.working, set.id, true);
            this.$emit('processing', set.id);
            try {
              let files = await this.api.cleanSyncConfig(set.id, config);
              if (files.length === 0) {
                this.println("... no orphans found");
              } else {
                this.println("..." + files.length, "orphaned file(s) found");
                files.forEach(f => this.println(" -", f));
              }
            } catch (e) {
              this.println(e.message);
            } finally {
              this.$delete(this.working, set.id);
              this.$emit('processing-done', set.id);
            }
          } else {
            this.println("No config found for", set.name);
          }
      }
    },
    syncAllDatasets: async function() {
      // This is a shortcut for downloading ResourceSync files
      // for all datasets... it is not really 'production ready'...
      let cleanupFiles = async(set: ImportDataset, config: ResourceSyncConfig) => {
        if (this.cleanupOrphans) {
          this.println("Checking for deleted files...")
          let orphans = this.api.cleanSyncConfig(set.id, config)
            if (orphans.length === 0) {
              this.println("...no deleted files found.")
            } else {
              this.println("Cleaning up orphans for dataset", set.name, ":");
              orphans.forEach(path => this.println(" x ", path));
              await this.api.deleteFiles(set.id, this.config.input, orphans);
              await this.api.deleteFiles(set.id, this.config.output, orphans);
            }
        }
      };

      let datasets: ImportDataset[] = this.datasets;
      this.setRunning();
      for (let set of datasets) {
        if (this.cancelled) {
          this.cleanup();
          throw new CancelledTask();
        }
        let config: ResourceSyncConfig|null = await this.api.getSyncConfig(set.id);
        if (config) {
          this.println("Syncing", set.name);
          this.$set(this.working, set.id, true);
          this.$emit('processing', set.id);
          try {
            let {url} = await this.api.sync(set.id, config);
            await this.monitor(url);
            await cleanupFiles(set, config);
          } catch (e) {
            this.println(e.message);
          } finally {
            this.$delete(this.working, set.id);
            this.$emit('processing-done', set.id);
          }
        } else {
          this.println("No config found for", set.name);
        }
      }
    },
    convertAllDatasets: async function() {
      let datasets: ImportDataset[] = this.datasets;
      this.setRunning();

      for (let set of datasets) {
        if (this.cancelled) {
          this.cleanup();
          throw new CancelledTask();
        }
        let config = await this.api.getConvertConfig(set.id);
        this.$set(this.working, set.id, true);
        this.$emit('processing', set.id);

        try {
          let {url} = await this.api.convert(set.id, null, {mappings: config, force: this.forceConvert});
          await this.monitor(url);
        } catch (e) {
          this.println(e.message);
        } finally {
          this.$delete(this.working, set.id);
          this.$emit('processing-done', set.id);
        }
      }
    },
    importAllDatasets: async function() {
      // This is a shortcut for running conversion on all datasets...
      let commitCheck = prompt("Type 'yes' to commit:");
      let commit = commitCheck === null || commitCheck.toLowerCase() === "yes";

      let datasets: ImportDataset[] = this.datasets;
      this.setRunning();

      for (let set of datasets) {
        if (this.cancelled) {
          this.cleanup();
          throw new CancelledTask();
        }

        let config = await this.api.getImportConfig(set.id);
        if (config) {
          this.println("Importing", set.name);
          this.$set(this.working, set.id, true);
          this.$emit('processing', set.id);
          try {
            let {url} = await this.api.ingestFiles(set.id, [], config, commit);
            await this.monitor(url);
          } catch (e) {
            this.println(e.message);
          } finally {
            this.$delete(this.working, set.id);
            this.$emit('processing-done', set.id);
          }
        } else {
          this.println("No import config found for", set.name);
        }
      }

      return true;
    },
    runAll: async function() {
      try {
        await this.syncAllDatasets();
        await this.convertAllDatasets();
        await this.importAllDatasets();
      } catch (e) {
        if (!(e instanceof CancelledTask)) {
          throw e;
        }
      }
    },
    copyConvertSettings: async function() {
      // Copy convert settings from one dataset to all the others...
      if (window.confirm(`Copy convert settings from ${this.copyFrom}?`)) {
        let config = await this.api.getConvertConfig(this.copyFrom);
        let others: ImportDataset[] = this.datasets.filter(s => s.id !== this.copyFrom);
        this.inProgress = true;
        for (let set of others) {
          await this.api.saveConvertConfig(set.id, config)
          this.println("Copied", set.name);
        }
        this.cleanup();
      }
    },
    copyImportSettings: async function() {
      // Copy convert settings from one dataset to all the others...
      if (window.confirm(`Copy import settings from ${this.copyFrom}?`)) {
        let config: ImportConfig = await this.api.getImportConfig(this.copyFrom);
        let others: ImportDataset[] = this.datasets.filter(s => s.id !== this.copyFrom);
        this.inProgress = true;
        for (let set of others) {
          await this.api.saveImportConfig(set.id, config)
          this.println("Copied", set.name);
        }
        this.cleanup();
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
        <a v-bind:class="{active: tab === 'copy'}" v-on:click.prevent="tab = 'copy'" href="#" class="nav-link">
          Copy Settings
        </a>
      </li>
      <li class="nav-item">
        <a v-bind:class="{active: tab === 'sync'}" v-on:click.prevent="tab = 'sync'" href="#" class="nav-link">
          Synchronise
        </a>
      </li>
      <li class="nav-item">
        <a v-bind:class="{active: tab === 'convert'}" v-on:click.prevent="tab = 'convert'" href="#" class="nav-link">
          Convert
        </a>
      </li>
      <li class="nav-item">
        <a v-bind:class="{active: tab === 'import'}" v-on:click.prevent="tab = 'import'" href="#" class="nav-link">
          Import
        </a>
      </li>
      <li class="nav-item">
        <a v-bind:class="{active: tab === 'all'}" v-on:click.prevent="tab = 'all'" href="#" class="nav-link">
          All
        </a>
      </li>
    </ul>

    <fieldset v-bind:disabled="inProgress" class="options-form">
      <div v-if="tab === 'copy'" class="form-group">
        <label class="form-label" for="from-dataset">
          Source Dataset
          <span class="required-input">*</span>
        </label>
        <select v-model="copyFrom" class="form-control" id="from-dataset">
          <option v-bind:value="null" disabled selected hidden>(required)</option>
          <option v-for="ds in datasets" v-bind:value="ds.id">{{ ds.name }}</option>
        </select>
      </div>

      <div v-if="tab === 'copy'" class="form-group">
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

      <div v-if="tab === 'sync' || tab === 'all'" class="form-group form-check">
        <input class="form-check-input" id="opt-clean" type="checkbox" v-model="cleanupOrphans"/>
        <label class="form-check-label" for="opt-clean">
          Cleanup Orphaned Files
        </label>
      </div>

      <div v-if="tab === 'convert' || tab === 'all'" class="form-group form-check">
        <input class="form-check-input" id="opt-force-check" type="checkbox" v-model="forceConvert"/>
        <label class="form-check-label" for="opt-force-check">
          Rerun existing conversions
        </label>
      </div>
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
      <template v-if="tab === 'sync'">
        <button v-bind:disabled="inProgress || datasets.find(d => d.src = 'rs') === undefined" v-on:click="checkForOrphans" type="button" class="btn btn-default">
          <i class="fa fa-fw fa-trash-o"></i>
          Check for Orphaned Files
        </button>
        <button v-bind:disabled="inProgress || datasets.find(d => d.src = 'rs') === undefined" v-on:click="syncAllDatasets" type="button" class="btn btn-secondary">
          Synchronise ResourceSync Datasets
        </button>
      </template>
      <button v-if="tab === 'convert'" v-bind:disabled="inProgress" v-on:click="convertAllDatasets" type="button" class="btn btn-secondary">
        Run Convert
      </button>
      <button v-if="tab === 'import'" v-bind:disabled="inProgress" v-on:click="importAllDatasets" type="button" class="btn btn-secondary">
        Run Import
      </button>
      <button v-if="tab === 'all'" v-bind:disabled="inProgress" v-on:click="runAll" type="button" class="btn btn-secondary">
        Synchronise, Convert &amp; Import
      </button>
    </template>
  </modal-window>
</template>
