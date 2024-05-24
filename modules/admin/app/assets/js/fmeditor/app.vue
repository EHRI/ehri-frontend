<script lang="ts">

import FileMetadataEditorApi from "./api";

export default {
  props: {
    service: Object,
  },
  data() {
    return {
      api: new FileMetadataEditorApi(this.service),
      loading: true,
      fileMetadata: []
    }
  },
  methods: {
    reload: function () {
      this.loading = true;
      this.api.list().then(fm => {
        this.loading = false;
        this.fileMetadata = fm
      });
    }
  },
  created: function () {
    this.reload();
  },
}
</script>

<template>
  <div id="field-metadata-editor" class="fm-editor">
    <span v-if="loading">Loading...</span>
    <table class="fm-editor-list">
        <tr>
            <th>Entity</th>
            <th>ID</th>
            <th>Name</th>
            <th>Description</th>
            <th>Usage</th>
            <th>See Also</th>
            <th></th>
        </tr>
        <tr v-for="fm in fileMetadata">
            <td>{{ fm.entityType }}</td>
            <td>{{ fm.id }}</td>
            <td>{{ fm.name }}</td>
            <td>{{ fm.description }}</td>
            <td>{{ fm.usage }}</td>
            <td>
                <a v-for="sa in fm.seeAlso" v-bind:href="sa.seeAlso">{{ fm.seeAlso }}</a>
            </td>
            <td>
                <button v-on:click="alert('Update')">Update</button>
                <button v-on:click="alert('Delete')">Delete</button>
            </td>
        </tr>
    </table>
  </div>
</template>
