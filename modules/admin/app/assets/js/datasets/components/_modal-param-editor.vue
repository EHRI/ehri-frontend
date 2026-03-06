<script lang="ts">

import ModalWindow from './_modal-window';
import ModalAlert from './_modal-alert';
import EditorJson from './_editor-json';

import _isObject from 'lodash/isObject';

export default {
  components: {EditorJson, ModalWindow, ModalAlert},
  props: {
    jsonObject: Object,
  },
  data: function () {
    return {
      data: this.jsonObject
    }
  },
  computed: {
    isValid: function (): boolean {
      try {
        let test = JSON.parse(this.data);
        return _isObject(test);
      } catch (e) {
        return false;
      }
    }
  }
};
</script>

<template>
  <modal-window v-bind:resizable="true" v-on:close="$emit('close')">
    <template v-slot:title>Edit Parameters...</template>
    <editor-json v-model="data" v-bind:content-type="'application/json'"></editor-json>
    <template v-slot:footer>
      <button v-bind:disabled="!isValid" v-on:click="$emit('saved', data)" type="button"
              class="btn btn-secondary">
        <i class="fa fa-fw fa-save"></i>
        <template>Save Parameters</template>
      </button>
    </template>
  </modal-window>
</template>

