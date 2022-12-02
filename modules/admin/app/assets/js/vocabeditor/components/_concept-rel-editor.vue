<script lang="ts">

import AutocompleteInput from './_autocomplete-input';
import ConceptHierarchy from './_concept-hierarchy';
import VocabEditorApi from "../api";
import {conceptTitle, sortByTitle} from "../common";

import _findIndex from 'lodash/findIndex';

export default {
  components: {AutocompleteInput, ConceptHierarchy},
  props: {
    api: VocabEditorApi,
    lang: String,
    id: String,
    data: Array,
    dirty: Boolean,
  },
  data: function () {
    return {
      expand: true,
      state: this.data,
      loading: false,
      saving: false,
      saved: false,
      error: false,
    }
  },
  computed: {
    sorted: function () {
      return this.state.concat().sort(sortByTitle(this.lang));
    },
    expandable: function() {
      return this.state.filter(i => i.broaderTerms.length > 0).length > 0;
    }
  },
  methods: {
    save: function() {
      this.$emit("item-rels-saved", this.state);
    },
    addBroader: function (text, id) {
      this.loading = true;
      this.api.get(id)
          .then(item => this.state.push(item))
          .then(() => this.loading = false);
    },
    removeBroader: function (item) {
      this.state.splice(_findIndex(this.state, c => c.id === item.id), 1);
    },
    conceptTitle,
  },
  watch: {
    data: function (newData) {
      this.state = newData;
    }
  },
}
</script>

<template>
  <div id="concept-editor-rels-tab" class="concept-editor-tab">
    <div class="concept-editor-broader-terms concept-editor-tab-form">
      <h4>Broader Terms
        <button v-bind:disabled="!expandable" class="btn btn-xs">
          <span v-if="!expand" v-on:click="expand = true">expand terms</span>
          <span v-if="expand" v-on:click="expand = false">collapse terms</span>
        </button>
      </h4>
      <autocomplete-input
          v-bind:api="api"
          v-bind:disabled="false"
          v-on:item-accepted="addBroader" />
      <ul v-if="state.length > 0">
        <li v-for="broader in sorted">
          {{ conceptTitle(broader, lang, id) }}
          <span title="Remove Broader Term" class="remove" v-on:click="removeBroader(broader)">
              <i class="fa fa-remove"></i>
            </span>
          <concept-hierarchy
              v-if="expand"
              v-bind:id="id"
              v-bind:data="broader.broaderTerms"
              v-bind:lang="lang"
          />
        </li>
      </ul>
      <div class="concept-editor-broader-terms-empty" v-else>No broader terms</div>
    </div>
    <div class="concept-editor-tab-form-footer" v-bind:class="{disabled:saving || loading}">
      <button v-bind:disabled="!dirty || saving" class="btn btn-danger" v-on:click="save">
        Save Concept Relationships
        <span v-if="saving"><i class="fa fa-fw fa-circle-o-notch fa-spin"></i></span>
        <span v-else-if="!dirty && saved"><i class="fa fa-fw fa-check"></i></span>
        <span v-else><i class="fa fa-fw fa-save"></i></span>
      </button>
      <button v-bind:disabled="!dirty || saving" class="pull-right btn btn-default" v-on:click="$emit('item-rels-reset')">
        Reset
        <span><i class="fa fa-fw fa-undo"></i></span>
      </button>
    </div>
  </div>
</template>
