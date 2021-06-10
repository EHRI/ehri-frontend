<script lang="ts">

import ModalWindow from './_modal-window';
import ModalAlert from './_modal-alert';

import {DatasetManagerApi} from '../api';

export default {
  components: {ModalWindow, ModalAlert},
  props: {
    api: DatasetManagerApi,
    config: Object,
  },
  data: function() {
    return {
      data: "",
      error: null,
      saving: false,
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
  },
};
</script>

<template>
  <modal-window v-on:close="$emit('close')">
    <template v-slot:title>Import datasets...</template>
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
        <label class="form-label" for="dataset-json">JSON data</label>
        <textarea rows="16" v-model="data" id="dataset-json" class="form-control" placeholder="Data must be formatted as a list of JSON objects"/>
      </div>
    </fieldset>
    <template v-slot:footer>
      <button v-bind:disabled="saving || !data" v-on:click="save" type="button" class="btn btn-secondary">
        <i v-if="saving" class="fa fa-fw fa-spin fa-circle-o-notch"></i>
        <i v-else class="fa fa-fw fa-save"></i>
        <template>Import data</template>
      </button>
    </template>
  </modal-window>
</template>

