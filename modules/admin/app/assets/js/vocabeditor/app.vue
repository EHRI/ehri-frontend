<script lang="ts">

import ConceptListItem from './components/_concept-list-item';
import ConceptEditor from './components/_concept-editor';
import ConceptCreator from './components/_concept-creator';

import VocabEditorApi from "./api";

import _difference from 'lodash/difference';
import _omit from 'lodash/omit';
import _pick from 'lodash/pick';
import _intersection from 'lodash/intersection';
import _union from 'lodash/union';
import {ConfigType} from "./types";
import emitter from 'tiny-emitter/instance'

export default {
  components: {ConceptListItem, ConceptEditor, ConceptCreator},
  props: {
    config: Object,
    service: Object,
    langData: {
      type: Object,
      default: {}
    },
    localeHelpers: {
      type: Object,
      default: {}
    }
  },
  data: function () {
    return {
      api: new VocabEditorApi(this.service, this.config.vocabId),
      loading: true,
      loadingForm: false,
      lang: "eng",
      langs: [],
      q: "",
      concepts: [],
      creating: false,
      createBuffer: null,
      editing: null,
      editBuffer: null,
      eventBus: {
        // See: https://v3-migration.vuejs.org/breaking-changes/events-api.html#event-bus
        $on: (...args) => emitter.on(...args),
        $once: (...args) => emitter.once(...args),
        $off: (...args) => emitter.off(...args),
        $emit: (...args) => emitter.emit(...args)
      },
      isSearch: false,
    }
  },
  computed: {
    dirtyData: function () {
      return JSON.stringify(_omit(this.editing, ["broaderTerms"]))
          !== JSON.stringify(_omit(this.editBuffer, ["broaderTerms"]));
    },
    dirtyRels: function () {
      return JSON.stringify(_pick(this.editing, ["broaderTerms"]))
          !== JSON.stringify(_pick(this.editBuffer, ["broaderTerms"]));

    }
  },
  methods: {
    conceptTemplate: function () {
      return this.api.getNextIdentifier().then(ident => {
        return {
          isA: "CvocConcept",
          identifier: ident,
          seeAlso: [],
          descriptions: [],
          accessibleTo: [],
          meta: {}
        }
      });
    },
    reload: function (item) {
      this.loading = true;
      this.api.getConcepts(this.q, this.lang).then(concepts => {
        this.loading = false;
        this.concepts = concepts;
        this.isSearch = this.q.trim() !== ""
      });
    },
    refreshItem: function (item) {
      this.reload();
      this.eventBus.$emit('refresh-children', [item.id]);
    },
    refreshUpdatedItem: function (item) {
      this.edit(item);
      this.refreshItem(item);
    },
    refreshSavedItem: function (item) {
      this.createBuffer = null;
      this.refreshUpdatedItem(item);
    },
    refreshReparentedItem: function (item) {
      // refresh broader terms that are
      let inBoth = _intersection(item.broaderTerms.map(c => c.id), this.editing.broaderTerms.map(c => c.id));
      let all = _union(item.broaderTerms.map(c => c.id), this.editing.broaderTerms.map(c => c.id));
      this.eventBus.$emit('refresh-children', _difference(all, inBoth));
      this.edit(item);
      this.reload();
    },
    showNewConceptForm: function () {
      if (this.createBuffer === null) {
        this.loadingForm = true;
        this.conceptTemplate().then(temp => {
          this.loadingForm = false;
          this.createBuffer = temp;
          this.creating = true;
          this.editing = null;
          this.editBuffer = null;
        });
      } else {
        this.creating = true;
        this.editing = null;
        this.editBuffer = null;
      }
    },
    cancelCreate: function() {
      this.creating = false;
      this.createBuffer = null;
    },
    loadItem: function (id) {
      this.loading = true;
      this.api.get(id).then(item => {
        this.loading = false;
        this.edit(item);
      });
    },
    edit: function (item) {
      this.creating = false;
      this.editing = item;
      this.resetData();
      this.resetRels();
    },
    resetData: function () {
      let bt = this.editBuffer && this.editing ? this.editBuffer.broaderTerms : null;
      this.editBuffer = JSON.parse(JSON.stringify(this.editing));
      if (bt) {
        this.editBuffer.broaderTerms = bt;
      }
    },
    resetRels: function () {
      if (this.editBuffer && this.editing) {
        this.editBuffer.broaderTerms = JSON.parse(JSON.stringify(this.editing.broaderTerms));
      }
    },
  },
  watch: {
    lang: function (newLang, oldLang) {
      this.reload();
    },
  },
  created: function () {
    this.api.getLangData().then(langs => this.langs = langs)
        .then(() => this.reload());
  },
}
</script>

