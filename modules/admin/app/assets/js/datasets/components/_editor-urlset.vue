<script lang="ts">

import {nextTick} from "vue";

import _padStart from 'lodash/padStart';
import _clone from 'lodash/clone';
import ModalAlert from './_modal-alert';
import {decodeTsv, encodeTsv} from "../common";

/**
 * FIXME: massive duplication with the tabular XQuery editor here
 */

export default {
  components: {ModalAlert},
  props: {
    modelValue: String,
  },
  data: function(): Object {
    return {
      mappings: this.deserialize(this.modelValue),
      selected: -1,
      pasteHelper: false,
      pasteText: "",
      confirmPaste: false,
    }
  },
  methods: {
    _padStart,

    update: function(): Promise<void> {
      this.$emit('update:modelValue', this.serialize(this.mappings));
      // Return a promise when the DOM is ready...
      return nextTick();
    },
    focus: function(row, col): void {
      let elem = this.$refs[_padStart(row, 4, '0') + '-' + col];
      if (elem && elem[0]) {
        elem[0].focus();
      }
    },
    add: function(): void {
      // Insert a new item below the current selection, or
      // at the end if nothing is selected.
      let point = this.selected === -1
          ? this.mappings.length
          : this.selected + 1;
      this.mappings.splice(point, 0, ["", ""])
      this.selected = point;
      this.update()
          .then(() => this.focus(this.selected, 0));
    },
    duplicate: function(i): void {
      let m = _clone(this.mappings[i]);
      this.selected = i + 1;
      this.mappings.splice(this.selected, 0, m);
      this.update();
    },
    remove: function(i): void {
      this.mappings.splice(i, 1);
      this.selected = Math.min(i, this.mappings.length - 1);
      this.update();
    },
    deserialize: function(str): string[][] {
      return str ? decodeTsv(str, 2) : [];
    },
    serialize: function(mappings: string[][]): string {
      return mappings ? encodeTsv(mappings, 2) : "";
    },
    acceptPasteInput: function() {
      // Hack around Firefox not having clipboard import
      if (this.pasteHelper) {
        this.mappings = this.deserialize(this.pasteText.trim());
        this.update();
        this.pasteHelper = false;
        this.pasteText = "";
      }
    },
    copyToClipboard: function() {
      navigator.clipboard.writeText(this.serialize(this.mappings));
    },
    importFromClipboard: function() {
      if (typeof navigator.clipboard.readText === 'function') {
        navigator.clipboard.readText().then(text => {
          this.mappings = this.deserialize(text.trim());
          this.update();
        });
      }
    },
    confirmImportFromClipboard: function() {
      if (typeof navigator.clipboard.readText === 'function') {
        if (this.mappings && this.mappings.length > 0) {
          this.confirmPaste = true;
        } else {
          this.importFromClipboard();
        }
      } else {
        // If we have no readText function we show a textarea the user can paste into:
        this.pasteHelper = true;
        this.pasteText = this.modelValue;
        nextTick().then(() =>{
          let el = this.$el.querySelector("textarea");
          if (el) {
            el.focus();
            el.select();
          }
        })
      }
    }
  },
  watch: {
    modelValue: function(newValue) {
      this.mappings = this.deserialize(newValue);
    }
  }
};
</script>

<template>
  <div class="urlset-editor tabular-editor">
    <div class="tabular-editor-data" v-on:keyup.esc="selected = -1">
      <div class="tabular-editor-header">
        <input readonly disabled type="text" value="url" @click="selected = -1"/>
        <input readonly disabled type="text" value="target-filename" @click="selected = -1"/>
      </div>

      <modal-alert
          v-if="confirmPaste"
          v-bind:title="'Overwrite existing contents?'"
          v-bind:accept="'Yes, overwrite'"
          v-on:accept="importFromClipboard(); confirmPaste = false"
          v-on:close="confirmPaste = false"
      />

      <textarea v-if="pasteHelper" placeholder="Paste TSV here..." class="textarea-paste-helper" v-model="pasteText"></textarea>
      <div v-else class="tabular-editor-mappings">
        <template v-for="(mapping, row) in mappings">
          <input
              v-for="col in [0, 1]"
              type="text"
              v-bind:ref="_padStart(row, 4, 0) + '-' + col"
              v-bind:key="_padStart(row, 4, 0) + '-' + col"
              v-bind:class="{'selected': selected === row}"
              v-model="mappings[row][col]"
              v-on:change="update"
              v-on:focusin="selected = row" />
        </template>
      </div>
    </div>
    <div class="tabular-editor-toolbar">
      <button class="btn btn-default btn-sm" v-on:click="add">
        <i class="fa fa-plus"></i>
        Add URL/name
      </button>
      <button class="btn btn-default btn-sm" v-bind:disabled="selected < 0" v-on:click="duplicate(selected)">
        <i class="fa fa-copy"></i>
        Duplicate
      </button>
      <button class="btn btn-default btn-sm" v-bind:disabled="selected < 0" v-on:click="remove(selected)">
        <i class="fa fa-trash-o"></i>
        Delete URL/name
      </button>
      <button v-if="pasteHelper" class="btn btn-default btn-sm" v-on:click.prevent.stop="acceptPasteInput">
        <i class="fa fa-check text-success"></i>
        Accept TSV Input...
      </button>
      <button v-else class="btn btn-default btn-sm" v-on:click.prevent.stop="confirmImportFromClipboard">
        <i class="fa fa-clipboard"></i>
        Import TSV From Clipboard
      </button>
      <button class="btn btn-default btn-sm" v-on:click.prevent.stop="copyToClipboard" title="Copy to Clipboard">
        <i class="fa fa-copy"></i>
      </button>
      <div class="tabular-editor-toolbar-info">
        URLs: {{mappings.length}}
      </div>
    </div>
  </div>
</template>

