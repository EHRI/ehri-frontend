<script lang="ts">
import VocabEditorApi from "../api";

export default {
  props: {
    api: VocabEditorApi,
    lang: String,
    id: String,
    data: Object,
  },
  data: function () {
    return {
      loading: false,
      error: false,
      iAmSure: false,
    }
  },
  methods: {
    deleteItem: function () {
      if (this.iAmSure) {
        this.loading = true;
        this.api.deleteItem(this.id).then(() => {
          this.loading = false;
          this.iAmSure = false;
          this.$emit("deleted-item", this.data);
        }).catch(_ => {
          this.error = true;
          this.loading = false;
        });
      }
    }
  },
}
</script>

<template>
  <div id="concept-delete-tab" class="concept-editor-tab" v-bind:class="{error: error}">
    <div class="concept-editor-tab-form">
      Are you sure you want to delete this item?
      <br/>
      <label><input type="checkbox" v-model="iAmSure"/>Yes, I am sure</label>
    </div>

    <div class="concept-editor-tab-form-footer" v-bind:class="{disabled:loading}">
      <button type="button" class="btn btn-danger" v-bind:disabled="loading || !iAmSure" v-on:click="deleteItem">
        Delete
        <span v-if="loading"><i class="fa fa-fw fa-circle-o-notch fa-spin"></i></span>
        <span v-else><i class="fa fa-fw fa-remove"></i></span>
      </button>
    </div>
  </div>
</template>
