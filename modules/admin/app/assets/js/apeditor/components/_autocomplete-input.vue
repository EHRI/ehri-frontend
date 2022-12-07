<script lang="ts">

import AutocompleteSuggestion from './_autocomplete-suggestion';

export default {
  components: {AutocompleteSuggestion},
  props: {
    type: String,
    disabled: Boolean,
    api: Object,
  },
  data: function () {
    return {
      input: "",
      text: "",
      selectedIdx: -1,
      suggestions: [],
      loading: false,
      item: null,
    }
  },
  methods: {
    search: function () {
      this.input = this.text;
      if (this.text.trim().length === 0) {
        this.suggestions = [];
      } else {
        this.loading = true;
        this.api.search(this.type, this.text).then(items => {
          this.loading = false;
          this.suggestions = items;
        });
      }
    },
    setItem: function (item) {
      this.item = item;
      this.text = item.name;
    },
    selectPrev: function () {
      this.selectedIdx = Math.max(-1, this.selectedIdx - 1);
      this.setItemFromSelection();
    },
    selectNext: function () {
      this.selectedIdx = Math.min(this.suggestions.length, this.selectedIdx + 1);
      this.setItemFromSelection();
    },
    setAndChooseItem: function (item) {
      this.setItem(item);
      this.accept();
    },
    setItemFromSelection: function () {
      let idx = this.selectedIdx,
          len = this.suggestions.length;
      if (idx > -1 && len > 0 && idx < len) {
        this.setItem(this.suggestions[idx]);
      } else if (idx === -1) {
        this.text = this.input;
        this.item = null;
      }
    },
    accept: function () {
      let text = this.item ? this.item.name : this.text,
          targetId = this.item ? this.item.id : null;
      this.$emit("item-accepted", text, targetId);
      this.text = "";
      this.input = "";
      this.cancelComplete();
    },
    cancelComplete: function () {
      this.suggestions = [];
      this.selectedIdx = -1;
      this.item = null;
    }
  },
}
</script>

<template>
  <div class="ap-editor-autocomplete-widget form-group">
    <label class="control-label">Related name:</label>
    <div class="control-elements">
      <div class="input-group">
        <input class="form-control" type="text"
               v-bind:disabled="disabled"
               v-model.trim="text"
               v-on:input="search"
               v-on:keydown.up="selectPrev"
               v-on:keydown.down="selectNext"
               v-on:keydown.enter="accept"
               v-on:keydown.esc="cancelComplete"/>
        <span class="input-group-append">
                <button title="Create a new text-only access point"
                        class="btn btn-success"
                        v-bind:disabled="text.trim().length === 0"
                        v-on:click="accept">
                  <i class="fa fa-plus-circle"></i>
                </button>
              </span>
      </div>
      <div class="dropdown-list" v-if="suggestions.length">
        <div class="ap-editor-autocomplete-widget-suggestions">
          <autocomplete-suggestion
              v-for="(suggestion, i) in suggestions"
              v-bind:class="{selected: i === selectedIdx}"
              v-bind:key="suggestion.id"
              v-bind:item="suggestion"
              v-bind:selected="i === selectedIdx"
              v-on:selected="setAndChooseItem"
            />
        </div>
      </div>
    </div>
  </div>
</template>