<template>
  <div id="vocab-editor-container" class="app-content">
<!--    <h1>{{ config.title }}</h1>-->
    <div id="vocab-editor-panels">
      <div id="vocab-editor-listnav">
        <div class="vocab-editor-controls form-inline">
          <div class="input-group">
              <span class="input-group-prepend">
                <select class="btn btn-default" v-model="lang">
                    <option v-bind:value="l" v-for="l in langs">{{langData[l]}}</option>
                </select>
              </span>
            <input class="form-control" v-model.trim="q" v-on:change="reload" placeholder="Search..."/>
            <div class="input-group-append">
              <button class="btn btn-secondary" v-on:click="reload"
                      v-bind:disabled="q === ''">
                <i v-if="loading" class="fa fa-fw fa-circle-o-notch fa-spin"></i>
                <i v-else class="fa fa-fw fa-search"></i>
              </button>
            </div>
          </div>
        </div>
        <div class="vocab-editor-list">
          <ul class="vocab-editor-top-concepts vocab-editor-concept-list" v-if="concepts.length">
            <concept-list-item v-for="concept in concepts"
                               v-bind:api="api"
                               v-bind:lang="lang"
                               v-bind:key="concept[0]"
                               v-bind:id="concept[0]"
                               v-bind:name="concept[1]"
                               v-bind:childCount="concept[2]"
                               v-bind:selectedId="editing ? editing.id : null"
                               v-bind:eventBus="eventBus"
                               v-bind:isSearch="isSearch"
                               v-on:edit-item="loadItem" />
          </ul>
          <p class="text-muted" v-else-if="loading">Loading data...</p>
          <p class="text-muted" v-else>No items found</p>
        </div>
        <div class="vocab-editor-listnav-footer">
          <button class="btn btn-success"
                  v-bind:disabled="creating"
                  v-on:click="showNewConceptForm">
            New Concept
            <i v-if="loadingForm" class="fa fa-circle-o-notch fa-fw fa-spin"></i>
            <i v-else-if="createBuffer !== null" class="fa fa-asterisk"></i>
          </button>
        </div>
      </div>
      <div id="vocab-editor-editpanel">
        <concept-editor v-if="editing != null"
                        v-bind:api="api"
                        v-bind:config="config"
                        v-bind:lang="lang"
                        v-bind:id="editing.id"
                        v-bind:data="editBuffer"
                        v-bind:dirtyData="dirtyData"
                        v-bind:dirtyRels="dirtyRels"
                        v-bind:langData="langData"
                        v-bind:locale-helpers="localeHelpers"
                        v-on:item-data-reset="resetData"
                        v-on:item-rels-reset="resetRels"
                        v-on:item-data-saved="refreshUpdatedItem"
                        v-on:item-rels-saved="refreshReparentedItem"
                        v-on:item-deleted="refreshItem" />

        <concept-creator v-else-if="creating"
                         v-bind:api="api"
                         v-bind:config="config"
                         v-bind:lang="lang"
                         v-bind:langData="langData"
                         v-bind:locale-helpers="localeHelpers"
                         v-bind:data="createBuffer"
                         v-on:item-data-saved="refreshSavedItem"
                         v-on:cancel-create="cancelCreate" />
        <div v-else id="vocab-editor-load-note">
          <h2>Click on an item left to edit...</h2>
        </div>
      </div>
    </div>
  </div>
</template>
