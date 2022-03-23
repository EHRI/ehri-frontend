<script lang="ts">

import Vue from 'vue';

import _padStart from 'lodash/padStart';
import _clone from 'lodash/clone';

/**
 * FIXME: massive duplication with the tabular XQuery editor here
 */

export default {
  props: {
    value: String,
  },
  data: function(): Object {
    return {
      mappings: this.deserialize(this.value),
      selected: -1,
      pasteHelper: false,
      pasteText: "",
    }
  },
  methods: {
    _padStart,

    update: function(): Promise<void> {
      this.$emit('input', this.serialize(this.mappings));
      // Return a promise when the DOM is ready...
      return Vue.nextTick();
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
      if (str !== "") {
        return str
            .split("\n")
            .map (m => {
              let parts = m.split("\t");
              return [
                parts[0] ? parts[0] : "",
                parts[1] ? parts[1] : "",
              ];
            });
      } else {
        return [];
      }
    },
    serialize: function(mappings: string[][]): string {
      return mappings.map(m => m.join("\t")).join("\n");
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
    importFromClipboard: function() {
      if (typeof navigator.clipboard.readText === 'function') {
        if (this.mappings && this.mappings.length > 0) {
          if (!window.confirm("Overwrite existing data?")) {
            return;
          }
        }
        navigator.clipboard.readText().then(text => {
          this.mappings = this.deserialize(text.trim());
          this.update();
        });
      } else {
        // If we have no readText function we show a textarea the user can paste into:
        this.pasteHelper = true;
        this.pasteText = this.value;
        Vue.nextTick().then(() =>{
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
    value: function(newValue) {
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
      <button v-else class="btn btn-default btn-sm" v-on:click.prevent.stop="importFromClipboard">
        <i class="fa fa-clipboard"></i>
        Import TSV From Clipboard
      </button>
      <div class="tabular-editor-toolbar-info">
        URLs: {{mappings.length}}
      </div>
    </div>
  </div>
</template>

