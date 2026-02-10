<script lang="ts">

import ModalWindow from './_modal-window';
import FormConfigFileManager from './_form-config-file-manager.vue';
import {DatasetManagerApi} from "../api";
import {timeToRelative} from "../common";
import {ImportConfig, ImportDataset} from "../types";


export default {
  components: {ModalWindow, FormConfigFileManager},
  props: {
    dataset: Object as ImportDataset,
    setHierarchy: Boolean,
    api: DatasetManagerApi,
    config: Object,
    opts: Object as ImportConfig,
    waiting: Boolean,
  },
  data: function (): object {
    return {
      allowUpdates: this.opts ? this.opts.allowUpdates : false,
      useSourceId: this.opts ? this.opts.useSourceId : false,
      tolerant: this.opts ? this.opts.tolerant : false,
      defaultLang: this.opts ? this.opts.defaultLang : null,
      properties: this.opts ? this.opts.properties : null,
      hierarchyFile: this.opts ? this.opts.hierarchyFile : null,
      logMessage: this.opts ? this.opts.logMessage : "",
      batchSize: this.opts ? this.opts.batchSize : null,
      loading: false,
      commit: false,
      error: null,
    }
  },
  methods: {
    submit: function () {
      this.$emit("saving");
      this.api.saveImportConfig(
          this.dataset.id, {
            allowUpdates: this.allowUpdates,
            useSourceId: this.useSourceId,
            tolerant: this.tolerant,
            defaultLang: this.defaultLang,
            properties: this.properties,
            hierarchyFile: this.hierarchyFile,
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
      let hasRequiredHierarchy = this.dataset.setHierarchy ? this.hierarchyFile : true;
      return this.logMessage && this.logMessage.trim() !== "" && hasRequiredHierarchy;
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
          {{ $t(`ingest.allow-update`) }}
            <span class="help">{{ $t(`ingest.allow-update.description`) }}</span>
        </label>
      </div>
      <div class="form-group form-check">
        <input v-model="tolerant" class="form-check-input" id="opt-tolerant-check" type="checkbox"/>
        <label class="form-check-label" for="opt-tolerant-check">
            {{ $t(`ingest.tolerant`) }}
            <span class="help">{{ $t(`ingest.tolerant.description`) }}</span>
        </label>
      </div>
      <div class="form-group form-check">
        <input v-model="useSourceId" class="form-check-input" id="opt-useSourceId-check" type="checkbox"/>
        <label class="form-check-label" for="opt-useSourceId-check">
            {{ $t(`ingest.use-source-id`) }}
            <span class="help">{{ $t(`ingest.use-source-id.description`) }}</span>
        </label>
      </div>

        <form-config-file-manager
                suffix=".properties"
                input-id="opt-properties-file"
                v-bind:title="$t(`ingest.properties`)"
                v-bind:dataset-id="dataset.id"
                v-bind:api="api"
                v-bind:config="config"
                v-model="properties"
        />

        <form-config-file-manager v-if="dataset.setHierarchy"
          suffix=".tsv"
          input-id="opt-hierarchy-file"
          v-bind:title="$t(`ingest.hierarchy-file`)"
          v-bind:dataset-id="dataset.id"
          v-bind:api="api"
          v-bind:config="config"
          v-bind:required="dataset.setHierarchy"
          v-model="hierarchyFile"
          />

      <div class="form-group">
        <label class="form-label" for="opt-log-message">{{ $t(`ingest.log`)}}</label>
        <input v-model="logMessage" class="form-control form-control-sm" id="opt-log-message" placeholder="(required)" required />
      </div>

      <hr class="form-group" />

      <div class="form-group form-check">
        <input tabindex="-1" v-model="commit" class="form-check-input" id="opt-commit-check" type="checkbox"/>
        <label class="form-check-label" for="opt-commit-check">
            {{ $t(`ingest.commit`) }}
            <span class="help">{{ $t(`ingest.commit.description`) }}</span>
        </label>
      </div>
        <div class="form-group">
            <label class="form-check-label" for="opt-batch-size">
                {{ $t(`ingest.batch-size`)}}
            </label>
            <input v-model.number.trim="batchSize" class="form-control form-control-sm" id="opt-batch-size" type="number" min="50"/>
            <span class="help">
                {{ $t(`ingest.batch-size.description`)}}
            </span>
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
