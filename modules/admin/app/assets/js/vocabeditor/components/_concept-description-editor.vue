<script lang="ts">

import ConceptDescriptionMultiItem from './_concept-description-multi-item';

export default {
  components: {ConceptDescriptionMultiItem},
  props: {
    data: Object,
    langData: Object,
    newForm: Boolean,
  },
  data: function () {
    return {
      state: this.data,
    }
  },
  watch: {
    data: function (newData) {
      this.state = newData;
    },
  },
}
</script>

<template>
  <div class="vocab-editor-description-body">
    <div class="concept-description-item" v-if="newForm">
      <label class="label">Language</label>
      <div class="controls">
        <select class="form-control" v-model="state.languageCode">
          <option value=""></option>
          <option v-bind:value="key" v-for="(item, key) in langData">{{item}}</option>
        </select>
      </div>
    </div>
    <div class="concept-description-item" v-if="newForm">
      <label class="label">Script Code</label>
      <div class="controls">
        <input type="text" class="form-control" v-model.trim="state.identifier"/>
      </div>
    </div>
    <div class="concept-description-item">
      <label class="label">Pref. Label</label>
      <div class="controls">
        <input class="form-control" v-model.trim="state.name"/>
      </div>
    </div>
    <concept-description-multi-item
        v-bind:label="'Alt. Labels'"
        v-bind:data="state.altLabels" />
    <concept-description-multi-item
        v-bind:label="'Hidden Labels'"
        v-bind:data="state.hiddenLabels" />
    <concept-description-multi-item
        v-bind:label="'Definition(s)'"
        v-bind:data="state.definition"/>
    <concept-description-multi-item
        v-bind:label="'Note(s)'"
        v-bind:data="state.note"/>
    <concept-description-multi-item
        v-bind:label="'Change Note(s)'"
        v-bind:data="state.changeNote"/>
    <concept-description-multi-item
        v-bind:label="'Editorial Note(s)'"
        v-bind:data="state.editorialNote"/>
    <concept-description-multi-item
        v-bind:label="'History Note(s)'"
        v-bind:data="state.historyNote"/>
    <concept-description-multi-item
        v-bind:label="'Scope Note(s)'"
        v-bind:data="state.scopeNote"/>
  </div>
</template>
