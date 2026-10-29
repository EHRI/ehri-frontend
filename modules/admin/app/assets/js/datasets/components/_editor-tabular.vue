<script lang="ts">

import {nextTick} from 'vue';

import _padStart from 'lodash/padStart';
import _clone from 'lodash/clone';
import _concat from 'lodash/concat';
import {decodeTsv, encodeTsv} from "../common";

const COLS = 4;

export default {
  props: {
    modelValue: String,
  },
  data: function (): Object {
    return {
      mappings: this.deserialize(this.modelValue),
      selected: -1,
    }
  },
  methods: {
    _padStart,

    update: function (): Promise<void> {
      this.$emit('update:modelValue', this.serialize(this.mappings));
      // Return a promise when the DOM is ready...
      return nextTick();
    },
    focus: function (row, col): void {
      let elem = this.$refs[_padStart(row, 4, '0') + '-' + col];
      if (elem && elem[0]) {
        elem[0].focus();
      }
    },
    add: function (): void {
      // Insert a new item below the current selection, or
      // at the end if nothing is selected.
      let point = this.selected === -1
          ? this.mappings.length
          : this.selected + 1;
      this.mappings.splice(point, 0, ["", "", "", ""])
      this.selected = point;
      this.update()
          .then(() => this.focus(this.selected, 0));
    },
    duplicate: function (i): void {
      let m = _clone(this.mappings[i]);
      this.selected = i + 1;
      this.mappings.splice(this.selected, 0, m);
      this.update();
    },
    remove: function (i): void {
      this.mappings.splice(i, 1);
      this.selected = Math.min(i, this.mappings.length - 1);
      this.update();
    },
    moveUp: function (i): void {
      if (i > 0) {
        let m = this.mappings.splice(i, 1)[0];
        this.mappings.splice(i - 1, 0, m);
        this.selected = i - 1;
        this.update();
      }
    },
    moveDown: function (i): void {
      if (i < this.mappings.length - 1) {
        let m = this.mappings.splice(i, 1)[0];
        this.mappings.splice(i + 1, 0, m);
        this.selected = i + 1;
        this.update();
      }
    },
    deserialize: function (str): string[][] {
      // Skip header row...
      return str ? decodeTsv(str, COLS).splice(1) : [];
    },
    serialize: function (mappings): string {
      let header = ["op", "columns", "to", "expr"]
      let all = _concat([header], mappings);
      return encodeTsv(all, COLS);
    },
  },
  watch: {
    modelValue: function (newValue) {
      this.mappings = this.deserialize(newValue);
    }
  }
};
</script>

<template>
  <div class="tabular-ops-editor tabular-editor">
    <div class="tabular-editor-data" v-on:keyup.esc="selected = -1">
      <div class="tabular-editor-header">
        <input readonly disabled type="text" value="op" @click="selected = -1"/>
        <input readonly disabled type="text" value="columns" @click="selected = -1"/>
        <input readonly disabled type="text" value="to" @click="selected = -1"/>
        <input readonly disabled type="text" value="expr" @click="selected = -1"/>
      </div>
      <div class="tabular-editor-mappings">
        <template v-for="(mapping, row) in mappings">
          <input
              v-for="col in [0, 1, 2, 3]"
              type="text"
              v-bind:ref="_padStart(row, 4, 0) + '-' + col"
              v-bind:key="_padStart(row, 4, 0) + '-' + col"
              v-bind:class="{'selected': selected === row}"
              v-model="mappings[row][col]"
              v-on:change="update"
              v-on:focusin="selected = row"/>
        </template>
      </div>
    </div>
    <div class="tabular-editor-toolbar ">
      <button class="btn btn-default btn-sm" v-on:click="add">
        <i class="fa fa-plus"></i>
        Add Operation
      </button>
      <button class="btn btn-default btn-sm" v-bind:disabled="selected < 0" v-on:click="duplicate(selected)">
        <i class="fa fa-copy"></i>
        Duplicate Operation
      </button>
      <button class="btn btn-default btn-sm" v-bind:disabled="selected < 0" v-on:click="remove(selected)">
        <i class="fa fa-trash-o"></i>
        Delete Operation
      </button>
      <button class="btn btn-default btn-sm" v-bind:disabled="selected < 0 || selected === 0"
              v-on:click="moveUp(selected)">
        <i class="fa fa-caret-up"></i>
        Move Up
      </button>
      <button class="btn btn-default btn-sm" v-bind:disabled="selected < 0 || selected === mappings.length - 1"
              v-on:click="moveDown(selected)">
        <i class="fa fa-caret-down"></i>
        Move Down
      </button>
      <div class="tabular-editor-toolbar-info">
        Operations: {{ mappings.length }}
      </div>
    </div>
    <div class="tabular-editor-legend">
      <dl>
        <dt>Ops</dt>
        <dd>
          <code>select</code>, <code>merge</code>, <code>split</code>, <code>derive</code> each read
          straight from the input row and contribute one or more named output columns, in row order
          &mdash; a column appears in the output only if some row produces it (with no producing
          rows at all, every input column passes through unchanged). <code>filter</code> rows decide
          which input rows survive; several <code>filter</code> rows are ANDed together and can
          appear anywhere. Row expressions use <code>?column</code> lookups against the input row.
        </dd>
        <dt>select / merge / split</dt>
        <dd>
          <code>select</code>: one input column, optionally renamed via <code>to</code>.
          <code>merge</code>: <code>columns</code> is a comma-separated list of input columns,
          joined (blank values skipped) into <code>to</code> with <code>expr</code> as the separator
          (defaults to <code>array.separator</code>). <code>split</code>: <code>columns</code> is
          the one source column, <code>to</code> a comma-separated list of new column names, and
          <code>expr</code> the separator. Each output column must come from a single row &mdash;
          combining several source columns into one? List them all in that one <code>merge</code>
          row's <code>columns</code> cell, not as separate rows; two rows targeting the same
          <code>to</code> will fail with an error rather than silently combining.
        </dd>
        <dt>Options</dt>
        <dd>
          Set via the <strong>Parameters</strong> panel (with defaults):
          <code>input.separator</code> (<code>comma</code>),
          <code>output.separator</code> (= input),
          <code>input.header</code> (<code>yes</code>),
          <code>output.header</code> (= input),
          <code>array.separator</code> (<code>||</code>).
          Separators may be <code>comma</code>, <code>semicolon</code>, <code>tab</code>, or a single character.
        </dd>
      </dl>
    </div>
  </div>
</template>
