<script lang="ts">

import ModalWindow from './_modal-window';
import ModalAlert from './_modal-alert';

import {DatasetManagerApi} from '../api';
import FormConfigFileManager from "./_form-config-file-manager.vue";
import {ImportDataset} from "../types";

export default {
  components: {FormConfigFileManager, ModalWindow, ModalAlert},
  props: {
    api: DatasetManagerApi,
    config: Object,
    info: Object as ImportDataset,
  },
  data: function () {
    return {
      id: this.info ? this.info.id : null,
      name: this.info ? this.info.name : null,
      src: this.info ? this.info.src : null,
      fonds: this.info ? this.info.fonds : null,
      setHierarchy: this.info ? this.info.setHierarchy : false,
      sync: this.info ? this.info.sync : false,
      nest: this.info ? this.info.nest : false,
      status: this.info ? this.info.status : "active",
      contentType: this.info ? this.info.contentType : null,
      notes: this.info ? this.info.notes : null,
      error: null,
      saving: false,
      deleting: false,
      showRemoveDialog: false,
      showAdvanced: false,
    }
  },
  methods: {
    save: function (): void {
      this.saving = true;

      let data = {
        id: this.id,
        name: this.name,
        src: this.src,
        fonds: this.fonds,
        setHierarchy: this.setHierarchy,
        sync: this.sync,
        nest: this.nest,
        status: this.status,
        contentType: this.contentType,
        notes: this.notes,
        loading: false,
        hierarchyMapOptions: [],
      };

      let op = this.info !== null
          ? this.api.updateDataset(this.id, data).then(ds => this.$emit('saved-dataset', ds))
          : this.api.createDataset(data).then(ds => {
            this.$emit('saved-dataset', ds);
            this.$emit('close');
          });

      op.catch(error => {
        if (error.response && error.response.data && error.response.data.error) {
          this.error = error.response.data.error;
        } else {
          throw error;
        }
      }).finally(() => this.saving = false);
    },
    remove: function (): void {
      this.deleting = true;
      this.showRemoveDialog = false;
      this.api.deleteDataset(this.id)
          .then(() => {
                this.$emit('deleted-dataset');
                this.$emit('close');
              }
          )
          .catch(error => this.error = error)
          .finally(() => this.deleting = false);
    },
  },
  watch: {
    fonds: function (newValue) {
      if (!newValue) {
        this.fonds = null;
        this.sync = false;
        this.nest = false;
      }
    }
  },
  computed: {
    isValidConfig: function (): boolean {
      return this.src !== null
          && this.name !== null
          && this.id !== null
          && this.isValidFonds;
    },
    isValidIdentifier: function (): boolean {
      return !this.id || (this.id.match(/^[a-z0-9_]+$/) !== null && this.id.length <= 50);
    },
    isValidFonds: function (): boolean {
      return !this.fonds || this.fonds.match("^" + this.config.repoId + "-.+");
    },
    hasChanged: function (): boolean {
      return this.info === null || (
              this.info.name !== this.name
              || this.info.src !== this.src
              || this.info.notes !== this.notes
              || this.info.fonds !== this.fonds
              || this.info.setHierarchy != this.setHierarchy
              || this.info.contentType !== this.contentType
              || Boolean(this.info.sync) !== Boolean(this.sync)
              || Boolean(this.info.nest) !== Boolean(this.nest))
          || this.info.status !== this.status;
    }
  },

};
</script>

