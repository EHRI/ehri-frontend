<script lang="ts">

import ModalWindow from './_modal-window';
import FormHttpBasicAuth from './_form-http-basic-auth';
import {DatasetManagerApi} from '../api';

export default {
  components: {FormHttpBasicAuth, ModalWindow},
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
      format: this.opts ? this.opts.format : null,
      set: this.opts ? this.opts.set : null,
      auth: this.opts ? this.opts.auth : null,
      tested: null,
      testing: false,
      error: null,
      noResume: false,
    }
  },
  computed: {
    isValidConfig: function(): boolean {
      return this.url
          && this.url.trim() !== ""
          && this.format
          && this.format.trim() !== ""
          && (!this.auth || (this.auth.username !== "" && this.auth.password !== ""));
    },
  },
  methods: {
    save: function() {
      this.$emit("saving");
      // auth data is not saved on the server, so don't send it...
      this.api.saveHarvestConfig(this.datasetId, {url: this.url, format: this.format, set: this.set, auth: null})
          .then(data => this.$emit("saved-config", {...data, auth: this.auth}, !this.noResume))
          .catch(error => this.$emit("error", "Error saving OAI-PMH config", error));
    },
    testEndpoint: function() {
      this.testing = true;
      this.api.testHarvestConfig(this.datasetId, {url: this.url, format: this.format, set: this.set, auth: this.auth})
          .then( r => {
            this.tested = !!r.name;
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
    }
  },
  watch: {
    opts: function(newValue) {
      this.url = newValue ? newValue.url : null;
      this.format = newValue ? newValue.format : null;
      this.set = newValue ? newValue.set : null;
      this.auth = newValue ? newValue.auth : null;
    },
  },
};
</script>

<template>
  <modal-window v-on:close="$emit('close')">
    <template v-slot:title>OAI-PMH Endpoint Configuration</template>

    <form class="options-form">
      <div class="form-group">
        <label class="form-label" for="opt-endpoint-url">
          OAI-PMH endpoint URL
        </label>
        <input class="form-control" id="opt-endpoint-url" type="url" v-model.trim="url" placeholder="(required)"/>
      </div>
      <div class="form-group">
        <label class="form-label" for="opt-format">
          OAI-PMH metadata format
        </label>
        <input class="form-control" id="opt-format" type="text" v-model.trim="format" placeholder="(required)"/>
      </div>
      <div class="form-group">
        <label class="form-label" for="opt-set">
          OAI-PMH set
        </label>
        <input class="form-control" id="opt-set" type="text" v-model.trim="set"/>
      </div>

      <form-http-basic-auth v-model="auth"/>

      <div class="form-group">
        <div class="form-check">
          <input type="checkbox" class="form-check-input" id="opt-no-resume" v-model="noResume"/>
          <label class="form-check-label" for="opt-no-resume">
            <strong>Do not</strong> resume from last harvest timestamp
          </label>
        </div>
      </div>
      <div id="endpoint-errors">
        <span v-if="tested === null">&nbsp;</span>
        <span v-else-if="tested" class="text-success">No errors detected</span>
        <span v-else-if="error" class="text-danger">{{error}}</span>
        <span v-else class="text-danger">Test unsuccessful</span>
      </div>
    </form>

    <template v-slot:footer>
      <button v-on:click="$emit('close')" type="button" class="btn btn-default">
        Cancel
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
        <i v-if="!waiting" class="fa fa-fw fa-cloud-download"></i>
        <i v-else class="fa fa-fw fa-spin fa-circle-o-notch"></i>
        Harvest Endpoint
      </button>
    </template>
  </modal-window>
</template>

