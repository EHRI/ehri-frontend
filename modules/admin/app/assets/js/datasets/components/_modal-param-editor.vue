<script lang="ts">

import ModalWindow from './_modal-window';
import ModalAlert from './_modal-alert';

import _isObject from 'lodash-es/isObject';

export default {
  components: {ModalWindow, ModalAlert},
  props: {
    obj: Object,
  },
  data: function() {
    return {
      data: JSON.stringify(this.obj, null, 2),
    }
  },
  computed: {
    isValid: function(): boolean {
      try {
        let test = JSON.parse(this.data);
        return _isObject(test);
      } catch(e) {
        return false;
      }
    }
  }
};
</script>

<template>
  <modal-window v-on:close="$emit('close')">
    <template v-slot:title>Edit Parameters...</template>
    <fieldset class="options-form">
      <div class="form-group">
        <label class="form-label" for="dataset-json">JSON object</label>
        <textarea rows="4" v-model="data" id="dataset-json" class="form-control" placeholder="Parameters must be formatted as a JSON object"/>
      </div>
    </fieldset>
    <template v-slot:footer>
      <button v-bind:disabled="!isValid" v-on:click="$emit('saved', JSON.parse(data))" type="button" class="btn btn-secondary">
        <i class="fa fa-fw fa-save"></i>
        <template>Save Parameters</template>
      </button>
    </template>
  </modal-window>
</template>

