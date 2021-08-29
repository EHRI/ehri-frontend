<script lang="ts">

import ConceptDataEditor from './_concept-data-editor';
import VocabEditorApi from "../api";

export default {
  components: {ConceptDataEditor},
  props: {
    api: VocabEditorApi,
    config: Object,
    lang: String,
    langData: Object,
    localeHelpers: Object,
    data: Object,
  },
  data: function () {
    return {
      loading: false,
      saving: false,
      saved: false,
      error: false,
      errors: {},
      state: this.data,
    }
  },
  methods: {
    createItem: function (toSave) {
      this.loading = true;
      this.saving = true;
      this.api.createItem(toSave)
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
  },
}
</script>

<template>
  <div id="concept-creator" class="form-horizontal">
    <h3 id="concept-creator-title">{{ config.vocabName }} | New Concept</h3>
    <concept-data-editor
        v-bind:lang="lang"
        v-bind:id="null"
        v-bind:create="true"
        v-bind:data="state"
        v-bind:langData="langData"
        v-bind:locale-helpers="localeHelpers"
        v-bind:dirty="true"
        v-bind:loading="loading"
        v-bind:saving="saving"
        v-bind:saved="saved"
        v-bind:error="error"
        v-bind:errors="errors"
        v-on:item-data-saved="createItem"
        v-on:cancel-create="$emit('cancel-create')"
    />
  </div>
</template>
