<script lang="ts">

import MixinUtil from './_mixin-util';
import MixinError from './_mixin-error';
import {DatasetManagerApi} from "../api";
import {Coreference} from "../types";
import _includes from 'lodash/includes';

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
      filter: "",
      set: null,
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
    importCoreferenceTable: async function() {
      this.importInProgress = true;
      try {
        this.result = await this.api.ingestCoreferenceTable();
      } catch (e) {
        this.showError("Error importing coreference table", e);
      } finally {
        this.importInProgress = false;
      }
    },
    countObjValues: function(obj: object, key: string): number {
      return (obj && obj[key])
          ? Object
            .keys(obj[key])
            .map(k => obj[key][k].length)
            .reduce((a, b) => a + b, 0)
          : 0;
    },
    isFiltered: function() {
      return this.set !== null || this.filter.trim() !== "";
    },
  },
  computed: {
    created: function() {
      return this.countObjValues(this.result, "created_keys");
    },
    updated: function() {
      return this.countObjValues(this.result, "updated_keys");
    },
    sets: function(): string[] {
      let out = [];
      let refs = this.references as Coreference[];
      for (let r of refs) {
        if (!_includes(out, r.setId)) {
          out.push(r.setId);
        }
      }
      return out;
    },
    filteredRefs: function () {
      let refs = this.references as Coreference[];
      let trimFilter = this.filter.trim().toLowerCase();
      let isFiltered = this.isFiltered();
      let match = (ref: Coreference) => !isFiltered || (
            (this.set === null || ref.setId === this.set) &&
            (trimFilter === "" || ref.text.toLowerCase().includes(trimFilter)));
      return this.references.filter(match);
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
    <div class="actions-bar">
      <div class="filter-control">
        <label class="sr-only">Filter references</label>
        <input v-model="filter" v-bind:disabled="references.length===0" type="text" placeholder="Filter references..." class="filter-input form-control form-control-sm">
        <i class="filtering-indicator fa fa-close fa-fw" style="cursor: pointer" v-on:click="filter = ''" v-if="isFiltered()"/>
        <select v-model="set" v-bind:disabled="references.length===0 || sets.length < 2" class="form-control form-control-sm">
          <option v-bind:value="null"></option>
          <option v-for="setId in sets" v-bind:value="setId">{{ setId }}</option>
        </select>
      </div>
      <button v-on:click.prevent="saveCoreferenceTable" class="btn btn-sm btn-info">
        <i v-if="!saveInProgress" class="fa fa-fw fa-refresh"></i>
        <i v-else class="fa fa-fw fa-circle-o-notch fa-spin"></i>
        Save Coreference Table
      </button>
      <button v-on:click.prevent="importCoreferenceTable" class="btn btn-sm btn-danger">
        <i v-if="!importInProgress" class="fa fa-fw fa-database"></i>
        <i v-else class="fa fa-fw fa-circle-o-notch fa-spin"></i>
        Import Coreference Table
      </button>
    </div>
    <p class="admin-help-notice">
      The coreference table aligns access point labels (text strings) to vocabulary items. The
      table can be imported following a data ingest to connect newly-created items, or reconnect updated
      ones, to vocabulary items.
    </p>

    <p v-if="result" class="alert alert-success">
      Created: <strong>{{ created }}</strong>
      Updated: <strong>{{ updated }}</strong>
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
          <tr v-for="ref in filteredRefs">
            <td v-if="isFiltered()"><strong>{{ ref.text }}</strong></td>
            <td v-else>{{ ref.text }}</td>
            <td>{{ ref.targetId }}</td>
            <td>{{ ref.setId }}</td>
          </tr>
          </tbody>
        </table>
        <p v-else class="info-message">
          No coreferences saved
        </p>
        <p v-if="references.length > 0 && filteredRefs.length === 0" class="info-message">
          No matching references found
        </p>
      </div>
    </div>
    <div v-else class="coreference-loading-indicator">
      <h3>Loading references...</h3>
      <i class="fa fa-3x fa-spinner fa-spin"></i>
    </div>
  </div>
</template>
