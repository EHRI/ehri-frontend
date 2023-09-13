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
import MixinTasklog from './_mixin-tasklog';
import {DatasetManagerApi} from '../api';
import {HarvestConfig, ImportConfig, ImportDataset} from "../types";

class CancelledTask extends Error {
  constructor() {
    super("Task cancelled");
    this.name = "TaskCancelled";
  }
}

export default {
  components: {FilePicker, ModalWindow, PanelLogWindow},
  mixins: [MixinTasklog],
  props: {
    datasets: Array,
    api: DatasetManagerApi,
    config: Object,
  },
  data: function () {
    return {
      tab: 'copy',
      current: null,
      inProgress: false,
      cleanupOrphans: false,
      copyFrom: null,
      copyType: 'convert',
      forceConvert: false,
      commit: false,
      cancelled: false,
      working: {},
      throwOnError: false,
      timestamp: (new Date()).getTime(),
    }
  },
  methods: {
    cancelOperation: async function () {
      this.println("Cancelling...");
      await this.cancelJob();
      this.cancelled = true;
    },
    cleanup: function () {
      if (this.cancelled) {
        this.println("Cancelled");
      }
      this.cancelled = false;
      // this.jobId = null;
      this.inProgress = false;
    },
    setRunning: function () {
      this.inProgress = true;
    },
    checkConfigs: async function () {
      let datasets: ImportDataset[] = this.datasets;
      for (let set of datasets) {
        let config = await this.api.getHarvestConfig(set.id);
        if (config === null) {
          this.println("Error: no import config found for set", set.name);
          return false;
        }
      }
      return true;
    },
    checkOrphansForSet: async function (set: ImportDataset, config: HarvestConfig): Promise<string[]> {
      let files = await this.api.cleanHarvestConfig(set.id, config);
      if (files.length === 0) {
        this.println("... no orphans found");
      } else {
        this.println("..." + files.length, "orphaned file(s) found");
        files.forEach(f => this.println(" -", f));
      }
      return files;
    },
    checkForOrphans: async function () {
      let datasets: ImportDataset[] = this.datasets;
      this.setRunning();

      for (let set of datasets) {
        if (this.cancelled) {
          if (this.throwOnError) {
            throw new CancelledTask();
          }
          break;
        }
        let config = await this.api.getHarvestConfig(set.id);
        if (config) {
          this.println("Checking", set.name + "...");
          this.working[set.id] = true;
          this.$emit('processing', set.id);
          try {
            await this.checkOrphansForSet(set, config);
          } catch (e) {
            this.println(e.message);
          } finally {
            delete this.working[set.id];
            this.$emit('processing-done', set.id);
          }
        } else {
          this.println("No config found for", set.name);
        }
      }
      this.cleanup();
    },
    syncAllDatasets: async function () {
      let datasets: ImportDataset[] = this.datasets;
      this.setRunning();
      for (let set of datasets) {
        if (this.cancelled) {
          if (this.throwOnError) {
            throw new CancelledTask();
          }
          break;
        }
        let config: HarvestConfig | null = await this.api.getHarvestConfig(set.id);
        if (config) {
          this.println("Syncing", set.name);
          this.working[set.id] = true;
          this.$emit('processing', set.id);
          try {
            let {url, jobId} = await this.api.harvest(set.id, config);
            await this.monitor(url, jobId);
            if (!this.cancelled && this.cleanupOrphans) {
              let orphans = this.checkOrphansForSet(set, config);
              if (orphans.length > 0) {
                await this.api.deleteFiles(set.id, this.config.input, orphans);
                await this.api.deleteFiles(set.id, this.config.output, orphans);
              }
            }
          } catch (e) {
            this.println(e.message);
          } finally {
            delete this.working[set.id];
            this.$emit('processing-done', set.id);
          }
        } else {
          this.println("No config found for", set.name);
        }
      }
      if (!this.cancelled) {
        this.println("All sets synced")
      }
      this.cleanup();
    },
    convertAllDatasets: async function () {
      let datasets: ImportDataset[] = this.datasets;
      this.setRunning();

      for (let set of datasets) {
        if (this.cancelled) {
          if (this.throwOnError) {
            throw new CancelledTask();
          }
          break;
        }
        this.println("Converting", set.name);
        let config = await this.api.getConvertConfig(set.id);
        this.working[set.id] = true;
        this.$emit('processing', set.id);

        try {
          let {url, jobId} = await this.api.convert(set.id, null, {
            mappings: config,
            force: this.forceConvert
          });
          await this.monitor(url, jobId);
        } catch (e) {
          this.println(e.message);
        } finally {
          delete this.working[set.id];
          this.$emit('processing-done', set.id);
        }
      }
      if (!this.cancelled) {
        this.println("All sets converted")
      }
      this.cleanup();
    },
    importAllDatasets: async function () {
      let datasets: ImportDataset[] = this.datasets;
      this.setRunning();

      for (let set of datasets) {
        if (this.cancelled) {
          if (this.throwOnError) {
            throw new CancelledTask();
          }
          break;
        }

        let config = await this.api.getImportConfig(set.id);
        if (config) {
          this.println("Importing", set.name);
          this.working[set.id] = true;
          this.$emit('processing', set.id);
          try {
            let {url, jobId} = await this.api.ingestFiles(set.id, [], config, this.commit);
            await this.monitor(url, jobId);
          } catch (e) {
            this.println(e.message);
          } finally {
            delete this.working[set.id];
            this.$emit('processing-done', set.id);
          }
        } else {
          this.println("No import config found for", set.name);
        }
      }

      if (!this.cancelled) {
        this.println("All sets imported")
      }
      this.cleanup();
    },
    runAll: async function () {
      try {
        this.throwOnError = true;
        await this.syncAllDatasets();
        await this.convertAllDatasets();
        await this.importAllDatasets();
      } catch (e) {
        if (!(e instanceof CancelledTask)) {
          throw e;
        }
      } finally {
        this.cleanup();
        this.throwOnError = false;
      }
    },
    copyConvertSettings: async function () {
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
    copyImportSettings: async function () {
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
    copySettings: function () {
      if (this.copyType === 'import') {
        this.copyImportSettings();
      } else if (this.copyType === 'convert') {
        this.copyConvertSettings();
      }
    },
    resize: function () {
      this.timestamp = (new Date()).getTime();
    }
  },
}
</script>

<template>
  <modal-window v-bind:resizable="true" v-on:close="$emit('close')" v-on:move="resize">
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

      <div v-if="tab === 'import' || tab === 'all'" class="form-group form-check">
        <input class="form-check-input" id="opt-commit" type="checkbox" v-model="commit"/>
        <label class="form-check-label" for="opt-commit">
          Commit import
        </label>
      </div>
    </fieldset>

    <div class="log-container" id="batch-ops-log">
      <panel-log-window v-bind:log="log" v-bind:resize="timestamp" v-bind:visible="true"/>
    </div>
    <template v-slot:footer>
      <button v-bind:disabled="!inProgress" v-on:click="cancelOperation"
              v-bind:class="{'btn-default': !inProgress, 'btn-warning': inProgress}"
              type="button" class="btn">
        <i v-if="cancelled" class="fa fa-fw fa-circle-o-notch" v-bind:class="{'fa-spin': inProgress}"></i>
        <i v-else class="fa fa-fw fa-stop-circle"></i>
        Cancel Operation
      </button>
      <button v-if="tab === 'copy'" v-bind:disabled="inProgress || !(copyFrom && copyType)" v-on:click="copySettings"
              type="button" class="btn btn-secondary">
        <i v-if="!inProgress" class="fa fa-fw fa-copy"></i>
        <i v-else class="fa fa-fw fa-circle-o-notch fa-spin"></i>
        Copy Settings
      </button>
      <template v-if="tab === 'sync'">
        <button v-bind:disabled="inProgress || datasets.find(d => d.src = 'rs') === undefined"
                v-on:click="checkForOrphans" type="button" class="btn btn-default">
          <i class="fa fa-fw fa-trash-o"></i>
          Check for Orphaned Files
        </button>
        <button v-bind:disabled="inProgress || datasets.find(d => d.src = 'rs') === undefined"
                v-on:click="syncAllDatasets" type="button" class="btn btn-secondary">
          <i v-if="!inProgress" class="fa fa-fw fa-clone"></i>
          <i v-else class="fa fa-fw fa-circle-o-notch fa-spin"></i>
          Synchronise ResourceSync Datasets
        </button>
      </template>
      <button v-if="tab === 'convert'" v-bind:disabled="inProgress" v-on:click="convertAllDatasets" type="button"
              class="btn btn-secondary">
        <i v-if="!inProgress" class="fa fa-fw fa-file-code-o"></i>
        <i v-else class="fa fa-fw fa-circle-o-notch fa-spin"></i>
        Run Convert
      </button>
      <template v-if="commit">
        <button v-if="tab === 'import'" v-bind:disabled="inProgress" v-on:click="importAllDatasets" type="button"
                class="btn btn-danger">
          <i v-if="!inProgress" class="fa fa-fw fa-database"></i>
          <i v-else class="fa fa-fw fa-circle-o-notch fa-spin"></i>
          Run Import
        </button>
        <button v-if="tab === 'all'" v-bind:disabled="inProgress" v-on:click="runAll" type="button"
                class="btn btn-danger">
          <i v-if="!inProgress" class="fa fa-fw fa-database"></i>
          <i v-else class="fa fa-fw fa-circle-o-notch fa-spin"></i>
          Synchronise, Convert &amp; Import
        </button>
      </template>
      <template v-else>
        <button v-if="tab === 'import'" v-bind:disabled="inProgress" v-on:click="importAllDatasets" type="button"
                class="btn btn-secondary">
          <i v-if="!inProgress" class="fa fa-fw fa-database"></i>
          <i v-else class="fa fa-fw fa-circle-o-notch fa-spin"></i>
          Run Dry Run
        </button>
        <button v-if="tab === 'all'" v-bind:disabled="inProgress" v-on:click="runAll" type="button"
                class="btn btn-secondary">
          <i v-if="!inProgress" class="fa fa-fw fa-database"></i>
          <i v-else class="fa fa-fw fa-circle-o-notch fa-spin"></i>
          Synchronise, Convert &amp; Dry Run Import
        </button>
      </template>
    </template>
  </modal-window>
</template>
