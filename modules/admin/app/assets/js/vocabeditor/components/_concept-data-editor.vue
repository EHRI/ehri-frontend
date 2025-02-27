<script lang="ts">

import ConceptDescriptionEditor from './_concept-description-editor';

import _findIndex from 'lodash/findIndex';

export default {
  components: {ConceptDescriptionEditor},
  props: {
    lang: String,
    id: String,
    create: Boolean,
    data: Object,
    dirty: Boolean,
    loading: Boolean,
    saving: Boolean,
    saved: Boolean,
    error: Boolean,
    errors: Object,
    langData: Object,
    localeHelpers: Object,
  },
  data: function () {
    return {
      state: this.data,
      newDesc: false,
      pendingDesc: this.descTemplate(),
      currentDescIdx: -1,
      showLinks: false,
      showGeo: false,
    }
  },
  computed: {
    invalid: function () {
      // TODO: More validation???
      return !this.data.identifier;
    },
    sortedDescriptions: function () {
      return this.state.descriptions.concat().sort((a, b) => {
        return a.languageCode.localeCompare(b.languageCode);
      })
    },
    descriptionIds: function () {
      return this.state.descriptions.map(this.descIdent);
    },
    pendingHasUniqueId: function () {
      return !this.descriptionIds.includes(this.descIdent(this.pendingDesc));
    },
    pendingValid: function () {
      return this.pendingDesc.languageCode && this.pendingDesc.name && this.pendingHasUniqueId;
    },
  },
  methods: {
    save: function () {
      this.$emit('item-data-saved', this.state);
    },
    descTemplate: function () {
      return {
        isA: "CvocConceptDescription",
        languageCode: null,
        identifier: null,
        name: "",
        altLabels: [],
        hiddenLabels: [],
        definition: [],
        note: [],
        changeNote: [],
        editorialNote: [],
        historyNote: [],
        scopeNote: [],
        creationProcess: "MANUAL",
        maintenanceEvents: [],
        unknownProperties: [],
        accessPoints: []
      }
    },
    descName: function (desc) {
      return [
        this.localeHelpers.languageCodeToName(desc.languageCode),
        desc.identifier,
        "-",
        desc.name
      ].filter(v => v).join(" ");
    },
    descIdent: function (desc) {
      return [
        desc.languageCode,
        desc.identifier,
      ].filter(v => v).join("-");
    },
    addDesc: function () {
      let data = JSON.parse(JSON.stringify(this.pendingDesc));
      this.state.descriptions.push(data);
      this.newDesc = false;
      this.pendingDesc = this.descTemplate();
    },
    cancelDesc: function () {
      this.newDesc = false;
      this.pendingDesc = this.descTemplate();
    },
    deleteDesc: function (desc) {
      let idx = _findIndex(this.state.descriptions, d =>
          this.descIdent(d) === this.descIdent(desc));
      this.state.descriptions.splice(idx, 1);
    },
  },
  watch: {
    data: function (newData) {
      this.state = newData;
      this.$emit("updated")
    }
  },
}
</script>

