<script lang="ts">

import ModalWindow from './_modal-window';
import FormConfigFileManager from './_form-config-file-manager.vue';
import {FileMeta} from '../types';
import {DatasetManagerApi} from "../api";

import _pick from 'lodash/pick';
import _size from 'lodash/size';
import {timeToRelative} from "../common";
import _forIn from "lodash/forIn";
import _formConfigFileManager from "./_form-config-file-manager.vue";


export default {
  components: {ModalWindow, FormConfigFileManager},
  props: {
    datasetId: String,
    api: DatasetManagerApi,
    config: Object,
    opts: Object,
    waiting: Boolean,
  },
  data: function (): object {
    return {
      allowUpdates: this.opts ? this.opts.allowUpdates : false,
      useSourceId: this.opts ? this.opts.useSourceId : false,
      tolerant: this.opts ? this.opts.tolerant : false,
      defaultLang: this.opts ? this.opts.defaultLang : null,
      properties: this.opts ? this.opts.properties : null,
      logMessage: this.opts ? this.opts.logMessage : "",
      batchSize: this.opts ? this.opts.batchSize : null,
      loading: false,
      commit: false,
      error: null,
      propertyConfigs: [],
    }
  },
  methods: {
    submit: function () {
      this.$emit("saving");
      this.api.saveImportConfig(
          this.datasetId, {
            allowUpdates: this.allowUpdates,
            useSourceId: this.useSourceId,
            tolerant: this.tolerant,
            defaultLang: this.defaultLang,
            properties: this.properties,
            logMessage: this.logMessage,
            batchSize: this.batchSize || null,
          })
          .then(data => this.$emit("saved-config", data, this.commit))
          .catch(error => this.$emit("error", "Error saving import config", error));
    },
    prettyDate: timeToRelative,
  },
  computed: {
    isValidConfig: function () {
      return this.logMessage && this.logMessage.trim() !== "";
    },
  },
};
</script>

<template>
  <modal-window v-on:close="$emit('close')">
    <template v-slot:title>Ingest Settings</template>

    <fieldset v-bind:disabled="loading" class="options-form">
      <div class="form-group form-check">
        <input v-model="allowUpdates" class="form-check-input" id="opt-allowUpdates-check" type="checkbox"/>
        <label class="form-check-label" for="opt-allowUpdates-check">
          Allow updates: check this if it is expected that existing items will be modified
        </label>
      </div>
      <div class="form-group form-check">
        <input v-model="tolerant" class="form-check-input" id="opt-tolerant-check" type="checkbox"/>
        <label class="form-check-label" for="opt-tolerant-check">
          Tolerant mode: do not abort on individual item validation errors
        </label>
      </div>
      <div class="form-group form-check">
        <input v-model="useSourceId" class="form-check-input" id="opt-useSourceId-check" type="checkbox"/>
        <label class="form-check-label" for="opt-useSourceId-check">
          Use source file ID as well as language code to identify descriptions (this will allow adding multiple
          descriptions in the same language if they come from different EAD files)
        </label>
      </div>

      <form-config-file-manager
          title="Properties File"
          suffix=".properties"
          v-bind:dataset-id="datasetId"
          v-bind:api="api"
          v-bind:config="config"
          v-bind:config-options="propertyConfigs"
          v-model="properties"
          />

      <div class="form-group">
        <label class="form-label" for="opt-log-message">Log Message</label>
        <input v-model="logMessage" class="form-control form-control-sm" id="opt-log-message" placeholder="(required)"/>
      </div>

      <hr class="form-group" />

      <div class="form-group form-check">
        <input tabindex="-1" v-model="commit" class="form-check-input" id="opt-commit-check" type="checkbox"/>
        <label class="form-check-label" for="opt-commit-check">
          Commit ingest: make changes to database
        </label>
      </div>
        <div class="form-group">
            <label class="form-check-label" for="opt-batch-size">Import Batch Size</label>
            <input v-model.number.trim="batchSize" class="form-control form-control-sm" id="opt-batch-size" type="number" min="50"/>
        </div>
      <div v-if="error" class="alert alert-danger">
        {{ error }}
      </div>
    </fieldset>

    <template v-slot:footer>
      <button v-on:click="$emit('close')" type="button" class="btn btn-default">
        Cancel
      </button>
      <button v-bind:disabled="!isValidConfig" v-on:click="submit"
              v-bind:class="{'btn-danger': commit, 'btn-secondary': !commit}"
              type="button" class="btn">
        <i v-if="!waiting" class="fa fa-fw fa-database"></i>
        <i v-else class="fa fa-fw fa-spin fa-circle-o-notch"></i>
        <template v-if="commit">Run Ingest</template>
        <template v-else>Start Dry Run</template>
      </button>
    </template>
  </modal-window>
</template>
