<script lang="ts">

import ModalWindow from './_modal-window';
import ModalAlert from './_modal-alert';

import _isObject from 'lodash-es/isObject';

export default {
  components: {ModalWindow, ModalAlert},
  props: {
  },
  data: function() {
    return {
      data: "",
    }
  },
  computed: {
    isValid: function(): boolean {
      return this.data.trim() !== "";
    }
  }
};
</script>

<template>
  <modal-window v-on:close="$emit('close')">
    <template v-slot:title>Create Snapshot...</template>
    <fieldset class="options-form">
      <div class="form-group">
        <label class="form-label sr-only" for="opt-snapshot-notes">Notes</label>
        <textarea rows="4" v-model="data" id="opt-snapshot-notes" class="form-control" placeholder="Notes..."/>
      </div>
    </fieldset>
    <template v-slot:footer>
      <button v-bind:disabled="!isValid" v-on:click="$emit('create', data)" type="button" class="btn btn-secondary">
        <i class="fa fa-fw fa-save"></i>
        <template>Save Snapshot</template>
      </button>
    </template>
  </modal-window>
</template>