<template>
  <div id="concept-editor-data-tab" class="concept-editor-tab" v-bind:class="{error: error}">
    <div class="concept-editor-tab-form">
      <div class="concept-editor-data-form">
        <div class="concept-description-item" v-bind:class="{'has-error': errors.identifier}">
          <label class="label">Identifier</label>
          <div class="controls">
            <input type="url" class="form-control" v-model.trim="state.identifier"/>
            <span v-if="errors.identifier" v-for="e in errors.identifier" class="help-block">{{ e }}</span>
          </div>
        </div>
        <fieldset>
          <h4 v-on:click="showLinks = !showLinks" class="section-toggle">
            Links
            <i v-if="!showLinks" class="fa fa-fw fa-caret-right"></i>
            <i v-else class="fa fa-fw fa-caret-down"></i>
          </h4>
          <template v-if="showLinks">
            <div v-for="(label, key) in {uri: 'URI', url: 'URL'}"
                 class="concept-description-item" v-bind:class="{'has-error': errors[key]}">
              <label class="label">{{ label }}</label>
              <div class="controls">
                <input type="url" class="form-control" v-model.trim="state[key]"/>
                <span v-if="errors[key]" v-for="e in errors[key]" class="help-block">{{ e }}</span>
              </div>
            </div>
          </template>
        </fieldset>
        <fieldset>
          <h4 v-on:click="showGeo = !showGeo" class="section-toggle">
            Geo
            <i v-if="!showGeo" class="fa fa-fw fa-caret-right"></i>
            <i v-else class="fa fa-fw fa-caret-down"></i>
          </h4>
          <template v-if="showGeo">
            <div v-for="(label, key) in {longitude: 'Longitude', latitude: 'Latitude'}"
                  class="concept-description-item" v-bind:class="{'has-error': errors[key]}">
              <label class="label">{{ label }}</label>
              <div class="controls">
                <input type="number" step="any" class="form-control" v-model.trim="state[key]"/>
                <span v-if="errors[key]" v-for="e in errors[key]" class="help-block">{{ e }}</span>
              </div>
            </div>
          </template>
        </fieldset>
        <div id="concept-editor-descriptions">
          <header>
            <div class="concept-editor-description-controls">
              <button v-if="!newDesc" class="btn btn-default" v-on:click="newDesc = true">
                <i class="fa fa-plus-circle"></i>
              </button>
              <button v-if="newDesc" class="btn btn-success" v-bind:disabled="!pendingValid" v-on:click="addDesc">
                <i class="fa fa-check"></i>
              </button><button v-if="newDesc" class="btn btn-default" v-on:click="cancelDesc">
              <i class="fa fa-remove"></i>
            </button>
            </div>
            <h4>Descriptions</h4>
          </header>
          <div class="concept-editor-new-description-form" v-if="newDesc">
            <div v-show="!pendingHasUniqueId" class="alert alert-warning">
              New descriptions must have a unique lang/script combination.
            </div>
            <concept-description-editor
                v-bind:idx="-1"
                v-bind:data="pendingDesc"
                v-bind:langData="langData"
                v-bind:newForm="true" />
            <div class="footer-buttons">
              <button class="btn btn-success" v-bind:disabled="!pendingValid" v-on:click="addDesc">
                Add Description
                <i class="fa fa-check"></i>
              </button>
              <button class="btn btn-default" v-on:click="cancelDesc">
                Cancel
                <i class="fa fa-remove"></i>
              </button>
            </div>
          </div>
          <div class="list-group concept-editor-description-tabs" v-if="!newDesc && state.descriptions.length">
            <div v-for="(description, i) in sortedDescriptions">
              <a href="#" class="list-group-item list-group-item-action"
                 v-bind:class="{active: currentDescIdx === i}"
                 v-on:click="currentDescIdx = i">
                {{descName(description)}}
              </a>
              <div class="concept-editor-description-tab clearfix" v-if="i === currentDescIdx">
                <concept-description-editor
                    v-bind:idx="i"
                    v-bind:key="description.id"
                    v-bind:data="description"
                    v-bind:langData="langData" />
                <button v-if="i === currentDescIdx"
                        class="btn btn-xs btn-danger delete-description" v-on:click="deleteDesc(description)">
                  Delete Description
                  <i class="fa fa-remove"></i>
                </button>
              </div>
            </div>
          </div>
          <p class="alert alert-info" v-else-if="!newDesc">
            No descriptions yet. <a href="#" v-on:click.prevent="newDesc = true">Create one...</a>
          </p>
        </div>
      </div>
    </div>
    <div class="concept-editor-tab-form-footer" v-bind:class="{disabled:saving || loading}">
      <button v-bind:disabled="!dirty || saving || invalid || newDesc" class="btn btn-danger" v-on:click="save">
        Save Concept
        <span v-if="saving"><i class="fa fa-fw fa-circle-o-notch fa-spin"></i></span>
        <span v-else-if="!dirty && saved"><i class="fa fa-fw fa-check"></i></span>
        <span v-else><i class="fa fa-fw fa-save"></i></span>
      </button>
      <button v-if="!create" v-bind:disabled="!dirty || saving" class="reset-data btn btn-default" v-on:click="$emit('item-data-reset'); newDesc = false">
        Reset
        <span><i class="fa fa-fw fa-undo"></i></span>
      </button>
      <button v-if="create" class="cancel-create btn btn-default" v-on:click="$emit('cancel-create')" title="Delete draft">
        <i class="fa fa-trash"></i>
      </button>
    </div>
  </div>
</template>