<template>
  <modal-window v-on:close="$emit('close')">
    <template v-if="info === null" v-slot:title>Create New Dataset...</template>
    <template v-else v-slot:title>Update Dataset: {{ info.name }}</template>

    <modal-alert v-if="showRemoveDialog"
                 v-on:accept="remove"
                 v-on:close="showRemoveDialog = false"
                 v-bind:title="'Delete Dataset?'"
                 v-bind:accept="'Yes, delete it !'">
      <p>This will remove all files associated with the dataset and
        cannot be undone. Are you sure?</p>
    </modal-alert>
    <modal-alert v-if="error"
                 v-on:accept="error = null"
                 v-on:close="error = null"
                 v-bind:cancel="null"
                 v-bind:title="'Error saving dataset...'"
                 v-bind:cls="'warning'">
      <p>{{ error }}</p>
    </modal-alert>
    <fieldset v-bind:disabled="deleting" class="options-form">
      <div class="form-group" v-if="info === null">
        <label class="form-label" for="dataset-id">
          Identifier
          <span class="input-mandatory">*</span>
        </label>
        <input v-model="id"
               v-bind:class="{'is-invalid': !isValidIdentifier}"
               pattern="[a-z0-9_]+"
               maxlength="50"
               type="text"
               id="dataset-id"
               class="form-control"
               autofocus="autofocus"
               autocomplete="off"
               placeholder="(required)"/>
        <div class="small form-text">
          Dataset identifiers must be at least 6 characters in length
          and can only contain lower case letters, numbers and underscores.
        </div>
      </div>
      <div class="form-group">
        <label class="form-label" for="dataset-name">
          Name
          <span class="input-mandatory">*</span>
        </label>
        <input type="text" v-model="name" id="dataset-name" class="form-control" placeholder="(required)"/>
      </div>
      <div class="form-group">
        <label class="form-label" for="dataset-src">
          Type
          <span class="input-mandatory">*</span>
        </label>
        <select v-model="src" class="form-control" id="dataset-src">
          <option v-bind:value="null" disabled selected hidden>(required)</option>
          <option value="upload">Uploads</option>
          <option value="oaipmh">OAI-PMH Harvesting</option>
          <option value="rs">ResourceSync</option>
          <option value="urlset">URL Set</option>
        </select>
      </div>
      <div class="form-group">
        <label class="form-label" for="dataset-fonds">
          Fonds ID
        </label>
        <input type="text" v-model="fonds" id="dataset-fonds" class="form-control" placeholder="(optional)"/>
      </div>
      <div class="form-group form-check">
          <input v-bind:disabled="!(this.fonds && this.isValidFonds)" v-model="sync" class="form-check-input"
                 id="opt-sync" type="checkbox"/>
          <label class="form-check-label" for="opt-sync" data-toggle="tooltip" title="Move or remove existing data to match EAD structure.">
              Synchronise fonds with dataset
          </label>
      </div>
      <div class="form-group form-check">
        <input v-bind:disabled="!(this.fonds && this.isValidFonds)" v-model="nest" class="form-check-input"
               id="opt-nest" type="checkbox"/>
        <label class="form-check-label" for="opt-nest" data-toggle="tooltip"
               title="Default (non-nested) behavior assumes EAD includes the specified fonds and imports at repository level.">
            Nest items beneath specified fonds
        </label>
      </div>
      <div class="form-group form-check">
          <input v-model="setHierarchy" class="form-check-input" id="opt-set-hierarchy" type="checkbox"/>
          <label class="form-check-label" for="opt-set-hierarchy" data-toggle="tooltip" title="Construct the item hierarchy via a TSV given at ingest time.">
              Set hierarchy with TSV
          </label>
      </div>
      <div class="form-group">
        <label class="form-label" for="dataset-content-type">
          Content-Type Override
        </label>
        <input type="text" v-model="contentType" id="dataset-content-type" class="form-control"
               placeholder="text/xml; charset=utf-8"/>
      </div>
      <div class="form-group">
        <label class="form-label" for="dataset-status">
          Status
        </label>
        <select v-model="status" class="form-control" id="dataset-status">
          <option v-bind:value="'active'">Active</option>
          <option v-bind:value="'onhold'">On Hold</option>
          <option v-bind:value="'inactive'">Inactive</option>
        </select>
      </div>
      <div class="form-group">
        <label class="form-label" for="dataset-notes">Notes</label>
        <textarea rows="4" v-model="notes" id="dataset-notes" class="form-control" placeholder="(optional)"/>
      </div>
    </fieldset>
    <template v-slot:footer>
      <button v-if="info"
              v-bind:disabled="saving || deleting"
              v-on:click="showRemoveDialog = true" class="btn btn-danger" id="delete-dataset" tabindex="-1">
        <i v-if="deleting" class="fa fa-fw fa-spin fa-circle-o-notch"></i>
        <i v-else class="fa fa-fw fa-trash-o"></i>
        Delete Dataset
      </button>
      <button v-bind:disabled="saving || deleting || !hasChanged || !(isValidConfig && isValidIdentifier)"
              v-on:click="save" type="button" class="btn btn-secondary">
        <i v-if="saving" class="fa fa-fw fa-spin fa-circle-o-notch"></i>
        <i v-else class="fa fa-fw fa-save"></i>
        <template v-if="info">Save Dataset</template>
        <template v-else>Create Dataset</template>
      </button>
    </template>
  </modal-window>
</template>

