<script lang="ts">

import {toRaw} from "vue";
import ModalWindow from './_modal-window';
import ModalAlert from './_modal-alert';
import PanelCodeView from './_panel-code-view';

import _isObject from 'lodash/isObject';
import {FileValidationError} from "../types";

export default {
  components: {PanelCodeView, ModalWindow, ModalAlert},
  props: {
    jsonObject: Object,
  },
  emits: ["saved", "close"],
  data: function () {
    return {
      data: JSON.stringify(this.jsonObject, null, 2),
      errors: [] as FileValidationError[],
    }
  },
  computed: {
    isValid(): boolean {
      try {
        let test = JSON.parse(this.data);
        this.errors = [];
        return _isObject(test);
      } catch (e) {
        this.errors = [{
          line: 1,
          pos: 0,
          error: e.message
        }];
        return false;
      }
    }
  }
};
</script>

<template>
  <modal-window v-bind:resizable="true" v-on:close="$emit('close')">
    <template v-slot:title>Edit Parameters...</template>
    <panel-code-view v-model="data" v-bind:content-type="'application/json'" v-bind:errors="errors"></panel-code-view>
    <template v-slot:footer>
      <button v-bind:disabled="!isValid" v-on:click="$emit('saved', JSON.parse(data))" type="button"
              class="btn btn-secondary">
        <i class="fa fa-fw fa-save"></i>
        Save Parameters
      </button>
    </template>
  </modal-window>
</template>

<style scoped>
.code-view-container {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  right: 0;
}
</style>
