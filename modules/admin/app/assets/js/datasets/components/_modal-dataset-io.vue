<script lang="ts">

import ModalWindow from './_modal-window';
import ModalAlert from './_modal-alert';

import {DatasetManagerApi} from '../api';
import _pick from 'lodash/pick';
import {ImportDataset, ImportDatasetInfo} from "../types";


export default {
  components: {ModalWindow, ModalAlert},
  props: {
    api: DatasetManagerApi,
    config: Object,
    datasets: Array,
  },
  data: function() {
    return {
      data: this.exportData(),
      error: null,
      saving: false,
      mode: 'export',
    }
  },
  methods: {
    save: function(): void {
      this.saving = true;

      this.api.importDatasets(this.id, this.data).then(ds => this.$emit('saved', ds))
          .catch(error => {
        if (error.response && error.response.data && error.response.data.error) {
          this.error = error.response.data.error;
        } else {
          throw error;
        }
      }).finally(() => this.saving = false);
    },
    exportData: function() {
      function dump(i: ImportDataset): ImportDatasetInfo {
        return {
          id: i.id,
          name: i.name,
          src: i.src,
          contentType: i.contentType,
          notes: i.notes,
          sync: i.sync,
          status: i.status,
          fonds: i.fonds,
        } as ImportDatasetInfo;
      }
      return JSON.stringify(this.datasets.map(dump), null, 2);
    }
  },
  watch: {
    mode: function (value) {
      this.data = this.mode === 'export'
          ? this.exportData()
          : "";
    }
  }
};
</script>

<template>
  <modal-window v-on:close="$emit('close')">
    <template v-slot:title>Import/export datasets...</template>
    <modal-alert v-if="error"
                 v-on:accept="error = null"
                 v-on:close="error = null"
                 v-bind:cancel="null"
                 v-bind:title="'Error saving data...'"
                 v-bind:cls="'warning'">
      <p>{{error}}</p>
    </modal-alert>

    <fieldset class="options-form">
      <div class="form-group">
        <label class="sr-only form-label" for="dialog-mode">
          Mode
        </label>
        <select v-model="mode" class="form-control" id="dialog-mode">
          <option v-bind:value="'export'">Export</option>
          <option v-bind:value="'import'">Import</option>
        </select>
      </div>
      <div class="form-group">
        <label class="form-label" for="dataset-json">JSON data</label>
        <textarea rows="16" v-model="data" id="dataset-json" class="form-control" placeholder="Data must be formatted as a list of JSON objects"/>
      </div>
    </fieldset>
    <template v-slot:footer>
      <button v-bind:disabled="mode !== 'import' || saving || !data" v-on:click="save" type="button" class="btn btn-secondary">
        <i v-if="saving" class="fa fa-fw fa-spin fa-circle-o-notch"></i>
        <i v-else class="fa fa-fw fa-save"></i>
        <template>Import data</template>
      </button>
    </template>
  </modal-window>
</template>

