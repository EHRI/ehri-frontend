<script lang="ts">

import ModalWindow from './_modal-window';
import ModalAlert from './_modal-alert';
import FormHttpBasicAuth from './_form-http-basic-auth';
import {DatasetManagerApi} from '../api';

export default {
  components: {FormHttpBasicAuth, ModalAlert, ModalWindow},
  props: {
    waiting: Boolean,
    datasetId: String,
    opts: Object,
    api: DatasetManagerApi,
    config: Object,
  },
  data: function() {
    return {
      url: this.opts ? this.opts.url : null,
      filter: this.opts ? this.opts.filter : null,
      auth: this.opts ? this.opts.auth : null,
      tested: null,
      testing: false,
      cleaning: false,
      error: null,
      orphanCheck: null,
    }
  },
  computed: {
    isValidConfig: function() {
      return this.url && this.url.trim() !== ""
          && (!this.auth || (this.auth.username !== "" && this.auth.password !== ""));
    },
  },
  methods: {
    save: function() {
      this.$emit("saving");
      this.api.saveHarvestConfig(this.datasetId, {url: this.url, filter: this.filter, auth: null})
          .then(data => this.$emit("saved-config", {...data, auth: this.auth}))
          .catch(error => this.$emit("error", "Error saving RS config", error));
    },
    testEndpoint: function() {
      this.testing = true;
      this.api.testHarvestConfig(this.datasetId, {url: this.url, filter: this.filter, auth: this.auth})
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
      this.api.cleanHarvestConfig(this.datasetId, {url: this.url, filter: this.filter})
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
      this.url = newValue ? newValue.url : null;
      this.filter = newValue ? newValue.filter : null;
    }
  },
}
</script>

<template>
  <modal-window v-on:close="$emit('close')">
    <template v-slot:title>ResourceSync Endpoint Configuration</template>

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

    <div class="options-form">
      <div class="form-group">
        <label class="form-label" for="opt-endpoint-url">
          ResourceSync capability list endpoint URL
        </label>
        <input class="form-control" id="opt-endpoint-url" type="url" v-model.trim="url" placeholder="(required)"/>
      </div>
      <div class="form-group">
        <label class="form-label" for="opt-filter">
          ResourceSync path filter RegEx
        </label>
        <input class="form-control" id="opt-filter" type="text" v-model.trim="filter" placeholder="(optional)"/>
      </div>

      <form-http-basic-auth v-model="auth"/>

      <div id="endpoint-errors">
        <span v-if="tested === null">&nbsp;</span>
        <span v-else-if="tested" class="text-success">No errors detected</span>
        <span v-else-if="error" class="text-danger">{{error}}</span>
        <span v-else class="text-danger">Test unsuccessful</span>
      </div>
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
        Sync Endpoint
      </button>
    </template>
  </modal-window>
</template>

