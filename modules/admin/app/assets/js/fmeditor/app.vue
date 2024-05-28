<script lang="ts">

import FileMetadataEditorApi from "./api";
import Editor from "./components/_editor";

export default {
  components: {Editor},
  props: {
    service: Object,
  },
  data() {
    return {
      api: new FileMetadataEditorApi(this.service),
      loading: true,
      fileMetadata: [],
      templates: null,
      addNew: null,
    }
  },
  methods: {
    reload: async function () {
      try {
        this.loading = true;
        this.fileMetadata = await this.api.list();
        let data = await this.api.templates();
        console.log("Templates", data);
        this.templates = data;
      } catch (e) {
        console.error(e);
      } finally {
        this.loading = false;
      }
    },
    addNewFieldMetadata: function (entityType, id) {
      this.addNew = {entityType, id, name: this.nameFor(entityType, id)};
    },
    updateFieldMetadata: function (entityType, id) {
      this.$emit('update-field-metadata', entityType, id);
    },
    deleteFieldMetadata: function (entityType, id) {
      this.$emit('delete-field-metadata', entityType, id);
    },
    nameFor: function(entityType, id) {
      for (let [fid, name] of this.templates[entityType].fields) {
        if (fid === id) {
          return name;
        }
      }
      return null;
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
      <div class="fm-editor-list" v-for="[entityType, entityTypeFm] in fileMetadata">
          <h3>{{entityType}}</h3>
          <table class="table table-striped table-bordered">
              <thead>
                  <tr>
                      <th>Name</th>
                      <th>Description</th>
                      <th>Usage</th>
                      <th>See Also</th>
                      <th></th>
                  </tr>
              </thead>
              <tbody>
                  <tr v-for="fm in entityTypeFm">
                      <td>{{ fm.name }}</td>
                      <td>{{ fm.description }}</td>
                      <td>{{ fm.usage }}</td>
                      <td>
                          <a v-for="sa in fm.seeAlso" v-bind:href="sa">{{ sa }}</a>
                      </td>
                      <td>
                          <a v-on:click="updateFieldMetadata(fm.entityType, fm.id)" href="#">Update</a>
                          /
                          <a v-on:click="deleteFieldMetadata(fm.entityType, fm.id)" href="#">Delete</a>
                      </td>
                  </tr>
                  <editor v-if="addNew !== null && addNew.entityType === entityType"
                          v-bind:api="api"
                          v-bind:fm="addNew"
                          v-bind:templates="templates"
                          v-on:saved="addNew = null; reload()" />
              </tbody>
          </table>
          <select v-on:change="addNewFieldMetadata(entityType, $event.target.value)">
              <option value="">Add new...</option>
              <template v-for="(ets, et) in templates">
                  <option v-if="entityType === et" v-for="[id, name] in ets.fields" v-bind:value="id">{{ name }}</option>
              </template>
          </select>
      </div>
  </div>
</template>
