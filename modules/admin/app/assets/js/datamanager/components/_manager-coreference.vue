<script lang="ts">

import MixinUtil from './_mixin-util';
import MixinError from './_mixin-error';
import {DatasetManagerApi} from "../api";

export default {
  mixins: {MixinUtil, MixinError},
  props: {
    config: Object,
    api: DatasetManagerApi,
  },
  data: function() {
    return {
      references: [],
      saveInProgress: false,
      importInProgress: false,
      loading: false,
      result: null,
      initialised: false,
    }
  },
  methods: {
    refresh: function() {
      this.loading = true;
      return this.api.getCoreferenceTable()
        .then(refs => this.references = refs)
        .catch(e => this.showError("Error loading coreference data", e))
        .finally(() => this.loading = false);
    },
    saveCoreferenceTable: function() {
      this.saveInProgress = true;
      this.api.saveCoreferenceTable()
        .then(r => {
          this.refresh();
          console.log("Done!", r);
        })
        .catch(e => this.showError("Error saving coreference table", e))
        .finally(() => this.saveInProgress = false);
    },
    importCoreferenceTable: function() {
      this.importInProgress = true;
      this.api.ingestCoreferenceTable()
        .then(data => {
          this.result = data;
          console.log(data);
        })
        .catch(e => this.showError("Error importing coreference table", e))
        .finally(() => this.importInProgress = false);
    }
  },
  created() {
    this.refresh()
      .then(() => this.initialised = true);
  }
}
</script>
<template>
  <div id="coreference-manager">
    <div id="coreference-manager-header">
      <h2>Access Point Coreference Table</h2>
      <button v-on:click="$emit('close')" class="btn btn-sm btn-default">
        <i class="fa fa-arrow-left"></i>
        Back to dataset list
      </button>
    </div>
    <p>
      The coreference table aligns access point labels (text strings) to vocabulary items. The
      table can be imported following a data ingest to connect newly-created items, or reconnect updated
      ones, to vocabulary items.

      <button v-on:click.prevent="saveCoreferenceTable" class="btn btn-sm btn-success">
        <i v-if="!saveInProgress" class="fa fa-fw fa-list"></i>
        <i v-else class="fa fa-fw fa-circle-o-notch fa-spin"></i>
        Save Coreference Table
      </button>
    </p>

    <h4>Import Coreference Table</h4>
    <p>
      <button v-on:click.prevent="importCoreferenceTable" class="btn btn-sm btn-danger">
        <i v-if="!importInProgress" class="fa fa-fw fa-database"></i>
        <i v-else class="fa fa-fw fa-circle-o-notch fa-spin"></i>
        Import Coreference Table
      </button>
      <span v-if="result" id="coreference-import-result">
        Created: <strong>{{ result.created_keys ? Object.keys(result.created_keys).length : 0 }}</strong>
        Updated: <strong>{{ result.updated_keys ? Object.keys(result.updated_keys).length : 0 }}</strong>
      </span>
    </p>

    <div id="coreference-manager-coreference-list" v-if="initialised">
      <h4>Coreferences: {{ references.length }}</h4>
      <div id="coreference-manager-coreference-table">
        <table v-if="references.length > 0" class="table table-sm table-striped table-bordered">
          <thead>
          <tr>
            <th>Text</th>
            <th>Target ID</th>
            <th>Set ID</th>
          </tr>
          </thead>
          <tbody>
          <tr v-for="ref in references">
            <td>{{ ref.text }}</td>
            <td>{{ ref.targetId }}</td>
            <td>{{ ref.setId }}</td>
          </tr>
          </tbody>
        </table>
        <p v-else class="info-message">
          No coreferences saved
        </p>
      </div>
    </div>
    <div v-else class="coreference-loading-indicator">
      <h3>Loading references...</h3>
      <i class="fa fa-3x fa-spinner fa-spin"></i>
    </div>
  </div>
</template>
