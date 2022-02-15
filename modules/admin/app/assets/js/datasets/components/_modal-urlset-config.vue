<script lang="ts">

import ModalWindow from './_modal-window';
import ModalAlert from './_modal-alert';
import {DatasetManagerApi} from '../api';
import EditorUrlset from "./_editor-urlset.vue";
import FormHttpBasicAuth from "./_form-http-basic-auth";

export default {
  components: {EditorUrlset, ModalAlert, ModalWindow, FormHttpBasicAuth},
  props: {
    waiting: Boolean,
    datasetId: String,
    opts: Object,
    api: DatasetManagerApi,
    config: Object,
  },
  data: function() {
    return {
      urlMap: this.opts ? this.opts.urlMap : null,
      filter: this.opts ? this.opts.filter : null,
      auth: this.opts ? this.opts.auth : null,
      tested: null,
      testing: false,
      cleaning: false,
      error: null,
      orphanCheck: null,
      tab: 'urls',
    }
  },
  computed: {
    isValidConfig: function() {
      return this.urlMap !== null
          && this.urlMap.length > 0
          && (!this.auth || (this.auth.username !== "" && this.auth.password !== ""));
    },
    urlMapText: {
      get: function(): string {
        return this.urlMap
            ? this.urlMap.map(pairs => pairs.join('\t')).join('\n')
            : '';
      },
      set: function(urlMapText: string): void {
        this.urlMap = urlMapText
            ? urlMapText.split('\n').map(p => p.split('\t'))
            : [];
      }
    }
  },
  methods: {
    save: function() {
      this.$emit("saving");
      this.api.saveHarvestConfig(this.datasetId, {urlMap: this.urlMap, auth: null})
          .then(data => this.$emit("saved-config", {...data, auth: this.auth}))
          .catch(error => this.$emit("error", "Error saving URL set config", error));
    },
    testEndpoint: function() {
      this.testing = true;
      this.api.testHarvestConfig(this.datasetId, {urlMap: this.urlMap, auth: this.auth})
          .then(() => {
            this.tested = true;
            this.error = null;
          })
          .catch(e => {
            this.tested = false;
            let err = e.response.data;
            if (err.error) {
              this.error = err.error;
            }
          })
          .finally(() => this.testing = false);
    },
    cleanEndpoint: function() {
      this.cleaning = true;
      this.api.cleanHarvestConfig(this.datasetId, {urlMap: this.urlMap, auth: null})
          .then(orphans => this.orphanCheck = orphans)
          .catch(e => this.error = e.message)
          .finally(() => this.cleaning = false);
    },
    deleteOrphans: function(orphans: string[]): Promise<void> {
      return this.api.deleteFiles(this.datasetId, this.config.input, orphans)
          .then(() => this.api.deleteFiles(this.datasetId, this.config.output, orphans)
            .then(() => {
              this.$emit('deleted-orphans')
              this.orphanCheck = null
            }));

    }
  },
  watch: {
    opts: function(newValue) {
      this.urlMap = newValue ? newValue.urlMap : null;
    }
  },
}
</script>

<template>
  <modal-window v-bind:resizable="true" v-on:close="$emit('close')">
    <template v-slot:title>URL Set Configuration</template>

    <modal-alert
      v-if="orphanCheck !== null && orphanCheck.length === 0"
      v-bind:title="'No orphaned files found'"
      v-bind:cls="'success'"
      v-bind:cancel="null"
      v-on:accept="orphanCheck = null"
      v-on:close="orphanCheck = null"
        />
    <modal-alert
      v-else-if="orphanCheck !== null && orphanCheck.length > 0"
      v-bind:title="'Ophaned files found: ' + orphanCheck.length"
      v-bind:large="true"
      v-bind:accept="'Delete ' + orphanCheck.length + ' file(s)?'"
      v-on:accept="deleteOrphans(orphanCheck)"
      v-on:close="orphanCheck = null">
      <div class="confirm-orphan-delete-list">
        <pre>{{ orphanCheck.join('\n') }}</pre>
      </div>
    </modal-alert>

    <ul id="urlset-options" class="nav nav-tabs">
      <li class="nav-item">
        <a v-on:click.prevent="tab = 'urls'" v-bind:class="{active: tab === 'urls'}" class="nav-link" href="#">URLs</a>
      </li>
      <li class="nav-item">
        <a v-on:click.prevent="tab = 'options'" v-bind:class="{active: tab === 'options'}" class="nav-link" href="#">Options</a>
      </li>
    </ul>

    <div v-if="tab === 'urls'" class="urlset-editor-input">
      <editor-urlset v-model.lazy="urlMapText"/>
    </div>
    <div v-else class="options-form">
      <form-http-basic-auth v-model="auth"/>
    </div>
    <div id="endpoint-errors">
      <span v-if="tested === null">&nbsp;</span>
      <span v-else-if="tested" class="text-success">No errors detected</span>
      <span v-else-if="error" class="text-danger">{{error}}</span>
      <span v-else class="text-danger">Test unsuccessful</span>
    </div>

    <template v-slot:footer>
      <button v-on:click="$emit('close')" type="button" class="btn btn-default">
        Cancel
      </button>
      <button v-bind:disabled="cleaning" v-on:click="cleanEndpoint" type="button" class="btn btn-default">
        <i v-if="cleaning" class="fa fa-fw fa-circle-o-notch fa-spin"></i>
        <i v-else class="fa fa-fw fa-trash-o"></i>
        Remove Orphaned Files
      </button>
      <button v-bind:disabled="!isValidConfig"
              v-on:click="testEndpoint" type="button" class="btn btn-default">
        <i v-if="testing" class="fa fa-fw fa-circle-o-notch fa-spin"></i>
        <i v-else-if="tested === null" class="fa fa-fw fa-question"/>
        <i v-else-if="tested" class="fa fa-fw fa-check text-success"/>
        <i v-else class="fa fa-fw fa-close text-danger"/>
        Test Endpoint
      </button>
      <button v-bind:disabled="!isValidConfig"
              v-on:click="save" type="button" class="btn btn-secondary">
        <i v-bind:class="{'fa-clone': !waiting, 'fa-circle-o-notch fa-spin': waiting}" class="fa fa-fw"></i>
        Sync Downloads
      </button>
    </template>
  </modal-window>
</template>
