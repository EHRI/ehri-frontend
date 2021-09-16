<script lang="ts">

import ConceptRelEditor from './_concept-rel-editor';
import ConceptDataEditor from './_concept-data-editor';
import ConceptDeleter from './_concept-deleter';

import {conceptTitle, formatTimestamp} from "../common";
import VocabEditorApi from "../api";

export default {
  components: {ConceptRelEditor, ConceptDataEditor, ConceptDeleter},
  filters: {conceptTitle, formatTimestamp},
  props: {
    api: VocabEditorApi,
    lang: String,
    id: String,
    data: Object,
    dirtyData: Boolean,
    dirtyRels: Boolean,
    langData: Object,
    localeHelpers: Object,
    config: Object,
  },
  data: function () {
    return {
      tab: 'data',
      loading: false,
      saving: false,
      saved: false,
      error: false,
      errors: {},
      deleting: false,
      deleted: false,
    };
  },
  methods: {
    updateItem: function (data) {
      this.loading = true;
      this.saving = true;
      this.api.updateItem(this.id, data)
          .then(item => this.$emit('item-data-saved', item))
          .then(() => {
            this.saved = true;
            this.error = false;
            this.saving = false;
            this.loading = false;
          }).catch(err => {
        this.error = true;
        this.saving = false;
        this.loading = false;
        if (err.response.status === 400 && err.response.data) {
          this.errors = err.response.data;
        }
      });
    },
    updateRels: function (broader) {
      this.loading = true;
      this.saving = true;
      this.api.setBroader(this.id, broader.map(c => c.id))
          .then(item => this.$emit('item-rels-saved', item))
          .then(() => {
            this.saved = true;
            this.error = false;
            this.saving = false;
            this.loading = false;
          }).catch(err => {
        this.error = true;
        this.saving = false;
        this.loading = false;
      });
    },
    deletedItem: function (data) {
      this.deleted = true;
      this.deleting = false;
      this.$emit("item-deleted", data);
    }
  },
  watch: {
    id: function (newId, oldId) {
      if (this.deleted && (newId !== oldId)) {
        this.deleted = false;
        this.tab = 'rels';
      }
    }
  },
}
</script>

<template>
  <div id="concept-editor" class="form-horizontal">
    <h3 id="concept-editor-item-title">{{ config.vocabName }} | {{ data|conceptTitle(lang, id)}} ({{data.identifier}})</h3>
    <small class="concept-editor-concept-meta" v-if="data.event.user">
      Last updated by {{ data.event.user.name }} {{ data.event.timestamp|formatTimestamp }}.
    </small>
    <div id="concept-editor-item-deleted" v-if="deleted">
      <p class="alert alert-danger">Item deleted</p>
    </div>
    <div id="concept-editor-body" v-else>
      <ul id="concept-editor-nav-tabs" class="nav nav-tabs">
        <li class="nav-item">
          <a class="nav-link" v-bind:class="{active: tab === 'data', 'error': error}" href="#" v-on:click.prevent="tab = 'data'">
            Data
            <i v-if="error" class="fa fa-exclamation-circle"></i>
          </a>
        </li>
        <li class="nav-item">
          <a class="nav-link" v-bind:class="{active: tab === 'rels'}" href="#" v-on:click.prevent="tab = 'rels'">Relationships</a>
        </li>
        <li class="nav-item delete-tab">
          <a class="nav-link" v-bind:class="{active: tab === 'delete'}" href="#" v-on:click.prevent="tab = 'delete'">Delete</a>
        </li>
      </ul>
      <concept-rel-editor
          v-if="tab === 'rels'"
          v-bind:api="api"
          v-bind:lang="lang"
          v-bind:id="id"
          v-bind:data="data.broaderTerms"
          v-bind:langData="langData"
          v-bind:dirty="dirtyRels"
          v-on:item-rels-saved="updateRels"
          v-on:item-rels-reset="$emit('item-rels-reset')"
      />
      <concept-data-editor
          v-else-if="tab === 'data'"
          v-bind:lang="lang"
          v-bind:id="id"
          v-bind:create="false"
          v-bind:data="data"
          v-bind:langData="langData"
          v-bind:locale-helpers="localeHelpers"
          v-bind:dirty="dirtyData"
          v-bind:loading="loading"
          v-bind:saving="saving"
          v-bind:saved="saved"
          v-bind:error="error"
          v-bind:errors="errors"
          v-on:item-data-saved="updateItem"
          v-on:item-data-reset="$emit('item-data-reset')"
      />
      <concept-deleter
          v-if="tab === 'delete'"
          v-bind:api="api"
          v-bind:id="id"
          v-bind:data="data"
          v-bind:lang="lang"
          v-on:delete-cancel="deleting = false"
          v-on:deleted-item="deletedItem"
      />
    </div>
  </div>
</template>
