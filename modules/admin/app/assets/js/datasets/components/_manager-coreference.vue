<script lang="ts">

import MixinUtil from './_mixin-util';
import MixinError from './_mixin-error';
import {DatasetManagerApi} from "../api";
import {Coreference} from "../types";
import _includes from 'lodash/includes';
import {decodeTsv} from "../common";
import _fromPairs from "lodash/fromPairs";

export default {
  mixins: [MixinUtil, MixinError],
  props: {
    config: Object,
    api: DatasetManagerApi,
  },
  data: function () {
    return {
      references: [],
      selected: [],
      pasteText: "",
      extractInProgress: false,
      applyInProgress: false,
      deleteInProgress: false,
      importFromTSV: false,
      validationErrors: false,
      validationMessages: "",
      loading: false,
      result: null,
      initialised: false,
      filter: "",
      set: null,
    }
  },
  methods: {
    refresh: async function () {
      this.validationErrors = false;
      this.validationMessages = "";
      try {
        this.loading = true;
        this.references = await this.api.getCoreferences();
        let refKeys = this.references.map(r => this.refKey(r));
        Object.keys(this.selected).forEach(key => {
          if (!refKeys.includes(key)) {
            delete this.selected[key];
          }
        })
      } catch (e) {
        this.showError("Error loading coreference data", e)
      } finally {
        this.loading = false;
      }
    },
    extractCoreferences: async function () {
      this.importFromTSV = false;
      this.extractInProgress = true;
      try {
        let r = await this.api.extractCoreferences();
        console.log("Extracted: ", r.imported);
        await this.refresh()
      } catch (e) {
        this.showError("Error extracting coreference table", e);
      } finally {
        this.extractInProgress = false;
      }
    },
    importCoreferences: async function () {
      if (this.importedCoreferencesValid()) {
        this.pasteText = "";
        try {
          let r = await this.api.importCoreferences(this.references);
          console.log("Imported: ", r.imported);
          await this.refresh();
        } catch (e) {
          this.showError("Error saving imported coreference table", e)
        }
      } else {
        this.validationErrors = true;
        this.validationMessages = "Invalid format for the imported coreferences, please check them!";
      }
    },
    applyCoreferences: async function () {
      this.applyInProgress = true;
      try {
        this.result = await this.api.applyCoreferences();
      } catch (e) {
        this.showError("Error importing coreference table", e);
      } finally {
        this.applyInProgress = false;
      }
    },
    setCoreferencesFromTsv: function () {
      let parsedTsv = decodeTsv(this.pasteText, 3);
      this.references = parsedTsv.map(function (value) {
        let ref: Coreference = {
          text: value[0],
          targetId: value[1],
          setId: value[2],
        };
        return ref;
      });
    },
    importedCoreferencesValid: function () {
      try {
        return this.references.every(r => r.text.length !== 0 && r.targetId.length !== 0 && r.setId.length !== 0);
      } catch (e) {
        return false;
      }
    },
    deleteCoreferences: async function () {
      try {
        this.deleteInProgress = true;
        let r = await this.api.deleteCoreferences(Object.values(this.selected));
        console.log("Deleted", r.deleted);
        await this.refresh();
      } catch (e) {
        this.showError("Error deleted selected values", e);
      } finally {
        this.deleteInProgress = false;
      }
    },
    countObjValues: function (obj: object, key: string): number {
      return (obj && obj[key])
          ? Object
              .keys(obj[key])
              .map(k => obj[key][k].length)
              .reduce((a, b) => a + b, 0)
          : 0;
    },
    isFiltered: function () {
      return this.set !== null || this.filter.trim() !== "";
    },
    updateOnTsvImport: function (value) {
      this.validationErrors = false;
      this.validationMessages = "";
      this.pasteText = value;
      this.setCoreferencesFromTsv();
    },
    toggleAll: function (toggle: boolean) {
      if (!toggle) {
        this.selected = {};
      } else {
        this.selected = _fromPairs(this.references.map(r => [this.refKey(r), r]));
      }
    },
    toggleRef: function (ref: Coreference) {
      if (this.selected[this.refKey(ref)]) {
        delete this.selected[this.refKey(ref)];
      } else {
        this.selected[this.refKey(ref)] = ref;
      }
    },
    refKey: function (ref: Coreference): string {
      return [ref.text, ref.targetId, ref.setId].join('-');
    }
  },
  computed: {
    created: function () {
      return this.countObjValues(this.result, "created_keys");
    },
    updated: function () {
      return this.countObjValues(this.result, "updated_keys");
    },
    sets: function (): string[] {
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
        <input v-model="filter" v-bind:disabled="references.length===0" type="text" placeholder="Filter references..."
               class="filter-input form-control form-control-sm">
        <i class="filtering-indicator fa fa-close fa-fw" style="cursor: pointer" v-on:click="filter = ''"
           v-if="isFiltered()"/>
        <select v-model="set" v-bind:disabled="references.length===0 || sets.length < 2"
                class="form-control form-control-sm">
          <option v-bind:value="null"></option>
          <option v-for="setId in sets" v-bind:value="setId">{{ setId }}</option>
        </select>
      </div>
      <button @click="importFromTSV = !importFromTSV" v-bind:disabled="importFromTSV" class="btn btn-sm btn-default">
        <i class="fa fa-cloud-upload"></i>
        Import from TSV
      </button>
      <button v-on:click.prevent="extractCoreferences" class="btn btn-sm btn-info" title="Build from existing terms">
        <i v-if="!extractInProgress" class="fa fa-fw fa-refresh"></i>
        <i v-else class="fa fa-fw fa-circle-o-notch fa-spin"></i>
        Extract From Data
      </button>
      <button v-on:click.prevent="applyCoreferences" class="btn btn-sm btn-danger"
              v-bind:disabled="references.length === 0">
        <i v-if="!applyInProgress" class="fa fa-fw fa-database"></i>
        <i v-else class="fa fa-fw fa-circle-o-notch fa-spin"></i>
        Apply to Data
      </button>
    </div>
    <p class="admin-help-notice">
      The coreference table aligns access point labels (text strings) to vocabulary items. The
      table can be imported following a data ingest to connect newly-created items, or reconnect updated
      ones, to vocabulary items.
    </p>

    <div class="import-coreferences-tsv" v-if="importFromTSV">
      <label for="opt-import-coreference-tsv" class="sr-only">Paste TSV here</label>
      <textarea :value="pasteText"
                @input="updateOnTsvImport($event.target.value)"
                class="form-control" id="opt-import-coreference-tsv" placeholder="Paste TSV here..."></textarea>
      <div class="import-coreferences-tsv-action-buttons">
        <button @click="importFromTSV = !importFromTSV; refresh()" class="btn btn-sm btn-default">
          <i class="fa fa-times"></i>
          Cancel
        </button>
        <button v-on:click.prevent="importCoreferences" v-bind:disabled="pasteText === ''"
                class="btn btn-sm btn-danger">
          <i class="fa fa-fw fa-floppy-o"></i>
          Save copied coreferences
        </button>
      </div>
    </div>

    <p v-if="result" class="alert alert-success">
      Created: <strong>{{ created }}</strong>
      Updated: <strong>{{ updated }}</strong>
    </p>

    <p v-if="validationErrors" class="alert alert-danger">
      {{ this.validationMessages }}
    </p>

    <div id="coreference-manager-coreference-list" v-if="initialised">
      <h4>Coreferences: {{ references.length }}
        <a href="" @click.prevent="deleteCoreferences" v-if="Object.keys(selected).length > 0"
           class="text-danger pull-right">
          <i v-if="!deleteInProgress" class="fa fa-fw fa-trash-o"></i>
          <i v-else class="fa fa-fw fa-circle-o-notch fa-spin"></i>
          Remove Selected Coreferences
        </a>
      </h4>
      <div id="coreference-manager-coreference-table">
        <table v-if="references.length > 0" class="table table-sm table-striped table-bordered">
          <thead>
          <tr>
            <th><input type="checkbox"
                       v-bind:id="'coreference-manager-coreference-table-checkall'"
                       v-bind:checked="Object.keys(selected).length === references.length"
                       v-bind:indeterminate.prop="Object.keys(selected).length > 0 && Object.keys(selected).length < references.length"
                       v-on:change="toggleAll"/></th>
            <th>Text</th>
            <th>Target ID</th>
            <th>Set ID</th>
          </tr>
          </thead>
          <tbody>
          <tr v-for="ref in filteredRefs">
            <td v-on:click.stop.prevent="toggleRef(ref)">
              <input type="checkbox"
                     v-bind:checked="Boolean(selected[refKey(ref)])"
                     v-on:input.stop.prevent.self="toggleRef(ref)"
                     v-on:click="$event.stopPropagation()">
            </td>
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
