<script lang="ts">
import {prettyDate, humanFileSize} from "../common";

import Vue from 'vue';
import { RecycleScroller } from 'vue-virtual-scroller';
import 'vue-virtual-scroller/dist/vue-virtual-scroller.css';

Vue.component('RecycleScroller', RecycleScroller);

function emptyObj() {
  return {};
}

/**
 * Files table: this can be a fairly heavy component if there are lots of files
 * so it's rendered as a functional component with no state of its own.
 */
export default {
  props: {
    files: Array,
    fileStage: String,
    loadingMore: {
      type: Boolean,
      default: false,
    },
    loaded: {
      type: Boolean,
      default: false,
    },
    truncated: {
      type: Boolean,
      default: false
    },
    previewing: {
      type: Object,
      default: emptyObj,
    },
    validating: {
      type: Object,
      default: emptyObj,
    },
    validationResults: {
      type: Object,
      default: emptyObj,
    },
    selected: {
      type: Object,
      default: emptyObj,
    },
    deleting: {
      type: Object,
      default: emptyObj,
    },
    downloading: {
      type: Object,
      default: emptyObj,
    },
    loadingInfo: {
      type: Object,
      default: emptyObj,
    },
    filter: {
      type: String,
      default: ""
    },
  },
  filters: {
    prettyDate,
    humanFileSize,
    decodeURI,
  },
};
</script>

<template>

  <RecycleScroller
    class="scroller table table-bordered table-striped table-sm"
    v-bind:items="files"
    v-bind:item-size="32"
    key-field="key"
    v-slot="{ item }"
  >
    <div class="file-item table-row d-sm-table-row">
      <div v-on:click.prevent="" class="selector table-cell d-sm-table-cell">
        <input type="checkbox"
               v-bind:checked="Boolean(selected[item.key])"
               v-on:input.stop.prevent.self="$emit('toggle-file', item)"
               v-on:click="$event.stopPropagation()">
      </div>
      <div class="file-name table-cell d-sm-table-cell">
        {{ item.key }}
      </div>
    </div>

  </RecycleScroller>
</template>


<style scoped>
.scroller {
  height: 100%;
  flex: 1;
  flex-basis: 0;
  display: grid;

}

.file-item {
  height: 32%;
  padding: 0 12px;
  display: flex;
  align-items: center;
}
</style>
