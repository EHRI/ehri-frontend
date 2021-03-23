<script lang="ts">

import Vue from 'vue';
import VocabEditorApi from "../api";

export default {
  name: 'concept-list-item',
  props: {
    api: VocabEditorApi,
    id: String,
    lang: String,
    name: String,
    childCount: Number,
    selectedId: String,
    eventBus: Vue,
    isSearch: Boolean
  },
  data: function () {
    return {
      loading: false,
      open: false,
      children: []
    };
  },
  methods: {
    refresh: function () {
      this.$emit('refresh');
      return this.api.getChildren(this.id, this.lang)
          .then(c => {
            this.children = c;
            if (c.length === 0) {
              this.open = false;
            }
          });
    },
    showList: function () {
      if (this.childCount) {
        this.open = true;
        this.loading = true;
        this.refresh().then(() => this.loading = false);
      }
    },
    hideList: function () {
      this.open = false;
    },
    forwardEdit: function (id) {
      this.$emit('edit-item', id);
    }
  },
  watch: {
    lang: function (newLang, oldLang) {
      if (this.open) {
        this.refresh();
      }
    },
  },
  created: function () {
    this.eventBus.$on('refresh-children', ids => {
      if (ids.includes(this.id)) {
        this.refresh();
      }
    })
  },
}
</script>

<template>
  <li class="vocab-editor-concept" v-bind:class="{'is-search': isSearch, 'active': selectedId === id}">
    <div class="vocab-editor-concept-heading">
          <span class="vocab-editor-open-narrower"
                v-if="childCount > 0 && !open && !loading"
                v-on:click="showList()"><i class="fa fa-angle-right fa-fw"></i></span>
      <span class="vocab-editor-close-narrower"
            v-if="open && !loading"
            v-on:click="hideList()"><i class="fa fa-angle-down fa-fw"></i></span>
      <span class="vocab-editor-loading-narrower"
            v-if="loading"><i class="fa fa-circle-o-notch fa-pulse fa-fw"></i></span>
      <span v-if="childCount === 0" class="fa fa-fw"></span>
      <span class="vocab-editor-concept-title"
            v-on:click="showList(); $emit('edit-item', id)">
                {{name}}
                <i v-if="selectedId === id" class="fa fa-asterisk"></i>
            </span>
    </div>
    <ul class="vocab-editor-concept-list" v-if="children.length > 0 && open">
      <concept-list-item v-for="child in children"
                         v-bind:api="api"
                         v-bind:key="child[0]"
                         v-bind:id="child[0]"
                         v-bind:lang="lang"
                         v-bind:name="child[1]"
                         v-bind:childCount="child[2]"
                         v-bind:selectedId="selectedId"
                         v-bind:eventBus="eventBus"
                         v-bind:isSearch="false"
                         v-on:refresh="refresh"
                         v-on:edit-item="forwardEdit"/>
    </ul>
  </li>
</template>
